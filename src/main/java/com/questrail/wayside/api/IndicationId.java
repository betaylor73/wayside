package com.questrail.wayside.api;

/**
 * IndicationId
 * -----------------------------------------------------------------------------
 * Marker interface identifying a {@link SignalId} that represents an INDICATION.
 *
 * Indications express OBSERVED or REPORTED STATE from a remote wayside logic
 * controller. They are descriptive, not confirmatory: the presence or absence
 * of an indication must not be interpreted as success or failure of any control
 * unless higher-level supervisory logic explicitly does so.
 *
 * <h2>Key Properties</h2>
 * <ul>
 * <li>Indications are observational, not causal</li>
 * <li>They may be stale, delayed, or partial</li>
 * <li>They may exist without any corresponding control</li>
 * <li>They may change for reasons unrelated to recent controls</li>
 * </ul>
 *
 * <h2>Architectural Notes</h2>
 * <ul>
 * <li>This interface intentionally adds NO methods beyond {@link SignalId}</li>
 * <li>It exists solely to maintain semantic separation from controls</li>
 * <li>It enforces independence between intent and observation</li>
 * </ul>
 *
 * <h2>Why This Matters</h2>
 * Many systems implicitly assume that:
 * <ul>
 * <li>Every control has a corresponding indication</li>
 * <li>Indications "confirm" controls</li>
 * </ul>
 *
 * This architecture explicitly rejects those assumptions. Any relationship
 * between controls and indications is the responsibility of the remote wayside
 * logic and, optionally, higher-level supervisory systems.
 *
 * <h2>Typical Implementations</h2>
 * <ul>
 * <li>{@code enum} types representing protocol- or site-specific indications</li>
 * </ul>
 */
public interface IndicationId extends SignalId
{
    // No additional methods by design
}