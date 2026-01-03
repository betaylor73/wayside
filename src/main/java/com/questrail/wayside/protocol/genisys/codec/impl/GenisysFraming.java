package com.questrail.wayside.protocol.genisys.codec.impl;

import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;

import java.util.Arrays;

/**
 * GenisysFraming
 * -----------------------------------------------------------------------------
 * Implements GENISYS message framing rules as specified in GENISYS.pdf
 * (see Section 5.1 and 5.1.1).
 *
 * <p>GENISYS is framed by:</p>
 * <ul>
 *   <li>a single <em>header/control</em> byte at the start of the message</li>
 *   <li>a single <em>terminator</em> byte at the end of the message</li>
 * </ul>
 *
 * <p>Per GENISYS.pdf:</p>
 * <ul>
 *   <li>Header/control bytes are in the range {@code $F1-$FE}, with certain
 *       values reserved (notably {@code $F6} is the terminator; {@code $F0} is
 *       the escape byte; {@code $FF} is reserved/not used).</li>
 *   <li>The terminator byte is {@code $F6}.</li>
 * </ul>
 *
 * <p>This class is responsible only for locating header/control and terminator
 * bytes and returning the message body between them (exclusive of the
 * terminator). It does <strong>not</strong> perform escaping or CRC validation.</p>
 */
final class GenisysFraming
{
    /** Escape byte ($F0). Used by the escaping layer; documented here because it
     * explains why the terminator byte remains unique on the wire. */
    static final int ESCAPE = 0xF0;

    /** Message terminator byte ($F6). See GENISYS.pdf Section 5.1.1.1. */
    static final int TERMINATOR = 0xF6;

    /** First possible header/control byte ($F1). See GENISYS.pdf Section 5.1.1.1. */
    static final int HEADER_MIN = 0xF1;

    /** Last possible header/control byte ($FE). See GENISYS.pdf Section 5.1.1.1. */
    static final int HEADER_MAX = 0xFE;

    private GenisysFraming() {}

    /**
     * Extracts a single GENISYS message from a raw datagram.
     *
     * <p>The returned bytes include the header/control byte as the first byte and
     * exclude the terminator byte. The caller is expected to apply unescaping and
     * CRC validation before constructing a {@link GenisysFrame}.</p>
     *
     * <p>Why exclude the terminator? GENISYS.pdf specifies that the security
     * checksum (CRC) is computed over all characters except the terminator.</p>
     *
     * @param datagram raw datagram bytes received from transport
     * @return message bytes from header/control through the last byte before the terminator
     * @throws FramingException if no valid header/control byte or terminator can be found
     */
    static byte[] extractMessageBytes(byte[] datagram)
            throws FramingException
    {
        if (datagram == null || datagram.length < 2) {
            throw new FramingException("Datagram too short for GENISYS message");
        }

        final int start = findHeaderStart(datagram);
        final int end = findTerminatorIndex(datagram, start + 1);

        // end is the index of the terminator ($F6). Exclude it.
        if (end <= start) {
            throw new FramingException("Invalid GENISYS framing: terminator precedes header");
        }

        if (end == start + 1) {
            throw new FramingException("GENISYS message contains no station/address byte");
        }

        return Arrays.copyOfRange(datagram, start, end);
    }

    private static int findHeaderStart(byte[] datagram)
            throws FramingException
    {
        for (int i = 0; i < datagram.length; i++) {
            final int b = datagram[i] & 0xFF;
            if (isHeaderControlByte(b)) {
                return i;
            }
        }
        throw new FramingException("Missing GENISYS header/control byte ($F1-$FE excluding reserved values)");
    }

    private static int findTerminatorIndex(byte[] datagram, int fromInclusive)
            throws FramingException
    {
        for (int i = fromInclusive; i < datagram.length; i++) {
            final int b = datagram[i] & 0xFF;
            if (b == TERMINATOR) {
                return i;
            }
        }
        throw new FramingException("Missing GENISYS terminator byte ($F6)");
    }

    /**
     * Returns true if {@code b} can be used as a GENISYS header/control byte.
     *
     * <p>Per GENISYS.pdf Section 5.1.1.1:</p>
     * <ul>
     *   <li>Header/control bytes are in the range $F1-$FE.</li>
     *   <li>$F6 is reserved as the message terminator and therefore cannot be a header.</li>
     *   <li>$FF is reserved/not used.</li>
     * </ul>
     */
    private static boolean isHeaderControlByte(int b)
    {
        return (b >= HEADER_MIN && b <= HEADER_MAX && b != TERMINATOR);
    }
}
