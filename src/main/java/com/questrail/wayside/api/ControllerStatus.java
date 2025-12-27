package com.questrail.wayside.api;

/**
 * ControllerStatus
 * -----------------------------------------------------------------------------
 * {@code ControllerStatus} represents the semantic health and availability of a
 * {@link WaysideController}.
 *
 * <h2>Purpose</h2>
 * This enum provides a protocol-agnostic, transport-agnostic view of whether a
 * {@code WaysideController} is able to meaningfully communicate with a remote
 * wayside logic system.
 * <p>
 * It intentionally does <b>not</b> attempt to describe:
 * <ul>
 *   <li>Field device correctness</li>
 *   <li>Safety state of the plant</li>
 *   <li>Validity of indications</li>
 *   <li>Cause or fault attribution</li>
 * </ul>
 *
 * Those concerns belong either to the remote wayside controller itself or to
 * higher-level supervisory logic.
 *
 * <h2>Semantic, Not Electrical</h2>
 * {@code ControllerStatus} reflects the controller's <b>ability to participate</b>
 * in the control/indication exchange at a semantic level.
 * <p>
 * For example:
 * <ul>
 *   <li>A controller may be {@link #CONNECTED} even if indications are stale</li>
 *   <li>A controller may be {@link #DEGRADED} even while messages still flow</li>
 *   <li>A controller may be {@link #DISCONNECTED} even if a transport socket is open</li>
 * </ul>
 *
 * Implementations are expected to map protocol- and transport-specific state
 * machines into one of these coarse-grained semantic states.
 *
 * <h2>Status Transitions</h2>
 * No guarantees are made about monotonicity or ordering of status transitions.
 * Implementations may transition freely between states based on observed
 * conditions.
 *
 * <h2>Extensibility</h2>
 * This enum is intentionally small. If additional nuance is required in the
 * future, it should be introduced via:
 * <ul>
 *   <li>Supplementary diagnostic APIs</li>
 *   <li>Implementation-specific metrics</li>
 *   <li>Higher-level supervisory logic</li>
 * </ul>
 * rather than by overloading this enum.
 */
public enum ControllerStatus
{
    /**
     * The controller is not currently able to communicate with the remote
     * wayside logic system.
     * <p>
     * This may represent:
     * <ul>
     *   <li>No active connection</li>
     *   <li>Link initialization in progress</li>
     *   <li>Repeated or unrecoverable communication failures</li>
     * </ul>
     */
    DISCONNECTED,

    /**
     * The controller is able to communicate with the remote wayside logic system
     * in a normal and expected manner.
     */
    CONNECTED,

    /**
     * The controller is communicating with the remote wayside logic system, but
     * with reduced confidence or capability.
     * <p>
     * Examples may include:
     * <ul>
     *   <li>Intermittent communication errors</li>
     *   <li>Missed polls or timeouts</li>
     *   <li>Partial or inconsistent indication updates</li>
     * </ul>
     *
     * This state signals that higher-level systems may wish to increase
     * supervision or apply conservative behavior.
     */
    DEGRADED
}
