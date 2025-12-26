package com.questrail.wayside.api;

/**
 * SignalState
 * -----------------------------------------------------------------------------
 * {@code SignalState} represents the semantic state of a single control or
 * indication at a particular point in time.
 *
 * This enum is intentionally TERNARY rather than boolean. In real railway
 * protocols and wayside systems, the absence of a value is just as meaningful
 * as an asserted or deasserted value.
 *
 * <h2>The Three States</h2>
 * <ul>
 *   <li>{@link #TRUE}      – the signal is relevant and asserted</li>
 *   <li>{@link #FALSE}     – the signal is relevant and deasserted</li>
 *   <li>{@link #DONT_CARE} – the signal is not specified and must not cause change</li>
 * </ul>
 *
 * <h2>DONT_CARE Is Not "Unknown"</h2>
 * {@code DONT_CARE} does <b>not</b> mean that the signal is unknown, invalid,
 * or erroneous. It means:
 * <p>
 * <b>"This signal is intentionally unspecified in this context."</b>
 * <p>
 * This distinction is critical. {@code DONT_CARE} is used to represent partial
 * updates, masked fields, and incremental protocol messages where only a subset
 * of signals are present.
 *
 * <h2>Controls vs Indications</h2>
 * <ul>
 *   <li>For <b>controls</b>, {@code DONT_CARE} means "do not change the existing
 *       desired value".</li>
 *   <li>For <b>indications</b>, {@code DONT_CARE} means "this indication was not
 *       reported in this message".</li>
 * </ul>
 *
 * In both cases, {@code DONT_CARE} must never overwrite a previously
 * materialized value.
 *
 * <h2>Why This Is an Enum (and Not Optional&lt;Boolean&gt;)</h2>
 * Using {@code Optional<Boolean>} or {@code null} to represent partial state
 * leads to ambiguity and fragile code. {@code SignalState}:
 * <ul>
 *   <li>Makes intent explicit</li>
 *   <li>Prevents accidental misuse of {@code null}</li>
 *   <li>Allows switch statements and exhaustive handling</li>
 *   <li>Encodes domain semantics directly</li>
 * </ul>
 *
 * <h2>Materialized State</h2>
 * A materialized (fully known) set of controls or indications must never
 * contain {@code DONT_CARE}. The presence of {@code DONT_CARE} implies a
 * partial or transient view.
 *
 * This distinction is enforced by convention in Java and may be enforced
 * by typestate in other languages (e.g. Rust).
 *
 * <h2>Architectural Boundary</h2>
 * {@code SignalState} is a semantic construct. It must never be used to
 * represent protocol-level encodings, wire values, or physical signal levels.
 * Those concerns belong strictly below this layer.
 */
public enum SignalState
{
    /**
     * The signal is relevant and asserted.
     */
    TRUE,

    /**
     * The signal is relevant and deasserted.
     */
    FALSE,

    /**
     * The signal is intentionally unspecified and must not cause change.
     */
    DONT_CARE;

    /**
     * Convenience method indicating whether this state is relevant (i.e. not
     * {@link #DONT_CARE}).
     *
     * @return {@code true} if this state is {@link #TRUE} or {@link #FALSE}
     */
    public boolean isRelevant()
    {
        return this != DONT_CARE;
    }
}
