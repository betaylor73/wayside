package com.questrail.wayside.api;

/**
 * ControlId
 * -----------------------------------------------------------------------------
 * Marker interface identifying a {@link SignalId} that represents a CONTROL.
 *
 * Controls express INTENT toward a remote wayside logic controller. They
 * represent desired state, not guaranteed outcome. The WaysideController
 * transports control intent but does not interpret or validate any causal
 * relationship between controls and resulting indications.
 *
 * <h2>Key Properties</h2>
 * <ul>
 * <li>Controls are semantic identifiers, not protocol fields</li>
 * <li>Controls may be set partially (incremental updates)</li>
 * <li>Controls may be latched or transient depending on the remote logic</li>
 * <li>Controls do not imply any corresponding indication</li>
 * </ul>
 *
 * <h2>Architectural Notes</h2>
 * <ul>
 * <li>This interface intentionally adds NO methods beyond {@link SignalId}</li>
 * <li>Its purpose is type separation, not behavior</li>
 * <li>It prevents accidental mixing of controls and indications</li>
 * </ul>
 *
 * <h2>Why a Marker Interface?</h2>
 * Using a marker interface rather than a flag or enum category:
 * <ul>
 * <li>Allows the compiler to enforce correct usage</li>
 * <li>Makes APIs self-documenting</li>
 * <li>Prevents whole classes of semantic bugs</li>
 * </ul>
 *
 * For example, a {@code ControlSet} can only accept {@code ControlId}s,
 * and it is impossible to accidentally insert an {@code IndicationId}.
 *
 * <h2>Typical Implementations</h2>
 * <ul>
 * <li>{@code enum} types representing protocol- or site-specific controls</li>
 * </ul>
 *
 * Example:
 * <pre>{@code
 * enum GenisysControlId implements ControlId {
 * CTRL_16(16, Optional.of("1LK_PK"));
 * }
 * }</pre>
 */
public interface ControlId extends SignalId
{
    // No additional methods by design
}