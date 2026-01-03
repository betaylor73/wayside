package com.questrail.wayside.protocol.genisys.codec.impl;

import java.util.Arrays;

/**
 * GenisysCrc
 * -----------------------------------------------------------------------------
 * Implements GENISYS CRC detection and validation.
 *
 * <p><strong>Anchoring note:</strong> This implementation (CRC-16/ARC) is taken
 * directly from the existing codebase in reanchor_bundle.zip and matches the
 * table-driven implementation referenced as {@code CRC16.java}.</p>
 */
final class GenisysCrc
{
    /*
     * CRC-16/ARC implementation (reflected algorithm)
     * -------------------------------------------------------------------------
     * Parameters (match `CRC16.java`):
     *   • Name: CRC-16/ARC (CRC-IBM)
     *   • Width: 16
     *   • Polynomial (normal): 0x8005
     *   • Reflected polynomial: 0xA001
     *   • Initial value (INIT): 0x0000
     *   • Input reflected: true
     *   • Output reflected: true
     *   • XOROUT: 0x0000
     */

    /* Reflected polynomial used for bitwise processing */
    private static final int REFLECTED_POLY = 0xA001;
    private static final int INIT = 0x0000;

    private GenisysCrc() {}

    /**
     * Returns true if the payload contains a CRC.
     *
     * <p>Structural inference only. This is appropriate for headers where CRC is
     * optional (notably Poll $FB), but it is NOT protocol-law for all headers.</p>
     */
    static boolean hasCrc(byte[] payload)
    {
        // Minimum message without CRC is header + station
        return payload != null && payload.length >= 4;
    }

    /**
     * Validates the CRC at the end of the payload.
     *
     * @throws CrcException if the computed CRC does not match the transmitted CRC
     */
    static void validate(byte[] payload) throws CrcException
    {
        if (!hasCrc(payload)) {
            throw new CrcException("CRC expected but payload too short");
        }

        final int len = payload.length;
        final int transmitted = ((payload[len - 2] & 0xFF) << 8)
                |  (payload[len - 1] & 0xFF);

        final int computed = compute(payload, 0, len - 2);

        if (transmitted != computed) {
            throw new CrcException(String.format(
                    "CRC mismatch: transmitted=0x%04X computed=0x%04X",
                    transmitted, computed));
        }
    }

    /**
     * Removes the CRC bytes from the payload.
     */
    static byte[] stripCrc(byte[] payload)
    {
        return Arrays.copyOf(payload, payload.length - 2);
    }

    /**
     * Appends a CRC to the provided message body.
     *
     * <p>This is intended for outbound encoding, where the CRC must be computed
     * over the canonical, <em>unescaped</em> message bytes (header + station +
     * payload), before escaping is applied.</p>
     *
     * <p>The CRC is appended as two bytes (big-endian), consistent with
     * {@link #validate(byte[])}.</p>
     */
    static byte[] appendCrc(byte[] body)
    {
        final int crc = compute(body, 0, body.length);
        final byte[] out = Arrays.copyOf(body, body.length + 2);
        out[out.length - 2] = (byte) ((crc >>> 8) & 0xFF);
        out[out.length - 1] = (byte) (crc & 0xFF);
        return out;
    }

    private static int compute(byte[] data, int off, int len)
    {
        int crc = INIT & 0xFFFF;

        for (int i = off; i < off + len; i++) {
            crc ^= (data[i] & 0xFF);
            for (int b = 0; b < 8; b++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ REFLECTED_POLY;
                } else {
                    crc = (crc >>> 1);
                }
            }
            crc &= 0xFFFF;
        }
        return crc & 0xFFFF;
    }
}
