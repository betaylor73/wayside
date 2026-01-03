package com.questrail.wayside.protocol.genisys.internal.decode;

import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;
import com.questrail.wayside.protocol.genisys.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link GenisysMessageDecoder}.
 *
 * These tests validate the inbound semantic boundary:
 *   GenisysFrame -> GenisysMessage
 */
final class GenisysMessageDecoderTest
{
    private final GenisysMessageDecoder decoder = new GenisysMessageDecoder(
            payload -> null,
            payload -> null
    );

    @Test
    void decodeAcknowledgeFrame()
    {
        GenisysFrame frame = new GenisysFrame(
                (byte) 0xF1,
                3,
                new byte[0],
                false
        );

        GenisysMessage msg = decoder.decode(frame);

        assertInstanceOf(Acknowledge.class, msg);
        assertEquals(3, msg.station().value());
    }

    @Test
    void decodeSecurePollFrame()
    {
        GenisysFrame frame = new GenisysFrame(
                (byte) 0xFB,
                7,
                new byte[0],
                true
        );

        GenisysMessage msg = decoder.decode(frame);

        assertInstanceOf(Poll.class, msg);
        Poll poll = (Poll) msg;
        assertEquals(7, poll.station().value());
        assertTrue(poll.secure());
    }

    @Test
    void decodeNonSecurePollFrame()
    {
        GenisysFrame frame = new GenisysFrame(
                (byte) 0xFB,
                7,
                new byte[0],
                false
        );

        GenisysMessage msg = decoder.decode(frame);

        assertInstanceOf(Poll.class, msg);
        Poll poll = (Poll) msg;
        assertFalse(poll.secure());
    }

    @Test
    void decodeIndicationDataFrame()
    {
        byte[] payload = new byte[] { 0x10, 0x20 };

        GenisysFrame frame = new GenisysFrame(
                (byte) 0xF2,
                4,
                payload,
                true
        );

        GenisysMessage msg = decoder.decode(frame);

        assertInstanceOf(IndicationData.class, msg);
        assertEquals(4, msg.station().value());
    }

    @Test
    void decodeControlDataFrame()
    {
        byte[] payload = new byte[] { 0x01, 0x02 };

        GenisysFrame frame = new GenisysFrame(
                (byte) 0xFC,
                12,
                payload,
                true
        );

        GenisysMessage msg = decoder.decode(frame);

        assertInstanceOf(ControlData.class, msg);
        assertEquals(12, msg.station().value());
    }

    @Test
    void decodeControlCheckbackFrame()
    {
        byte[] payload = new byte[] { 0x05 };

        GenisysFrame frame = new GenisysFrame(
                (byte) 0xF3,
                13,
                payload,
                true
        );

        GenisysMessage msg = decoder.decode(frame);

        assertInstanceOf(ControlCheckback.class, msg);
        assertEquals(13, msg.station().value());
    }

    @Test
    void rejectMissingRequiredCrc()
    {
        GenisysFrame frame = new GenisysFrame(
                (byte) 0xF2, // IndicationData requires CRC
                1,
                new byte[] { 0x01 },
                false
        );

        assertThrows(GenisysDecodeException.class,
                () -> decoder.decode(frame));
    }

   @Test
   void rejectAcknowledgeWithUnexpectedCrc()
   {
       GenisysFrame frame = new GenisysFrame(
               (byte) 0xF1, // ACK must never carry CRC
               2,
               new byte[0],
               true
       );

       assertThrows(GenisysDecodeException.class,
               () -> decoder.decode(frame));
   }

    @Test
    void decodeRecallFrameRequiresCrc()
    {
        GenisysFrame frame = new GenisysFrame(
                (byte) 0xFD,
                5,
                new byte[0],
                false
        );

        assertThrows(GenisysDecodeException.class,
                () -> decoder.decode(frame));
    }

    @Test
    void decodeExecuteControlsFrameRequiresCrc()
    {
        GenisysFrame frame = new GenisysFrame(
                (byte) 0xFE,
                6,
                new byte[0],
                false
        );

        assertThrows(GenisysDecodeException.class,
                () -> decoder.decode(frame));
    }

    @Test
    void decodeAcknowledgeAndPollRequiresCrc()
    {
        GenisysFrame frame = new GenisysFrame(
                (byte) 0xFA,
                7,
                new byte[0],
                false
        );

        assertThrows(GenisysDecodeException.class,
                () -> decoder.decode(frame));
    }
}
