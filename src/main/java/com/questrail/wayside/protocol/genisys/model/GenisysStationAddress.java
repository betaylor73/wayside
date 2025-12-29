package com.questrail.wayside.protocol.genisys.model;

import java.util.Objects;

/**
 * Strongly typed representation of a GENISYS station address.
 *
 * <h2>Why this type exists</h2>
 * <p>
 * In the GENISYS protocol, the station address is a first-class semantic concept:
 * it identifies the slave unit being addressed by the master, or the slave
 * responding to the master.
 * </p>
 *
 * <p>
 * Although GENISYS station addresses are encoded on the wire as a single byte
 * (values {@code 0x01}–{@code 0xFF}, with {@code 0x00} reserved for broadcast
 * in certain master-to-slave messages), treating them as raw {@code int} or
 * {@code byte} values in the protocol behavior layer would:
 * </p>
 *
 * <ul>
 *   <li>Allow accidental mixing with unrelated numeric identifiers</li>
 *   <li>Obscure protocol intent in reducer logic and tests</li>
 *   <li>Weaken observability and traceability</li>
 * </ul>
 *
 * <p>
 * This class therefore provides a GENISYS-specific, type-safe wrapper around
 * the station address value. It is intentionally scoped to GENISYS and MUST NOT
 * be reused as a generic "station address" abstraction for other protocols.
 * </p>
 *
 * <h2>Protocol constraints</h2>
 * <ul>
 *   <li>Valid slave station addresses are {@code 1}–{@code 255} (inclusive)</li>
 *   <li>{@code 0} is reserved for broadcast in limited master-to-slave contexts</li>
 * </ul>
 *
 * <p>
 * This class represents a <em>specific slave station</em>. As such, the broadcast
 * address ({@code 0}) is intentionally not representable here. If broadcast
 * semantics are required in the future, they should be modeled explicitly and
 * separately.
 * </p>
 *
 * <h2>Layering note</h2>
 * <p>
 * This type lives in the <em>protocol semantic model</em> layer. It has:
 * </p>
 * <ul>
 *   <li>No knowledge of wire encoding</li>
 *   <li>No knowledge of transport or framing</li>
 *   <li>No dependency on Netty, Spring, or configuration mechanisms</li>
 * </ul>
 */
public final class GenisysStationAddress
{
    /**
     * Minimum valid slave station address.
     */
    public static final int MIN_VALUE = 1;

    /**
     * Maximum valid slave station address.
     */
    public static final int MAX_VALUE = 255;

    private final int value;

    private GenisysStationAddress(int value) {
        this.value = value;
    }

    /**
     * Creates a {@code GenisysStationAddress} for the given numeric value.
     *
     * @param value the station address value (1–255 inclusive)
     * @return a {@code GenisysStationAddress} instance
     * @throws IllegalArgumentException if the value is outside the valid range
     */
    public static GenisysStationAddress of(int value) {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException(
                    "GENISYS station address must be in range "
                            + MIN_VALUE + "–" + MAX_VALUE
                            + " (was " + value + ")"
            );
        }
        return new GenisysStationAddress(value);
    }

    /**
     * Returns the numeric station address value.
     *
     * <p>
     * This value is suitable for:
     * </p>
     * <ul>
     *   <li>Use as a map key (via {@link #equals(Object)} / {@link #hashCode()})</li>
     *   <li>Correlation and observability tags</li>
     *   <li>Conversion to a wire-level representation by lower layers</li>
     * </ul>
     *
     * @return the station address value (1–255)
     */
    public int value() {
        return value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GenisysStationAddress that)) return false;
        return value == that.value;
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }

    @Override
    public String toString() {
        return "GenisysStationAddress[" + value + "]";
    }
}
