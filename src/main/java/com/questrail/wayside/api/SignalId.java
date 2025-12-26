package com.questrail.wayside.api;

import java.util.Optional;

/**
 * SignalId
 * -----------------------------------------------------------------------------
 * A {@code SignalId} represents the *semantic identity* of a single control or
 * indication in a wayside system.
 *
 * This interface is intentionally small, stable, and highly constrained. It is
 * one of the most important abstractions in the entire architecture.
 *
 * <h2>What a SignalId IS</h2>
 * <ul>
 *   <li>A stable, protocol-independent identifier for a logical signal</li>
 *   <li>An association between a logical number (as found in ICDs and specs)
 *       and optional human-readable metadata</li>
 *   <li>The unit of identity used by {@code ControlSet} and {@code IndicationSet}</li>
 * </ul>
 *
 * <h2>What a SignalId IS NOT</h2>
 * <ul>
 *   <li>It is <b>not</b> a bit index</li>
 *   <li>It is <b>not</b> a byte offset</li>
 *   <li>It is <b>not</b> a protocol field or wire encoding</li>
 *   <li>It does <b>not</b> imply any relationship to other signals</li>
 * </ul>
 *
 * Bit positions, byte ordering, and packing rules are protocol- and
 * message-specific concerns and are handled exclusively by the mapping layer
 * (e.g. {@code SignalIndex}, {@code SignalMapping}). They must never leak into
 * this interface.
 *
 * <h2>Logical Number</h2>
 * Every signal is identified by a logical number, typically taken directly from
 * a protocol specification, ICD, or long-standing railway convention.
 * <p>
 * Important properties of the logical number:
 * <ul>
 *   <li>It is stable over time</li>
 *   <li>It is meaningful to humans and documentation</li>
 *   <li>It may be 1-based, 0-based, or irregular depending on the protocol</li>
 * </ul>
 *
 * The architecture makes <b>no assumption</b> that the logical number maps
 * directly to a bit index. If such a mapping exists (e.g. {@code bit = number-1}),
 * it is an implementation detail handled elsewhere.
 *
 * <h2>Optional Label</h2>
 * A signal may optionally have a human-readable label such as {@code "1LK_PK"}.
 * Labels:
 * <ul>
 *   <li>Are intended for logs, diagnostics, UI, and configuration</li>
 *   <li>May be absent entirely</li>
 *   <li>May vary by site or deployment</li>
 * </ul>
 *
 * The presence or absence of a label must not affect system behavior. Labels are
 * descriptive metadata only.
 *
 * <h2>Equality and Identity</h2>
 * Implementations of {@code SignalId} must obey the following rule:
 * <p>
 * <b>Equality and hash code are based on the logical number, not the label.</b>
 * <p>
 * Labels may change or be absent; the logical number is the true identity.
 *
 * <h2>Typical Implementations</h2>
 * <ul>
 *   <li>{@code enum}s (most common and preferred)</li>
 *   <li>Small immutable value objects</li>
 * </ul>
 *
 * {@code enum}-based implementations are strongly encouraged because they:
 * <ul>
 *   <li>Are type-safe</li>
 *   <li>Are easy to audit</li>
 *   <li>Produce readable logs</li>
 *   <li>Work well in both Java and Rust ports</li>
 * </ul>
 *
 * <h2>Why this interface matters</h2>
 * This interface is the point where:
 * <ul>
 *   <li>Semantic meaning enters the system</li>
 *   <li>Protocol details are explicitly excluded</li>
 *   <li>Long-term stability is enforced</li>
 * </ul>
 *
 * If this interface remains clean, the rest of the system can evolve safely.
 */
public interface SignalId
{
    /**
     * Returns the logical number of this signal.
     * <p>
     * This number typically corresponds to how the signal is identified in
     * protocol specifications, ICDs, or legacy documentation (e.g. "Control 16",
     * "Indication 42").
     * <p>
     * No assumptions are made about whether this number is 0-based or 1-based.
     * Any conversion to internal indices is handled elsewhere.
     *
     * @return the stable logical number identifying this signal
     */
    int number();

    /**
     * Returns an optional human-readable label for this signal.
     * <p>
     * Examples include values such as {@code "1LK_PK"}, {@code "SW12_N"}, or
     * {@code "SIG5_CLR"}.
     * <p>
     * The label is intended for diagnostics, logging, and UI presentation only.
     * It must not be used to infer behavior or semantics.
     *
     * @return an {@link Optional} containing the label if one is known, or
     *         {@link Optional#empty()} if no label is defined
     */
    Optional<String> label();
}
