package com.questrail.wayside.mapping;

import com.questrail.wayside.api.SignalId;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * SignalIndex
 * -----------------------------------------------------------------------------
 * {@code SignalIndex} defines the mapping between semantic {@link SignalId}s and
 * dense, 0-based indices used by internal representations (e.g. BitSet) and/or
 * protocol encodings.
 *
 * <h2>Why this exists</h2>
 * Many wayside protocols and ICDs identify signals by a logical number
 * ("Control 16", "Indication 42"). In some protocols that number happens to
 * coincide with the bit position on the wire (often with a 1-based offset).
 * In other protocols the layout may be sparse, grouped, message-type-specific,
 * or otherwise irregular.
 *
 * This interface isolates those layout concerns behind an explicit boundary so
 * that:
 * <ul>
 *   <li>Semantic code never manipulates bit indices directly</li>
 *   <li>Dense representations remain possible and efficient</li>
 *   <li>0-based vs 1-based conventions do not leak upward</li>
 *   <li>Protocol mapping logic can evolve independently</li>
 * </ul>
 *
 * <h2>Index Semantics</h2>
 * The index returned by {@link #indexOf(SignalId)} is always:
 * <ul>
 *   <li>0-based</li>
 *   <li>Dense in the sense that it is suitable for BitSet addressing</li>
 *   <li>Stable within a given {@code SignalIndex} instance</li>
 * </ul>
 *
 * No meaning should be assigned to the index outside of storage/encoding.
 *
 * <h2>Universe</h2>
 * Every {@code SignalIndex} defines a universe of signals:
 * <ul>
 *   <li>{@link #size()} determines the maximum index + 1</li>
 *   <li>{@link #allSignals()} provides the complete set of known IDs</li>
 * </ul>
 *
 * A {@code SignalIndex} may be protocol-wide, site-specific, or endpoint-specific
 * depending on the system.
 */
public interface SignalIndex<ID extends SignalId>
{
    /**
     * Returns the number of signals in the universe.
     *
     * @return the size of the index (max index + 1)
     */
    int size();

    /**
     * Returns the 0-based index corresponding to the given signal ID.
     *
     * @param id signal identifier
     * @return 0-based dense index
     * @throws IllegalArgumentException if the ID is unknown to this index
     */
    int indexOf(ID id);

    /**
     * Reverse-lookup: returns the signal ID at the given index.
     *
     * @param index 0-based dense index
     * @return the signal ID
     * @throws IndexOutOfBoundsException if index is out of range
     */
    ID idAt(int index);

    /**
     * Returns the complete set of known signal IDs.
     *
     * @return all signals in this universe
     */
    Set<ID> allSignals();

    /**
     * Returns true if this index contains the given signal ID.
     */
    default boolean contains(ID id) {
        Objects.requireNonNull(id, "id");
        try {
            indexOf(id);
            return true;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    /**
     * Attempts to resolve a signal by its logical number.
     * <p>
     * This is intended primarily for diagnostics and configuration workflows
     * where humans refer to signals by number.
     * <p>
     * IMPORTANT: The number here is the {@link SignalId#number()} (ICD number),
     * not a bit index.
     */
    default Optional<ID> tryResolveByNumber(int number) {
        for (ID id : allSignals()) {
            if (id.number() == number) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }

    /**
     * Attempts to resolve a signal by its label.
     * <p>
     * Labels are optional and may not be unique across all deployments. This
     * method is best-effort and intended for configuration and diagnostics.
     */
    default Optional<ID> tryResolveByLabel(String label) {
        Objects.requireNonNull(label, "label");
        for (ID id : allSignals()) {
            if (id.label().isPresent() && id.label().get().equals(label)) {
                return Optional.of(id);
            }
        }
        return Optional.empty();
    }
}
