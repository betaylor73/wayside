package com.questrail.wayside.protocol.genisys.codec.impl;

import com.questrail.wayside.protocol.genisys.codec.GenisysFrameDecoder;
import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;

import java.util.Arrays;
import java.util.Optional;

/**
 * DefaultGenisysFrameDecoder
 * -----------------------------------------------------------------------------
 * Concrete implementation of {@link GenisysFrameDecoder}.
 *
 * <p>This decoder performs the following steps, in order:</p>
 * <ol>
 *   <li>Framing (GENISYS.pdf §5.1 / §5.1.1)</li>
 *   <li>Unescaping (GENISYS.pdf §5.1.1.1)</li>
 *   <li>CRC detection and validation (GENISYS.pdf §5.1.1.4)</li>
 *   <li>Structural parsing into {@link GenisysFrame}</li>
 * </ol>
 *
 * <p><strong>Protocol-mandated CRC presence determination</strong> (GENISYS.pdf §5.1.1.4):</p>
 * <ul>
 *   <li>$F1 Acknowledge: CRC is never present</li>
 *   <li>$FB Poll: CRC is present only for secure poll (presence inferred structurally)</li>
 *   <li>All other headers: CRC is required</li>
 * </ul>
 */
public final class DefaultGenisysFrameDecoder implements GenisysFrameDecoder
{
    @Override
    public Optional<GenisysFrame> decode(byte[] datagram)
    {
        try {
            // 1) Framing: extract header..last-byte-before-terminator
            final byte[] framed = GenisysFraming.extractMessageBytes(datagram);

            // 2) Unescape per GENISYS.pdf §5.1.1.1
            final byte[] unescaped = GenisysEscaping.unescape(framed);

            if (unescaped.length < 2) {
                throw new FramingException("GENISYS message too short");
            }

            final int header = unescaped[0] & 0xFF;

            // 3) CRC presence is protocol-law based on header (except Poll)
            final boolean crcPresent;
            if (header == 0xF1) {
                crcPresent = false;
            }
            else if (header == 0xFB) {
                // Poll: CRC present only for secure poll; infer by length
                crcPresent = GenisysCrc.hasCrc(unescaped);
            }
            else {
                crcPresent = true;
            }

            final byte[] crcStripped;
            if (crcPresent) {
                if (unescaped.length < 4) {
                    throw new FramingException("CRC required but payload too short");
                }
                GenisysCrc.validate(unescaped);
                crcStripped = GenisysCrc.stripCrc(unescaped);
            } else {
                crcStripped = unescaped;
            }

            // 4) Structural parse
            //    byte 0 : header/control
            //    byte 1 : station address
            //    byte 2..N : message payload
            final int station = crcStripped[1] & 0xFF;
            final byte[] payload = Arrays.copyOfRange(crcStripped, 2, crcStripped.length);

            // 5) Construct post-wire GenisysFrame
            return Optional.of(new GenisysFrame((byte) (header & 0xFF), station, payload, crcPresent));
        }
        catch (FramingException | EscapeException | CrcException e) {
            // Wire-level failure → drop datagram
            return Optional.empty();
        }
    }
}
