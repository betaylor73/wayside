package com.questrail.wayside.protocol.genisys.codec.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * GenisysCrcTest
 * -----------------------------------------------------------------------------
 * Unit tests for {@link GenisysCrc}.
 *
 * <p>These tests validate the CRC behavior used in this module: CRC-16/ARC
 * (also known as CRC-IBM or CRC-16/ARC). The implementation parameters are:
 * polynomial 0x8005 (reflected 0xA001), initial value 0x0000, reflected
 * input/output, XOROUT 0x0000. CRC is computed over all message bytes except
 * the terminator and appended as two bytes (big-endian) at the end of the
 * payload.</p>
 *
 * <p>The tests are intentionally wire-level and semantics-free.</p>
 */
final class GenisysCrcTest
{
    @Test
    void hasCrcShortPayload()
    {
        byte[] payload = new byte[] { (byte) 0xF1, 0x01 };
        assertFalse(GenisysCrc.hasCrc(payload));
    }

    @Test
    void hasCrcPayloadWithRoomForCrc()
    {
        byte[] payload = new byte[] { (byte) 0xF1, 0x01, 0x12, 0x34 };
        assertTrue(GenisysCrc.hasCrc(payload));
    }

    @Test
    void appendCrcProducesPayloadThatValidates()
    {
        byte[] body = new byte[] { (byte) 0xF2, 0x01, 0x10, 0x20 };
        byte[] withCrc = GenisysCrc.appendCrc(body);
        assertDoesNotThrow(() -> GenisysCrc.validate(withCrc));
    }

    @Test
    void validateValid()
    {
        byte[] message = new byte[] { (byte) 0xF2, 0x01, 0x10 };
        int crc = invokeCompute(message);
        byte[] payload = new byte[] {
                (byte) 0xF2,
                0x01,
                0x10,
                (byte) ((crc >>> 8) & 0xFF),
                (byte) (crc & 0xFF)
        };

        assertDoesNotThrow(() -> GenisysCrc.validate(payload));
    }

    @Test
    void validateInvalid()
    {
        byte[] payload = new byte[] {
                (byte) 0xF2,
                0x01,
                0x10,
                0x00,
                0x00
        };

        assertThrows(CrcException.class, () -> GenisysCrc.validate(payload));
    }

    @Test
    void stripCrc()
    {
        byte[] payload = new byte[] {
                (byte) 0xF2,
                0x01,
                0x10,
                0x12,
                0x34
        };

        byte[] stripped = GenisysCrc.stripCrc(payload);
        assertArrayEquals(new byte[] { (byte) 0xF2, 0x01, 0x10 }, stripped);
    }

    @Test
    void computeDeterministic()
    {
        byte[] message = new byte[] { (byte) 0xF2, 0x01, 0x10, 0x20, 0x30 };
        int c1 = invokeCompute(message);
        int c2 = invokeCompute(message);
        assertEquals(c1, c2);
    }

    /**
     * Helper that mirrors GenisysCrc.compute for test purposes without
     * exposing it publicly. This implements the reflected CRC-16/ARC algorithm
     * (polynomial 0x8005, reflected 0xA001) with initial value 0x0000.
     */
    private static int invokeCompute(byte[] data)
    {
        int crc = 0x0000; // INIT for CRC-16/ARC
        for (byte b : data) {
            crc ^= (b & 0xFF);
            for (int i = 0; i < 8; i++) {
                if ((crc & 0x0001) != 0) {
                    crc = (crc >>> 1) ^ 0xA001;
                } else {
                    crc = (crc >>> 1);
                }
            }
            crc &= 0xFFFF;
        }
        return crc & 0xFFFF;
    }
}
