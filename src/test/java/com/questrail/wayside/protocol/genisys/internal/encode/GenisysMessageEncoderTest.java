package com.questrail.wayside.protocol.genisys.internal.encode;

import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;
import com.questrail.wayside.protocol.genisys.model.*;
        import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class GenisysMessageEncoderTest
{
    @Test
    void encodeAcknowledge_hasNoCrcAndNoPayload()
    {
        GenisysMessageEncoder encoder = new GenisysMessageEncoder(
                indications -> new byte[] { 0x01 },
                controls -> new byte[] { 0x02 }
        );

        GenisysFrame frame = encoder.encode(new Acknowledge(GenisysStationAddress.of(1)));

        assertEquals(0xF1, frame.header() & 0xFF);
        assertEquals(1, frame.stationAddress());
        assertFalse(frame.crcPresent());
        assertEquals(0, frame.payload().length);
    }

    @Test
    void encodePoll_secureControlsCrcPresence()
    {
        GenisysMessageEncoder encoder = new GenisysMessageEncoder(
                indications -> new byte[0],
                controls -> new byte[0]
        );

        GenisysFrame secure = encoder.encode(new Poll(GenisysStationAddress.of(5), true));
        assertEquals(0xFB, secure.header() & 0xFF);
        assertTrue(secure.crcPresent());

        GenisysFrame nonSecure = encoder.encode(new Poll(GenisysStationAddress.of(5), false));
        assertEquals(0xFB, nonSecure.header() & 0xFF);
        assertFalse(nonSecure.crcPresent());
    }

    @Test
    void encodeIndicationData_usesInjectedPayloadEncoder()
    {
        byte[] encodedPayload = new byte[] { 0x11, 0x22 };

        GenisysMessageEncoder encoder = new GenisysMessageEncoder(
                indications -> encodedPayload,
                controls -> new byte[0]
        );

        IndicationSet dummy = null; // We do not invent ControlSet/IndicationSet construction here.
        GenisysFrame frame = encoder.encode(new IndicationData(GenisysStationAddress.of(2), dummy));

        assertEquals(0xF2, frame.header() & 0xFF);
        assertEquals(2, frame.stationAddress());
        assertTrue(frame.crcPresent());
        assertArrayEquals(encodedPayload, frame.payload());
    }

    @Test
    void encodeControlData_usesInjectedPayloadEncoder()
    {
        byte[] encodedPayload = new byte[] { 0x33 };

        GenisysMessageEncoder encoder = new GenisysMessageEncoder(
                indications -> new byte[0],
                controls -> encodedPayload
        );

        ControlSet dummy = null;
        GenisysFrame frame = encoder.encode(new ControlData(GenisysStationAddress.of(3), dummy));

        assertEquals(0xFC, frame.header() & 0xFF);
        assertTrue(frame.crcPresent());
        assertArrayEquals(encodedPayload, frame.payload());
    }
}
