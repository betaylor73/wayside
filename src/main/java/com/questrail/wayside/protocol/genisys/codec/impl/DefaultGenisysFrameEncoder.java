package com.questrail.wayside.protocol.genisys.codec.impl;

import com.questrail.wayside.protocol.genisys.codec.GenisysFrameEncoder;
import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;

import java.util.Arrays;
import java.util.Objects;

/**
 * DefaultGenisysFrameEncoder
 * -----------------------------------------------------------------------------
 * Concrete implementation of {@link GenisysFrameEncoder}.
 *
 * <p>This is the mechanical inverse of {@link DefaultGenisysFrameDecoder}.
 * It assumes the caller has already produced a correctly structured
 * {@link GenisysFrame} (including correct header and payload bytes).</p>
 */
public final class DefaultGenisysFrameEncoder implements GenisysFrameEncoder
{
    @Override
    public byte[] encode(GenisysFrame frame)
    {
        Objects.requireNonNull(frame, "frame");

        // ---------------------------------------------------------------------
        // 1) Construct canonical, unescaped body:
        //    [ header ][ station ][ payload... ][ optional CRC ]
        // ---------------------------------------------------------------------

        final byte header = frame.header();
        final byte station = (byte) frame.stationAddress();
        final byte[] payload = frame.payload();

        byte[] body = new byte[2 + payload.length];
        body[0] = header;
        body[1] = station;
        System.arraycopy(payload, 0, body, 2, payload.length);

        if (frame.crcPresent()) {
            body = GenisysCrc.appendCrc(body);
        }

        // ---------------------------------------------------------------------
        // 2) Apply escaping (header byte is never escaped per GENISYS framing rules)
        // ---------------------------------------------------------------------

        byte[] escaped = escapeBody(body);

        // ---------------------------------------------------------------------
        // 3) Append terminator ($F6)
        // ---------------------------------------------------------------------

        byte[] datagram = Arrays.copyOf(escaped, escaped.length + 1);
        datagram[datagram.length - 1] = (byte) GenisysFraming.TERMINATOR;
        return datagram;
    }

    private static byte[] escapeBody(byte[] body)
    {
        // Fast path: detect whether escaping is required for bytes after header.
        boolean needsEscape = false;
        for (int i = 1; i < body.length; i++) {
            if ((body[i] & 0xFF) >= GenisysFraming.ESCAPE) {
                needsEscape = true;
                break;
            }
        }

        if (!needsEscape) {
            return body;
        }

        // Worst-case expansion: each byte after header could become two bytes.
        byte[] out = new byte[1 + (body.length - 1) * 2];
        int w = 0;

        // Header/control byte is written verbatim.
        out[w++] = body[0];

        for (int i = 1; i < body.length; i++) {
            int v = body[i] & 0xFF;
            if (v >= GenisysFraming.ESCAPE) {
                out[w++] = (byte) GenisysFraming.ESCAPE;
                out[w++] = (byte) (v - GenisysFraming.ESCAPE);
            } else {
                out[w++] = body[i];
            }
        }

        return (w == out.length) ? out : Arrays.copyOf(out, w);
    }
}
