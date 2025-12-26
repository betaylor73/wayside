package com.questrail.wayside.api;

import java.util.Collection;
import java.util.Objects;
import java.util.Set;

/**
 * SignalSet
 * -----------------------------------------------------------------------------
 * {@code SignalSet} represents a semantic set of signals (controls or
 * indications), each associated with a {@link SignalState}.
 *
 * This abstraction is the *heart* of the wayside architecture. It is where:
 * <ul>
 *   <li>semantic identity ({@link SignalId})</li>
 *   <li>partial state ({@link SignalState#DONT_CARE})</li>
 *   <li>and dense internal representation</li>
 * </ul>
 * are unified under a clean, protocol-agnostic API.
 *
 * <h2>Core Semantics</h2>
 * <ul>
 *   <li>A {@code SignalSet} maps each {@link SignalId} to a {@link SignalState}</li>
 *   <li>Signals not present in the set are treated as {@link SignalState#DONT_CARE}</li>
 *   <li>The set may represent a <b>partial</b> or <b>materialized</b> view</li>
 * </ul>
 *
 * <h2>Partial vs Materialized</h2>
 * <p>
 * A partial {@code SignalSet}:
 * <ul>
 *   <li>May contain {@link SignalState#DONT_CARE}</li>
 *   <li>Is typically used for updates, decoded messages, or masks</li>
 * </ul>
 *
 * A materialized {@code SignalSet}:
 * <ul>
 *   <li>Contains only {@link SignalState#TRUE} or {@link SignalState#FALSE}</li>
 *   <li>Represents complete known state</li>
 * </ul>
 *
 * The distinction is semantic rather than structural. Implementations may use
 * the same underlying representation for both.
 *
 * <h2>Controls vs Indications</h2>
 * {@code SignalSet} is intentionally generic and is specialized via subtypes:
 * <ul>
 *   <li>{@code ControlSet} – expresses desired intent</li>
 *   <li>{@code IndicationSet} – expresses observed state</li>
 * </ul>
 *
 * No assumptions are made about correspondence between controls and indications.
 * They are logically independent sets.
 *
 * <h2>Dense Representation</h2>
 * Implementations are expected (but not required) to use dense representations
 * such as {@code BitSet} internally for efficiency. Bit positions and indexing
 * are <b>not</b> part of this interface and must never leak through it.
 *
 * <h2>Immutability and Thread Safety</h2>
 * This interface does not mandate immutability. Concrete implementations may be
 * mutable or immutable depending on performance and usage requirements.
 * Callers must assume no thread-safety guarantees unless explicitly documented
 * by an implementation.
 *
 * <h2>Architectural Boundary</h2>
 * {@code SignalSet} is a semantic construct. It must not:
 * <ul>
 *   <li>expose protocol layout</li>
 *   <li>expose bit indices</li>
 *   <li>encode transport semantics</li>
 * </ul>
 */
public interface SignalSet<ID extends SignalId>
{
    /**
     * Returns the {@link SignalState} associated with the given signal.
     * <p>
     * If the signal is not present in this set, {@link SignalState#DONT_CARE}
     * is returned.
     *
     * @param id the signal identifier (must not be {@code null})
     * @return the associated {@link SignalState}, never {@code null}
     */
    SignalState get(ID id);

    /**
     * Returns {@code true} if the given signal is relevant in this set.
     * <p>
     * This is equivalent to {@code get(id).isRelevant()} and is provided as a
     * convenience for readability.
     *
     * @param id the signal identifier
     * @return {@code true} if the signal is {@link SignalState#TRUE} or
     *         {@link SignalState#FALSE}
     */
    default boolean isRelevant(ID id)
    {
        return get(id).isRelevant();
    }

    /**
     * Returns {@code true} if this set contains no relevant signals.
     * <p>
     * An empty set semantically represents "no change" when applied as an
     * update.
     *
     * @return {@code true} if all signals are {@link SignalState#DONT_CARE}
     */
    boolean isEmpty();

    /**
     * Returns the set of signal identifiers that are relevant in this set.
     * <p>
     * The returned set must contain exactly those signals whose state is
     * {@link SignalState#TRUE} or {@link SignalState#FALSE}.
     *
     * @return a set of relevant signal identifiers
     */
    Set<ID> relevantSignals();

    /**
     * Returns all signal identifiers known to this set.
     * <p>
     * This defines the universe of signals for which this {@code SignalSet}
     * can provide values. Signals outside this universe must not be queried.
     *
     * @return the complete set of known signal identifiers
     */
    Set<ID> allSignals();

    /**
     * Creates a new {@code SignalSet} by merging this set with another.
     * <p>
     * Merge semantics:
     * <ul>
     *   <li>If {@code other} has a relevant value for a signal, it overrides</li>
     *   <li>If {@code other} has {@link SignalState#DONT_CARE}, this set is preserved</li>
     * </ul>
     *
     * This operation is fundamental for applying partial updates.
     *
     * @param other the update set to merge (must not be {@code null})
     * @return a new merged {@code SignalSet}
     */
    SignalSet<ID> merge(SignalSet<ID> other);

    /**
     * Validates that this {@code SignalSet} contains no {@link SignalState#DONT_CARE}
     * values.
     * <p>
     * This method is typically used to assert that a set is materialized before
     * use in contexts that require complete state.
     *
     * @throws IllegalStateException if any signal is {@link SignalState#DONT_CARE}
     */
    void assertMaterialized();

    /**
     * Returns a human-readable representation suitable for diagnostics.
     * <p>
     * Implementations are encouraged to include signal numbers and labels
     * where available.
     *
     * @return a diagnostic string
     */
    @Override
    String toString();

    /**
     * Convenience method for validating non-null arguments.
     *
     * @param obj the object to check
     * @param name parameter name for error messages
     */
    static void requireNonNull(Object obj, String name)
    {
        Objects.requireNonNull(obj, name + " must not be null");
    }
}
