package com.questrail.wayside.mapping;

import com.questrail.wayside.api.SignalId;

import java.util.*;

/**
 * ArraySignalIndex
 * -----------------------------------------------------------------------------
 * A straightforward, efficient {@link SignalIndex} implementation backed by:
 *
 * <ul>
 *   <li>an array for index -> id</li>
 *   <li>a map for id -> index</li>
 * </ul>
 *
 * This is a good default implementation for most systems.
 */
public final class ArraySignalIndex<ID extends SignalId> implements SignalIndex<ID>
{
    private final ID[] idByIndex;
    private final Map<ID, Integer> indexById;
    private final Set<ID> all;

    /**
     * Creates an index from an ordered list of IDs.
     *
     * The position in the array is the 0-based index.
     */
    @SafeVarargs
    public ArraySignalIndex(ID... idsInIndexOrder) {
        Objects.requireNonNull(idsInIndexOrder, "idsInIndexOrder");
        if (idsInIndexOrder.length == 0) {
            throw new IllegalArgumentException("At least one ID is required");
        }

        this.idByIndex = Arrays.copyOf(idsInIndexOrder, idsInIndexOrder.length);

        Map<ID, Integer> tmp = new HashMap<>(idsInIndexOrder.length * 2);
        for (int i = 0; i < idByIndex.length; i++) {
            ID id = Objects.requireNonNull(idByIndex[i], "id at index " + i);
            Integer prev = tmp.put(id, i);
            if (prev != null) {
                throw new IllegalArgumentException("Duplicate ID in index: " + id);
            }
        }
        this.indexById = Collections.unmodifiableMap(tmp);
        this.all = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(idByIndex)));
    }

    @Override
    public int size() {
        return idByIndex.length;
    }

    @Override
    public int indexOf(ID id) {
        Objects.requireNonNull(id, "id");
        Integer idx = indexById.get(id);
        if (idx == null) {
            throw new IllegalArgumentException("Unknown signal id: " + id);
        }
        return idx;
    }

    @Override
    public ID idAt(int index) {
        if (index < 0 || index >= idByIndex.length) {
            throw new IndexOutOfBoundsException("index=" + index + ", size=" + idByIndex.length);
        }
        return idByIndex[index];
    }

    @Override
    public Set<ID> allSignals() {
        return all;
    }
}
