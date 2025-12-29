package com.questrail.wayside.protocol.genisys.internal.frame;

import java.util.Arrays;

/**
 * GenisysFrame
 * -----------------------------------------------------------------------------
 * Immutable, decoded representation of a GENISYS protocol frame.
 *
 * <h2>What this represents</h2>
 * A {@code GenisysFrame} represents a GENISYS message after:
 * <ul>
 *   <li>Framing has been detected</li>
 *   <li>Escaping has been removed</li>
 *   <li>CRC (if present) has been validated</li>
 * </ul>
 *
 * It is still <em>not</em> a semantic protocol message.
 * Reducers and protocol logic must not branch on header bytes or interpret
 * payload formats directly.
 *
 * <h2>Why {@code byte[]} is used for payload</h2>
 * GENISYS payloads are ordered, dense, byte-oriented protocol data.
 * Using {@code byte[]} avoids boxing, improves clarity, and simplifies decoding.
 *
 * Immutability is enforced via defensive copying.
 */
public final class GenisysFrame
{
    /**
     * GENISYS header/control byte.
     * Stored as a signed byte but interpreted as unsigned where required.
     */
    private final byte header;

    /**
     * Station address as received on the wire.
     *
     * NOTE:
     * This is intentionally kept as a primitive here.
     * Conversion to {@code GenisysStationAddress} occurs at the semantic boundary.
     */
    private final int stationAddress;

    /**
     * Decoded payload bytes (may be empty, never null).
     */
    private final byte[] payload;

    /**
     * Indicates whether this frame was received in secure (CRC-protected) form.
     */
    private final boolean crcPresent;

    public GenisysFrame(byte header,
                        int stationAddress,
                        byte[] payload,
                        boolean crcPresent) {

        this.header = header;
        this.stationAddress = stationAddress;
        this.payload = (payload == null) ? new byte[0] : payload.clone();
        this.crcPresent = crcPresent;
    }

    /**
     * Returns the GENISYS header/control byte.
     */
    public byte header() {
        return header;
    }

    /**
     * Returns the raw station address value as received on the wire.
     *
     * Semantic validation is intentionally deferred to the decode layer.
     */
    public int stationAddress() {
        return stationAddress;
    }

    /**
     * Returns a copy of the payload bytes.
     *
     * Callers must not assume mutability or identity stability.
     */
    public byte[] payload() {
        return payload.clone();
    }

    /**
     * Indicates whether this frame included a CRC.
     */
    public boolean crcPresent() {
        return crcPresent;
    }

    @Override
    public String toString() {
        return "GenisysFrame[" +
                "header=0x" + Integer.toHexString(header & 0xFF) +
                ", station=" + stationAddress +
                ", payloadLength=" + payload.length +
                ", crcPresent=" + crcPresent +
                ']';
    }
}
