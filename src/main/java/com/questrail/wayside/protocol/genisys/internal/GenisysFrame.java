package com.questrail.wayside.protocol.genisys.internal;

import java.util.List;
import java.util.Objects;

/**
 * GenisysFrame
 * -----------------------------------------------------------------------------
 * Logical representation of a decoded GENISYS protocol frame.
 *
 * <h2>Scope</h2>
 * This class represents a frame *after*:
 * <ul>
 *   <li>Framing has been validated</li>
 *   <li>Escaping has been removed</li>
 *   <li>CRC (if present) has been verified</li>
 * </ul>
 *
 * As such, {@code GenisysFrame} is suitable for direct consumption by the
 * protocol state machine and event model.
 *
 * <h2>Design intent</h2>
 * This is intentionally a minimal skeleton:
 * <ul>
 *   <li>No encoding or decoding logic</li>
 *   <li>No transport assumptions</li>
 *   <li>No semantic interpretation of data bytes</li>
 * </ul>
 *
 * Future evolution may add convenience helpers, but this class should remain
 * a simple, immutable data carrier.
 */
public final class GenisysFrame
{
    private final byte header;
    private final int stationAddress;
    private final List<Byte> payload;
    private final boolean crcPresent;

    public GenisysFrame(byte header,
                        int stationAddress,
                        List<Byte> payload,
                        boolean crcPresent) {
        this.header = header;
        this.stationAddress = stationAddress;
        this.payload = List.copyOf(payload);
        this.crcPresent = crcPresent;
    }

    /**
     * Returns the GENISYS header/control byte (e.g. $F2, $FB, etc.).
     */
    public byte header() {
        return header;
    }

    /**
     * Returns the station address this frame applies to.
     */
    public int stationAddress() {
        return stationAddress;
    }

    /**
     * Returns the raw, decoded payload bytes.
     * <p>
     * For indication and control frames, this will typically be
     * repeated [byteAddress, byteValue] pairs.
     */
    public List<Byte> payload() {
        return payload;
    }

    /**
     * Indicates whether this frame carried a CRC on the wire.
     */
    public boolean crcPresent() {
        return crcPresent;
    }
}
