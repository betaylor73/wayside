package com.questrail.wayside.api;

import java.util.Optional;

/**
 * WaysideController
 * -----------------------------------------------------------------------------
 * {@code WaysideController} is the primary semantic façade for interacting with
 * a remote railway wayside logic system.
 *
 * This interface represents the *intentional and observational boundary* between
 * higher-level systems (CTC, supervision, simulation, test harnesses) and the
 * lower-level protocol, mapping, and transport layers.
 *
 * <h2>Core Responsibilities</h2>
 * A {@code WaysideController} is responsible for:
 * <ul>
 *   <li>Maintaining the current desired set of controls (INTENT)</li>
 *   <li>Maintaining the last-known set of indications (OBSERVATION)</li>
 *   <li>Merging partial updates in a semantically correct manner</li>
 *   <li>Exposing controller/link health at a semantic level</li>
 * </ul>
 *
 * It is explicitly <b>not</b> responsible for:
 * <ul>
 *   <li>Interpreting control↔indication causality</li>
 *   <li>Validating field-device behavior</li>
 *   <li>Implementing protocol framing or encoding</li>
 *   <li>Managing retries, polling, or transport timing</li>
 * </ul>
 *
 * <h2>Controls vs Indications</h2>
 * Controls and indications are treated as <b>logically independent sets</b>.
 * Any relationship between them is the responsibility of the remote wayside
 * logic controller and, optionally, higher-level supervisory systems.
 * <p>
 * This interface deliberately avoids implying that:
 * <ul>
 *   <li>a control has a corresponding indication</li>
 *   <li>an indication confirms a control</li>
 *   <li>a lack of indication implies failure</li>
 * </ul>
 *
 * <h2>Partial State and Merging</h2>
 * Many railway protocols communicate partial state. Accordingly:
 * <ul>
 *   <li>{@link SignalState#DONT_CARE} is a first-class concept</li>
 *   <li>Applying a {@link ControlSet} is a merge operation, not a replacement</li>
 *   <li>Unspecified controls must not change existing desired state</li>
 * </ul>
 *
 * Implementations are expected to maintain a materialized internal control state
 * even when updates are partial.
 *
 * <h2>Why There Is No Observer / Listener API (Yet)</h2>
 * This interface intentionally does <b>not</b> expose an Observer- or
 * Listener-style mechanism for receiving indication updates.
 * <p>
 * This omission is deliberate and architectural, not accidental:
 * <ul>
 *   <li>Indications in railway systems are typically <b>state</b>, not events</li>
 *   <li>Many protocols poll or periodically refresh indications, even when
 *       nothing has changed</li>
 *   <li>Indication messages may be partial, repetitive, or not temporally
 *       meaningful</li>
 *   <li>Observer APIs force premature decisions about threading, ordering,
 *       delivery guarantees, and back-pressure</li>
 * </ul>
 *
 * Introducing observers at this level would blur the boundary between
 * <b>semantic state</b> and <b>temporal or policy concerns</b>.
 *
 * <p>
 * Instead, {@code WaysideController} exposes a stable, query-based semantic
 * interface. Higher-level components may:
 * <ul>
 *   <li>Poll for changes</li>
 *   <li>Wrap this interface with observer or reactive adapters</li>
 *   <li>Implement alarm, supervision, or UI update policies</li>
 * </ul>
 *
 * without forcing those policies into the core abstraction.
 *
 * <p>
 * Observer-style APIs are expected to be introduced <b>above</b> this interface
 * (e.g. via decorators or adapters) once protocol- and deployment-specific
 * semantics are well understood.
 *
 * <h2>Threading and Concurrency</h2>
 * This interface makes no guarantees about thread safety.
 * <p>
 * Implementations may:
 * <ul>
 *   <li>Serialize all operations on a single thread</li>
 *   <li>Internally synchronize access</li>
 *   <li>Require external synchronization</li>
 * </ul>
 *
 * Callers must consult implementation documentation for concurrency guarantees.
 *
 * <h2>Protocol Neutrality</h2>
 * {@code WaysideController} is intentionally protocol-agnostic. It does not:
 * <ul>
 *   <li>Expose message boundaries</li>
 *   <li>Expose protocol state machines</li>
 *   <li>Expose transport-level errors</li>
 * </ul>
 *
 * Those concerns are isolated below this interface.
 *
 * <h2>Lifecycle</h2>
 * Implementations may represent:
 * <ul>
 *   <li>A live connection to a physical wayside</li>
 *   <li>A simulated or replayed environment</li>
 *   <li>An offline or disconnected state</li>
 * </ul>
 *
 * The lifecycle semantics are reflected through {@link #getStatus()} rather
 * than through exceptions or protocol-specific signals.
 *
 * <h2>Why this interface matters</h2>
 * This interface is the "thin waist" of the entire architecture. If it remains
 * stable, the system can accommodate:
 * <ul>
 *   <li>Multiple protocols (GENISYS, ATCS, RP2000, etc.)</li>
 *   <li>Multiple transports (HDLC, UDP, serial)</li>
 *   <li>Multiple deployment models (live, simulated, test)</li>
 * </ul>
 *
 * without forcing change on higher-level code.
 */
public interface WaysideController
{
    /**
     * Applies a set of control updates to the controller.
     * <p>
     * The provided {@link ControlSet} may be partial or complete. Signals whose
     * state is {@link SignalState#DONT_CARE} must not modify the existing desired
     * control state.
     * <p>
     * This method expresses <b>intent</b>. It does not guarantee that the remote
     * wayside logic controller has received, accepted, or acted upon the controls.
     *
     * @param controls control updates to apply (must not be {@code null})
     */
    void setControls(ControlSet controls);

    /**
     * Returns the current materialized set of desired controls.
     * <p>
     * The returned {@link ControlSet} represents the controller's complete
     * understanding of desired state, after merging all applied updates.
     * <p>
     * The returned set must not contain {@link SignalState#DONT_CARE}.
     *
     * @return the current materialized control state
     */
    ControlSet getControls();

    /**
     * Returns the most recently observed set of indications, if any.
     * <p>
     * Indications may be partial depending on protocol behavior, message timing,
     * or link health. Callers must not assume the returned set is materialized
     * unless explicitly documented by the implementation.
     *
     * @return the last-known indication set, or {@link Optional#empty()} if no
     *         indications have yet been observed
     */
    Optional<IndicationSet> getIndications();

    /**
     * Returns the current semantic status of the controller.
     * <p>
     * This reflects the controller's ability to communicate with the remote
     * wayside system, not the correctness or safety of the field equipment.
     *
     * @return the current controller status
     */
    ControllerStatus getStatus();
}
