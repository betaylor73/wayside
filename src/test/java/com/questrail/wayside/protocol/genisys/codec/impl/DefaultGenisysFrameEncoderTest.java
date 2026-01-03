package com.questrail.wayside.protocol.genisys.codec.impl;

import com.questrail.wayside.protocol.genisys.codec.GenisysFrameEncoder;
import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class DefaultGenisysFrameEncoderTest
{
    private final GenisysFrameEncoder encoder = new DefaultGenisysFrameEncoder();

    @Test
    void encodeAckFrameWithoutCrc_appendsTerminator()
    {
        GenisysFrame frame = new GenisysFrame(
                (byte) 0xF1,
                0x01,
                new byte[0],
                false
        );

        byte[] datagram = encoder.encode(frame);

        assertArrayEquals(new byte[] { (byte) 0xF1, 0x01, (byte) 0xF6 }, datagram);
    }

    @Test
    void encodeCrcBearingFrame_isAcceptedByDecoder()
    {
        GenisysFrame frame = new GenisysFrame(
                (byte) 0xF2,
                0x01,
                new byte[] { 0x10, 0x20 },
                true
        );

        byte[] datagram = encoder.encode(frame);
        assertEquals((byte) 0xF6, datagram[datagram.length - 1]);

        DefaultGenisysFrameDecoder decoder = new DefaultGenisysFrameDecoder();
        assertTrue(decoder.decode(datagram).isPresent());
    }

    @Test
    void encodeAppliesEscaping_whenBodyContainsControlRangeBytes()
    {
        GenisysFrame frame = new GenisysFrame(
                (byte) 0xF2,
                0x01,
                new byte[] { (byte) 0xF0 },
                true
        );

        byte[] datagram = encoder.encode(frame);

        boolean foundEscape = false;
        for (byte b : datagram) {
            if ((b & 0xFF) == GenisysFraming.ESCAPE) {
                foundEscape = true;
                break;
            }
        }
        assertTrue(foundEscape);
    }
}
