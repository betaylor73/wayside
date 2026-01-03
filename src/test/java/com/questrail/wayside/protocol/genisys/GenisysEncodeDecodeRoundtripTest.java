package com.questrail.wayside.protocol.genisys;

import com.questrail.wayside.protocol.genisys.codec.impl.DefaultGenisysFrameDecoder;
import com.questrail.wayside.protocol.genisys.codec.impl.DefaultGenisysFrameEncoder;
import com.questrail.wayside.protocol.genisys.internal.decode.GenisysMessageDecoder;
import com.questrail.wayside.protocol.genisys.internal.encode.GenisysMessageEncoder;
import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;
import com.questrail.wayside.protocol.genisys.model.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 4 semantic round-trip tests.
 *
 * These tests prove:
 *   GenisysMessage -> bytes -> GenisysMessage
 * preserves semantic meaning.
 */
final class GenisysEncodeDecodeRoundTripTest
{
    private final GenisysMessageEncoder messageEncoder =
            new GenisysMessageEncoder(
                    indications -> new byte[] { 0x11 },
                    controls -> new byte[] { 0x22 }
            );

    private final DefaultGenisysFrameEncoder frameEncoder =
            new DefaultGenisysFrameEncoder();

    private final DefaultGenisysFrameDecoder frameDecoder =
            new DefaultGenisysFrameDecoder();

    private final GenisysMessageDecoder messageDecoder =
            new GenisysMessageDecoder(
                    payload -> null,
                    payload -> null
            );

    @Test
    void acknowledgeRoundTrip()
    {
        GenisysMessage original = new Acknowledge(GenisysStationAddress.of(1));

        GenisysFrame frame = messageEncoder.encode(original);
        byte[] bytes = frameEncoder.encode(frame);
        GenisysFrame decodedFrame = frameDecoder.decode(bytes).orElseThrow();
        GenisysMessage decoded = messageDecoder.decode(decodedFrame);

        assertInstanceOf(Acknowledge.class, decoded);
        assertEquals(original.station().value(), decoded.station().value());
    }

    @Test
    void securePollRoundTrip()
    {
        GenisysMessage original = new Poll(GenisysStationAddress.of(9), true);

        GenisysFrame frame = messageEncoder.encode(original);
        byte[] bytes = frameEncoder.encode(frame);
        GenisysFrame decodedFrame = frameDecoder.decode(bytes).orElseThrow();
        GenisysMessage decoded = messageDecoder.decode(decodedFrame);

        assertInstanceOf(Poll.class, decoded);
        Poll poll = (Poll) decoded;
        assertTrue(poll.secure());
        assertEquals(9, poll.station().value());
    }

    @Test
    void nonSecurePollRoundTrip()
    {
        GenisysMessage original = new Poll(GenisysStationAddress.of(9), false);

        GenisysFrame frame = messageEncoder.encode(original);
        byte[] bytes = frameEncoder.encode(frame);
        GenisysFrame decodedFrame = frameDecoder.decode(bytes).orElseThrow();
        GenisysMessage decoded = messageDecoder.decode(decodedFrame);

        assertInstanceOf(Poll.class, decoded);
        Poll poll = (Poll) decoded;
        assertFalse(poll.secure());
        assertEquals(9, poll.station().value());
    }

    @Test
    void recallRoundTrip()
    {
        GenisysMessage original = new Recall(GenisysStationAddress.of(3));

        GenisysFrame frame = messageEncoder.encode(original);
        byte[] bytes = frameEncoder.encode(frame);
        GenisysFrame decodedFrame = frameDecoder.decode(bytes).orElseThrow();
        GenisysMessage decoded = messageDecoder.decode(decodedFrame);

        assertInstanceOf(Recall.class, decoded);
        assertEquals(3, decoded.station().value());
    }

    @Test
    void acknowledgeAndPollRoundTrip()
    {
        GenisysMessage original =
                new AcknowledgeAndPoll(GenisysStationAddress.of(4));

        GenisysFrame frame = messageEncoder.encode(original);
        byte[] bytes = frameEncoder.encode(frame);
        GenisysFrame decodedFrame = frameDecoder.decode(bytes).orElseThrow();
        GenisysMessage decoded = messageDecoder.decode(decodedFrame);

        assertInstanceOf(AcknowledgeAndPoll.class, decoded);
        assertEquals(4, decoded.station().value());
    }

    @Test
    void executeControlsRoundTrip()
    {
        GenisysMessage original =
                new ExecuteControls(GenisysStationAddress.of(5));

        GenisysFrame frame = messageEncoder.encode(original);
        byte[] bytes = frameEncoder.encode(frame);
        GenisysFrame decodedFrame = frameDecoder.decode(bytes).orElseThrow();
        GenisysMessage decoded = messageDecoder.decode(decodedFrame);

        assertInstanceOf(ExecuteControls.class, decoded);
        assertEquals(5, decoded.station().value());
    }

    @Test
    void controlDataRoundTrip_payloadDelivered()
    {
        byte[] expectedPayload = new byte[] { 0x33, 0x44 };

        final byte[][] observed = new byte[1][];

        GenisysMessageEncoder encoder = new GenisysMessageEncoder(
                indications -> new byte[0],
                controls -> expectedPayload
        );

        GenisysMessageDecoder decoder = new GenisysMessageDecoder(
                payload -> null,
                payload -> {
                    observed[0] = payload;
                    return null;
                }
        );

        GenisysMessage original = new ControlData(
                GenisysStationAddress.of(6),
                null
        );

        GenisysFrame frame = encoder.encode(original);
        byte[] bytes = frameEncoder.encode(frame);
        GenisysFrame decodedFrame = frameDecoder.decode(bytes).orElseThrow();
        decoder.decode(decodedFrame);

        assertArrayEquals(expectedPayload, observed[0]);
    }

    // Proves:
    // GenisysMessage
    //  → encode
    //  → frame
    //  → bytes
    //  → decode
    //  → payload handed to correct decoder
    @Test
    void payloadIsDeliveredToPayloadDecoder()
    {
        byte[] expectedPayload = new byte[] { 0x55, 0x66 };

        final byte[][] observed = new byte[1][];

        GenisysMessageEncoder encoder = new GenisysMessageEncoder(
                indications -> expectedPayload,
                controls -> expectedPayload
        );

        GenisysMessageDecoder decoder = new GenisysMessageDecoder(
                payload -> {
                    observed[0] = payload;
                    return null;
                },
                payload -> null
        );

        GenisysMessage original = new IndicationData(
                GenisysStationAddress.of(8),
                null
        );

        GenisysFrame frame = encoder.encode(original);
        byte[] bytes = frameEncoder.encode(frame);
        GenisysFrame decodedFrame = frameDecoder.decode(bytes).orElseThrow();
        decoder.decode(decodedFrame);

        assertArrayEquals(expectedPayload, observed[0]);
    }
}
