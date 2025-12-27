package com.questrail.wayside.core;

import com.questrail.wayside.api.*;
import com.questrail.wayside.mapping.SignalIndex;

import java.util.Objects;
import java.util.Optional;

/**
 * AbstractWaysideController
 * -----------------------------------------------------------------------------
 * A protocol-neutral base implementation of {@link WaysideController} that
 * centralizes the *semantic* responsibilities common to all wayside protocols.
 *
 * <h2>What this class does</h2>
 * This class implements the architectural contract expressed by
 * {@link WaysideController}:
 *
 * <ul>
 *   <li>Maintains a <b>materialized</b> desired control state</li>
 *   <li>Accepts <b>partial</b> control updates via {@link #setControls(ControlSet)}</li>
 *   <li>Merges partial updates correctly (DONT_CARE never overwrites)</li>
 *   <li>Maintains a "last-known" indication state by cumulatively applying
 *       partial indication updates</li>
 *   <li>Exposes a coarse, semantic {@link ControllerStatus}</li>
 * </ul>
 *
 * <h2>What this class does NOT do</h2>
 * This class is intentionally NOT a protocol engine. It does not:
 * <ul>
 *   <li>Frame, encode, or decode protocol messages</li>
 *   <li>Manage polling cadence, retries, or timeouts</li>
 *   <li>Implement transport (HDLC, UDP, serial) behavior</li>
 * </ul>
 *
 * Those responsibilities live in concrete subclasses and lower layers.
 *
 * <h2>Threading model</h2>
 * The base class uses a single private lock ("monitor") to protect state.
 * This yields a simple, predictable correctness story. Subclasses that have
 * stronger concurrency requirements may:
 * <ul>
 *   <li>call these methods from a single thread (preferred)</li>
 *   <li>or rely on the built-in synchronization</li>
 * </ul>
 *
 * <h2>Control materialization</h2>
 * A key invariant of this class is:
 * <blockquote>
 *     {@link #getControls()} always returns a fully materialized control set;
 *     it contains no {@link SignalState#DONT_CARE}.
 * </blockquote>
 *
 * To uphold that, the constructor initializes all controls to FALSE.
 * (This is a semantic default for "no asserted controls"; if a deployment
 * requires a different default, a subclass may override
 * {@link #initialControlState(ControlId)}.)
 *
 * <h2>Indication semantics</h2>
 * Indications are kept as a cumulative "last-known" state, starting from
 * all DONT_CARE and applying updates as they arrive.
 * <p>
 * This gives callers a useful view of the last observed values without
 * forcing event semantics into the interface.
 */
public abstract class AbstractWaysideController implements WaysideController
{
    private final Object lock = new Object();

    private final SignalIndex<ControlId> controlIndex;
    private final Optional<SignalIndex<IndicationId>> indicationIndex;

    /**
     * Always materialized.
     */
    private final ControlBitSetSignalSet controls;

    /**
     * Cumulative last-known indications; starts as all DONT_CARE.
     * Null until first update is observed.
     */
    private IndicationBitSetSignalSet indications;

    private volatile ControllerStatus status = ControllerStatus.DISCONNECTED;

    protected AbstractWaysideController(
            SignalIndex<ControlId> controlIndex,
            SignalIndex<IndicationId> indicationIndex
    ) {
        this.controlIndex = Objects.requireNonNull(controlIndex, "controlIndex");
        this.indicationIndex = Optional.ofNullable(indicationIndex);

        // Materialize controls immediately.
        this.controls = new ControlBitSetSignalSet(this.controlIndex);
        for (ControlId id : this.controlIndex.allSignals()) {
            controls.set(id, initialControlState(id));
        }
        // Defensive: guarantee invariant holds at construction time.
        controls.assertMaterialized();

        // Indications are created lazily on first update. If indicationIndex is
        // null, indications are simply unsupported.
        this.indications = null;
    }

    /**
     * Returns the default initial state for a control at startup.
     * <p>
     * The base implementation returns FALSE for all controls.
     * Subclasses may override for site-specific requirements.
     */
    protected SignalState initialControlState(ControlId id) {
        return SignalState.FALSE;
    }

    @Override
    public final void setControls(ControlSet updates) {
        Objects.requireNonNull(updates, "updates");

        // We'll compute (1) the actual changes applied, and (2) the resulting
        // materialized control state, then delegate transmission to the subclass.
        final ControlBitSetSignalSet appliedDelta;
        final ControlBitSetSignalSet materializedSnapshot;

        synchronized (lock) {
            appliedDelta = new ControlBitSetSignalSet(controlIndex);

            // Apply only relevant controls. DONT_CARE must not overwrite.
            for (ControlId id : updates.relevantSignals()) {
                SignalState desired = updates.get(id);
                if (!desired.isRelevant()) {
                    continue; // defensive: should not happen if relevantSignals() is correct
                }

                SignalState prior = controls.get(id);
                if (prior != desired) {
                    controls.set(id, desired);
                    appliedDelta.set(id, desired);
                }
            }

            // Snapshot the complete materialized controls for downstream.
            materializedSnapshot = snapshotControlsLocked();
        }

        // Delegate sending behavior outside of the lock.
        // (This prevents protocol/IO code from blocking semantic state updates.)
        onControlsUpdated(appliedDelta, materializedSnapshot);
    }

    @Override
    public final ControlSet getControls() {
        synchronized (lock) {
            return snapshotControlsLocked();
        }
    }

    @Override
    public final Optional<IndicationSet> getIndications() {
        synchronized (lock) {
            if (indications == null) {
                return Optional.empty();
            }
            return Optional.of(snapshotIndicationsLocked());
        }
    }

    @Override
    public final ControllerStatus getStatus() {
        return status;
    }

    /**
     * Updates the semantic status of this controller.
     * <p>
     * Intended for subclasses to call when link/protocol state changes.
     */
    protected final void setStatus(ControllerStatus status) {
        this.status = Objects.requireNonNull(status, "status");
    }

    /**
     * Subclasses call this when a (possibly partial) set of indications has been
     * received/decoded from the remote wayside.
     *
     * <h3>Merging rule</h3>
     * Only relevant indications in {@code update} are applied.
     * Unspecified signals (DONT_CARE) do not overwrite prior last-known state.
     */
    protected final void applyIndicationUpdate(IndicationSet update) {
        Objects.requireNonNull(update, "update");

        synchronized (lock) {
            if (indicationIndex.isEmpty()) {
                throw new IllegalStateException("This controller does not support indications");
            }

            if (indications == null) {
                indications = new IndicationBitSetSignalSet(indicationIndex.get());
            }

            for (IndicationId id : update.relevantSignals()) {
                SignalState state = update.get(id);
                if (state.isRelevant()) {
                    indications.set(id, state);
                }
            }
        }
    }

    /**
     * Hook for subclasses to transmit controls to the remote wayside.
     *
     * @param appliedDelta         only the controls that changed as a result of
     *                             {@link #setControls(ControlSet)} (may be empty)
     * @param currentMaterialized  the resulting full, materialized control state
     */
    protected abstract void onControlsUpdated(ControlSet appliedDelta, ControlSet currentMaterialized);

    // ------------------------
    // Snapshot helpers
    // ------------------------

    private ControlBitSetSignalSet snapshotControlsLocked() {
        ControlBitSetSignalSet snap = new ControlBitSetSignalSet(controlIndex);
        for (ControlId id : controlIndex.allSignals()) {
            snap.set(id, controls.get(id));
        }
        // Should always hold.
        snap.assertMaterialized();
        return snap;
    }

    private IndicationBitSetSignalSet snapshotIndicationsLocked() {
        // indications is non-null by precondition of caller.
        IndicationBitSetSignalSet snap = new IndicationBitSetSignalSet(indicationIndex.orElseThrow());
        for (IndicationId id : indicationIndex.orElseThrow().allSignals()) {
            snap.set(id, indications.get(id));
        }
        return snap;
    }
}
