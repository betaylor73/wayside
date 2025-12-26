package com.questrail.wayside.core;

import com.questrail.wayside.api.SignalId;
import com.questrail.wayside.api.SignalSet;
import com.questrail.wayside.api.SignalState;
import com.questrail.wayside.mapping.SignalIndex;

import java.util.BitSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * BitSetSignalSet
 * -----------------------------------------------------------------------------
 * A dense, efficient {@link SignalSet} implementation backed by {@link BitSet},
 * using a {@link SignalIndex} to map semantic {@link SignalId}s to 0-based indices.
 *
 * <h2>Design Intent</h2>
 * This class is the primary production implementation of {@code SignalSet}.
 * It preserves all semantic guarantees of the API while enabling:
 * <ul>
 *   <li>Compact memory representation</li>
 *   <li>O(1) access by signal identity</li>
 *   <li>Fast merge operations for partial updates</li>
 * </ul>
 *
 * <h2>Internal Representation</h2>
 * Two parallel {@link BitSet}s are maintained:
 * <ul>
 *   <li>{@code relevance} – whether a signal is specified</li>
 *   <li>{@code values}    – the boolean value when relevant</li>
 * </ul>
 *
 * Interpretation:
 * <pre>
 * relevance = false                 → DONT_CARE
 * relevance = true,  values = false → FALSE
 * relevance = true,  values = true  → TRUE
 * </pre>
 *
 * <h2>Mutability</h2>
 * This class is mutable by design and makes no thread-safety guarantees.
 * Builders, decoders, and controllers are expected to manage concurrency
 * at higher layers if required.
 */
public class BitSetSignalSet<ID extends SignalId> implements SignalSet<ID>
{
    protected final SignalIndex<ID> index;
    protected final BitSet relevance;
    protected final BitSet values;

    /**
     * Creates an empty {@code BitSetSignalSet} (all signals DONT_CARE)
     * for the given signal universe.
     *
     * @param index mapping between {@link SignalId} and dense indices
     */
    protected BitSetSignalSet(SignalIndex<ID> index) {
        this.index = Objects.requireNonNull(index, "index");
        this.relevance = new BitSet(index.size());
        this.values = new BitSet(index.size());
    }

    /**
     * Sets the state of a signal.
     * <p>
     * This method is intended for use by builders, protocol decoders,
     * and unit tests. Higher-level code should prefer immutable or
     * copy-on-write usage patterns if required.
     *
     * @param id    signal identifier
     * @param state desired signal state
     */
    public final void set(ID id, SignalState state) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(state, "state");

        int idx = index.indexOf(id);

        switch (state) {
            case DONT_CARE -> {
                relevance.clear(idx);
                values.clear(idx);
            }
            case FALSE -> {
                relevance.set(idx);
                values.clear(idx);
            }
            case TRUE -> {
                relevance.set(idx);
                values.set(idx);
            }
        }
    }

    @Override
    public final SignalState get(ID id) {
        Objects.requireNonNull(id, "id");
        int idx = index.indexOf(id);

        if (!relevance.get(idx)) {
            return SignalState.DONT_CARE;
        }
        return values.get(idx) ? SignalState.TRUE : SignalState.FALSE;
    }

    @Override
    public final boolean isEmpty() {
        return relevance.isEmpty();
    }

    @Override
    public final Set<ID> relevantSignals() {
        return relevance.stream()
                .mapToObj(index::idAt)
                .collect(Collectors.toSet());
    }

    @Override
    public final Set<ID> allSignals() {
        return index.allSignals();
    }

    @Override
    public final SignalSet<ID> merge(SignalSet<ID> other) {
        Objects.requireNonNull(other, "other");

        BitSetSignalSet<ID> merged = new BitSetSignalSet<>(index);

        // Start with this set's materialized bits
        merged.relevance.or(this.relevance);
        merged.values.or(this.values);

        // Overlay relevant signals from the update
        for (ID id : other.relevantSignals()) {
            merged.set(id, other.get(id));
        }

        return merged;
    }

    @Override
    public final void assertMaterialized() {
        if (relevance.cardinality() != index.size()) {
            throw new IllegalStateException("SignalSet is not fully materialized");
        }
    }

    @Override
    public final String toString() {
        return IntStream.range(0, index.size())
                .filter(relevance::get)
                .mapToObj(i -> index.idAt(i) + "=" + values.get(i))
                .collect(Collectors.joining(", ", "{", "}"));
    }
}
