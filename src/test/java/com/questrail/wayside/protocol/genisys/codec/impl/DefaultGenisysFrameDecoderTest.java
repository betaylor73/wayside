package com.questrail.wayside.protocol.genisys.codec.impl;

import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * DefaultGenisysFrameDecoderTest
 * -----------------------------------------------------------------------------
 * Unit tests for {@link DefaultGenisysFrameDecoder}.
 *
 * <p>These tests exercise the full wire-level decode pipeline defined in
 * GENISYS.pdf §5.1 / §5.1.1:</p>
 *
 * <ul>
 *   <li>framing (header/control + terminator)</li>
 *   <li>escaping</li>
 *   <li>CRC detection and validation</li>
 *   <li>structural extraction into {@link GenisysFrame}</li>
 * </ul>
 *
 * <p>No semantic interpretation is performed here. These tests validate only
 * decode-before-event behavior and drop-on-wire-failure semantics.</p>
 */
final class DefaultGenisysFrameDecoderTest
{
    private final DefaultGenisysFrameDecoder decoder = new DefaultGenisysFrameDecoder();

    @Test
    void decodeValidCrcBearingMessage()
    {
        // Use a CRC-bearing header ($F2) to avoid the ACK ($F1) exception.
        byte[] body = new byte[] { (byte) 0xF2, 0x01, 0x10 };
        byte[] withCrc = GenisysCrc.appendCrc(body);

        byte[] datagram = appendTerminator(withCrc);

        Optional<GenisysFrame> frame = decoder.decode(datagram);
        assertTrue(frame.isPresent());
        assertTrue(frame.get().crcPresent());
    }

    @Test
    void decodeAckWithoutCrc()
    {
        byte[] datagram = new byte[] { (byte) 0xF1, 0x01, (byte) 0xF6 };

        GenisysFrame frame = decoder.decode(datagram).orElseThrow();
        assertEquals(0xF1, frame.header() & 0xFF);
        assertEquals(0x01, frame.stationAddress());
        assertFalse(frame.crcPresent());
        assertArrayEquals(new byte[0], frame.payload());
    }

    @Test
    void decodeExtractsFields()
    {
        byte[] body = new byte[] { (byte) 0xF2, 0x05, 0x20, 0x30 };
        byte[] withCrc = GenisysCrc.appendCrc(body);

        GenisysFrame frame = decoder.decode(appendTerminator(withCrc)).orElseThrow();

        assertEquals(0xF2, frame.header() & 0xFF);
        assertEquals(0x05, frame.stationAddress());
        assertArrayEquals(new byte[] { 0x20, 0x30 }, frame.payload());
        assertTrue(frame.crcPresent());
    }

    @Test
    void decodeRejectsMissingRequiredCrc()
    {
        // $F2 requires CRC; provide none.
        byte[] datagram = new byte[] { (byte) 0xF2, 0x01, 0x10, (byte) 0xF6 };
        assertTrue(decoder.decode(datagram).isEmpty());
    }

    @Test
    void decodeInvalidFraming()
    {
        // Missing terminator
        byte[] datagram = new byte[] { (byte) 0xF2, 0x01, 0x10 };
        assertTrue(decoder.decode(datagram).isEmpty());
    }

    @Test
    void decodeInvalidEscape()
    {
        // Dangling escape byte before terminator
        byte[] datagram = new byte[] {
                (byte) 0xF2,
                0x01,
                (byte) 0xF0,
                (byte) 0xF6
        };

        assertTrue(decoder.decode(datagram).isEmpty());
    }

    @Test
    void decodeCrcMismatch()
    {
        // $F2 requires CRC, but provide an incorrect CRC.
        byte[] datagram = new byte[] {
                (byte) 0xF2,
                0x01,
                0x10,
                0x00,
                0x00,
                (byte) 0xF6
        };

        assertTrue(decoder.decode(datagram).isEmpty());
    }

    private static byte[] appendTerminator(byte[] body)
    {
        byte[] datagram = Arrays.copyOf(body, body.length + 1);
        datagram[datagram.length - 1] = (byte) 0xF6;
        return datagram;
    }
}
