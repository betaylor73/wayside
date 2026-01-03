package com.questrail.wayside.protocol.genisys.codec.impl;

import java.util.Arrays;

/**
 * GenisysEscaping
 * -----------------------------------------------------------------------------
 * Implements GENISYS byte escaping and unescaping rules.
 *
 * <p>This class reverses any escaping applied at the wire level so that higher
 * layers operate on canonical byte sequences.</p>
 */
final class GenisysEscaping
{
    private GenisysEscaping() {}

    static byte[] unescape(byte[] escaped)
            throws EscapeException
    {
        // GENISYS.pdf (Section 5.1.1.1 Control Character) defines byte escaping:
        //
        //   • Any data byte with value in the range $F0-$FF is transmitted as a
        //     two-byte sequence: $F0 followed by (<byte value> - $F0).
        //   • On reception, whenever $F0 is encountered, it is arithmetically added
        //     to the immediately following byte to reconstruct the original data byte.
        //
        // This unescape routine is purely mechanical. It does not interpret message
        // fields, and it must be applied before CRC validation (CRC is calculated
        // before escape characters are inserted per GENISYS.pdf Section 5.1.1.4).

        if (escaped == null) {
            throw new EscapeException("Escaped buffer must not be null");
        }
        if (escaped.length == 0) {
            return escaped;
        }

        // Output cannot be larger than input; allocate input length and shrink.
        byte[] out = new byte[escaped.length];
        int w = 0;

        for (int r = 0; r < escaped.length; r++) {
            int b = escaped[r] & 0xFF;

            if (b != GenisysFraming.ESCAPE) {
                out[w++] = (byte) b;
                continue;
            }

            // $F0 must always be followed by one byte whose value is added to $F0.
            if (r + 1 >= escaped.length) {
                throw new EscapeException("Dangling escape byte ($F0) at end of message");
            }

            int next = escaped[++r] & 0xFF;
            int reconstructed = (GenisysFraming.ESCAPE + next) & 0xFF;
            out[w++] = (byte) reconstructed;
        }

        return (w == out.length) ? out : Arrays.copyOf(out, w);
    }
}
