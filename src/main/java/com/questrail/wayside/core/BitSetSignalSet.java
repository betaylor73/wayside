package com.questrail.wayside.core;

import com.questrail.wayside.api.SignalId;
import com.questrail.wayside.api.SignalSet;
import com.questrail.wayside.api.SignalState;

import java.util.BitSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * BitSetSignalSet
 * -----------------------------------------------------------------------------
 * A dense, efficient {@link SignalSet} implementation backed by {@link BitSet}.
 *
 * This class is the primary production implementation of {@link SignalSet}.
 * It preserves all semantic guarantees established by the API while providing:
 *
 * <ul>
 *   <li>Compact memory representation</li>
 *   <li>Fast merge and diff operations</li>
 *   <li>Cache-friendly iteration</li>
 * </ul>
 *
 * <h2>Internal Representation</h2>
 * Two parallel {@link BitSet}s are used:
 * <ul>
 *   <li>{@code values}     – the boolean value (TRUE/FALSE)</li>
 *   <li>{@code relevance}  – whether the value is relevant</li>
 * </ul>
 *
 * A signal is interpreted as:
 * <ul>
 *   <li>relevance=false → {@link SignalState#DONT_CARE}</li>
 *   <li>relevance=true & value=false → {@link SignalState#FALSE}</li>
 *   <li>relevance=true & value=true  → {@link SignalState#TRUE}</li>
 * </ul>
 *
 * <h2>Identity Mapping</h2>
 * Bit positions are mapped to {@link SignalId}s via an externally supplied
 * index function. This keeps protocol/layout concerns out of this class.
 *
 * <h2>Mutability</h2>
 * This implementation is mutable by design for performance reasons. Callers
 * must not assume thread safety.
 */
public final class BitSetSignalSet<ID extends SignalId> implements SignalSet<ID>
{
    private final BitSet values;
    private final BitSet relevance;
    private final int size;
    private final IntFunction<ID> idForIndex;

    /**
     * Constructs a new empty (all DONT_CARE) BitSetSignalSet.
     *
     * @param size        number of signals in the universe
     * @param idForIndex  mapping from bit index to SignalId
     */
    public BitSetSignalSet(int size, IntFunction<ID> idForIndex) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be positive");
        }
        this.size = size;
        this.idForIndex = Objects.requireNonNull(idForIndex, "idForIndex");
        this.values = new BitSet(size);
        this.relevance = new BitSet(size);
    }

    /**
     * Sets the state of a signal by index.
     * Intended for builders and decoders.
     */
    public void set(int index, SignalState state) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        switch (state) {
            case DONT_CARE -> {
                relevance.clear(index);
                values.clear(index);
            }
            case FALSE -> {
                relevance.set(index);
                values.clear(index);
            }
            case TRUE -> {
                relevance.set(index);
                values.set(index);
            }
        }
    }

    @Override
    public SignalState get(ID id) {
        Objects.requireNonNull(id, "id");
        for (int i = 0; i < size; i++) {
            if (id.equals(idForIndex.apply(i))) {
                if (!relevance.get(i)) return SignalState.DONT_CARE;
                return values.get(i) ? SignalState.TRUE : SignalState.FALSE;
            }
        }
        return SignalState.DONT_CARE;
    }

    @Override
    public boolean isEmpty() {
        return relevance.isEmpty();
    }

    @Override
    public Set<ID> relevantSignals() {
        return relevance.stream()
                .mapToObj(idForIndex::apply)
                .collect(Collectors.toSet());
    }

    @Override
    public Set<ID> allSignals() {
        return IntStream.range(0, size)
                .mapToObj(idForIndex)
                .collect(Collectors.toSet());
    }

    @Override
    public SignalSet<ID> merge(SignalSet<ID> other) {
        Objects.requireNonNull(other, "other");

        BitSetSignalSet<ID> merged = new BitSetSignalSet<>(size, idForIndex);
        merged.values.or(this.values);
        merged.relevance.or(this.relevance);

        for (ID id : other.relevantSignals()) {
            SignalState state = other.get(id);
            for (int i = 0; i < size; i++) {
                if (id.equals(idForIndex.apply(i))) {
                    merged.set(i, state);
                }
            }
        }
        return merged;
    }

    @Override
    public void assertMaterialized() {
        if (relevance.cardinality() != size) {
            throw new IllegalStateException("SignalSet is not materialized");
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (int i = 0; i < size; i++) {
            if (relevance.get(i)) {
                if (!first) sb.append(", ");
                first = false;
                sb.append(idForIndex.apply(i))
                        .append('=')
                        .append(values.get(i));
            }
        }
        sb.append('}');
        return sb.toString();
    }
}
