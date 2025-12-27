package com.questrail.wayside.core;

import com.questrail.wayside.api.*;
import com.questrail.wayside.mapping.ArraySignalIndex;
import com.questrail.wayside.mapping.SignalIndex;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link AbstractWaysideController}.
 *
 * These tests are intentionally protocol-free. They validate the semantic
 * invariants that every concrete controller MUST uphold.
 */
class AbstractWaysideControllerTests
{
    // ---------- Test IDs ----------

    enum Ctl implements ControlId {
        A(1), B(2), C(3);

        private final int n;
        Ctl(int n) { this.n = n; }
        @Override public int number() { return n; }
        @Override public Optional<String> label() { return Optional.empty(); }
    }

    enum Ind implements IndicationId {
        X(10), Y(11), Z(12);

        private final int n;
        Ind(int n) { this.n = n; }
        @Override public int number() { return n; }
        @Override public Optional<String> label() { return Optional.empty(); }
    }

    private static SignalIndex<ControlId> controlIndex() {
        return new ArraySignalIndex<>(Ctl.A, Ctl.B, Ctl.C);
    }

    private static SignalIndex<IndicationId> indicationIndex() {
        return new ArraySignalIndex<>(Ind.X, Ind.Y, Ind.Z);
    }

    // ---------- Test controller ----------

    static final class TestController extends AbstractWaysideController {

        final AtomicReference<ControlSet> lastDelta = new AtomicReference<>();
        final AtomicReference<ControlSet> lastFull = new AtomicReference<>();

        TestController() {
            super(controlIndex(), indicationIndex());
        }

        void pushIndications(IndicationSet update) {
            applyIndicationUpdate(update);
        }

        @Override
        protected void onControlsUpdated(ControlSet appliedDelta, ControlSet currentMaterialized) {
            lastDelta.set(appliedDelta);
            lastFull.set(currentMaterialized);
        }
    }

    @Test
    void controlsAreMaterializedAtConstructionAndDefaultFalse() {
        TestController c = new TestController();

        ControlSet controls = c.getControls();
        controls.assertMaterialized();

        assertEquals(SignalState.FALSE, controls.get(Ctl.A));
        assertEquals(SignalState.FALSE, controls.get(Ctl.B));
        assertEquals(SignalState.FALSE, controls.get(Ctl.C));
    }

    @Test
    void setControlsMergesPartialUpdatesAndDoesNotAffectOthers() {
        TestController c = new TestController();

        ControlSet update = new ControlSetBuilder(controlIndex())
                .set(Ctl.B) // TRUE
                .build();

        c.setControls(update);

        ControlSet controls = c.getControls();
        controls.assertMaterialized();

        assertEquals(SignalState.FALSE, controls.get(Ctl.A));
        assertEquals(SignalState.TRUE, controls.get(Ctl.B));
        assertEquals(SignalState.FALSE, controls.get(Ctl.C));
    }

    @Test
    void dontCareDoesNotOverwriteExistingDesiredState() {
        TestController c = new TestController();

        c.setControls(new ControlSetBuilder(controlIndex()).set(Ctl.A).build());
        assertEquals(SignalState.TRUE, c.getControls().get(Ctl.A));

        // A is not present in relevantSignals() here; builder defaults to DONT_CARE.
        // This update touches only B.
        c.setControls(new ControlSetBuilder(controlIndex()).set(Ctl.B).build());

        assertEquals(SignalState.TRUE, c.getControls().get(Ctl.A));
        assertEquals(SignalState.TRUE, c.getControls().get(Ctl.B));
    }

    @Test
    void appliedDeltaContainsOnlyChangedBits() {
        TestController c = new TestController();

        // First set B true.
        c.setControls(new ControlSetBuilder(controlIndex()).set(Ctl.B).build());
        ControlSet delta1 = c.lastDelta.get();
        assertNotNull(delta1);
        assertEquals(SignalState.TRUE, delta1.get(Ctl.B));
        assertTrue(delta1.relevantSignals().contains(Ctl.B));

        // Set B true again: should produce an empty delta.
        c.setControls(new ControlSetBuilder(controlIndex()).set(Ctl.B).build());
        ControlSet delta2 = c.lastDelta.get();
        assertNotNull(delta2);
        assertTrue(delta2.isEmpty(), "Delta should be empty when no state changes");
    }

    @Test
    void onControlsUpdatedReceivesMaterializedSnapshot() {
        TestController c = new TestController();

        c.setControls(new ControlSetBuilder(controlIndex()).set(Ctl.C).build());
        ControlSet full = c.lastFull.get();
        assertNotNull(full);
        full.assertMaterialized();

        assertEquals(SignalState.FALSE, full.get(Ctl.A));
        assertEquals(SignalState.FALSE, full.get(Ctl.B));
        assertEquals(SignalState.TRUE, full.get(Ctl.C));
    }

    @Test
    void indicationsAreEmptyUntilFirstUpdateThenCumulative() {
        TestController c = new TestController();
        assertTrue(c.getIndications().isEmpty());

        // First update sets X true.
        IndicationSet upd1 = new IndicationSetBuilder(indicationIndex())
                .set(Ind.X)
                .build();
        c.pushIndications(upd1);

        IndicationSet ind1 = c.getIndications().orElseThrow();
        assertEquals(SignalState.TRUE, ind1.get(Ind.X));
        assertEquals(SignalState.DONT_CARE, ind1.get(Ind.Y));

        // Second update sets Y false; X should remain true.
        IndicationSet upd2 = new IndicationSetBuilder(indicationIndex())
                .clear(Ind.Y)
                .build();
        c.pushIndications(upd2);

        IndicationSet ind2 = c.getIndications().orElseThrow();
        assertEquals(SignalState.TRUE, ind2.get(Ind.X));
        assertEquals(SignalState.FALSE, ind2.get(Ind.Y));
        assertEquals(SignalState.DONT_CARE, ind2.get(Ind.Z));
    }

    @Test
    void statusDefaultsToDisconnectedAndIsSettableBySubclass() {
        TestController c = new TestController();
        assertEquals(ControllerStatus.DISCONNECTED, c.getStatus());

        c.setStatus(ControllerStatus.CONNECTED);
        assertEquals(ControllerStatus.CONNECTED, c.getStatus());

        c.setStatus(ControllerStatus.DEGRADED);
        assertEquals(ControllerStatus.DEGRADED, c.getStatus());
    }
}
