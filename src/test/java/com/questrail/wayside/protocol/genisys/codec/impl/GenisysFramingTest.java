package com.questrail.wayside.protocol.genisys.codec.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GenisysFramingTest
{
    // ---------------------------------------------------------------------
    // Happy Path
    // ---------------------------------------------------------------------

    /**
     * Verifies that a well-formed datagram containing a valid header/control byte
     * followed by payload bytes and a terminator returns the bytes from the
     * header up to (but excluding) the terminator; trailing bytes after the
     * terminator are ignored.
     */
    @Test
    void returnsHeaderThroughLastByteBeforeTerminator()
            throws FramingException
    {
        byte[] datagram = new byte[] {
                (byte) 0xF1, // header
                0x10, 0x20, 0x30, // payload (station/address + data)
                (byte) 0xF6, // terminator
                0x00 // trailing garbage (should be ignored)
        };

        byte[] extracted = GenisysFraming.extractMessageBytes(datagram);
        byte[] expected = new byte[] { (byte) 0xF1, 0x10, 0x20, 0x30 };

        assertArrayEquals(expected, extracted);
    }

    // ---------------------------------------------------------------------
    // Header Detection
    // ---------------------------------------------------------------------

    /**
     * Verifies that any leading non-header bytes are skipped and the first
     * valid header/control byte (0xF1-0xFE excluding reserved values) is used
     * as the start of the extracted message.
     */
    @Test
    void skipsLeadingGarbageUntilHeaderFound() throws FramingException {
        byte[] datagram = new byte[] {
                0x01, 0x02, // leading garbage
                (byte) 0xF2, // header
                0x11,
                (byte) 0xF6
        };

        byte[] extracted = GenisysFraming.extractMessageBytes(datagram);
        byte[] expected = new byte[] { (byte) 0xF2, 0x11 };
        assertArrayEquals(expected, extracted);
    }

    /**
     * Verifies that a datagram containing no legal header/control byte causes a
     * FramingException to be thrown (header bytes must be in the allowed range).
     */
    @Test
    void rejectsWhenNoHeaderControlBytePresent() {
        byte[] datagram = new byte[] { 0x00, 0x01, 0x02, 0x03 };
        assertThrows(FramingException.class, () -> GenisysFraming.extractMessageBytes(datagram));
    }

    // ---------------------------------------------------------------------
    // Terminator Handling
    // ---------------------------------------------------------------------

    /**
     * Verifies that if no terminator (0xF6) appears after the header, a
     * FramingException is thrown because a complete GENISYS message is
     * not delimited.
     */
    @Test
    void rejectsWhenNoTerminatorPresent() {
        byte[] datagram = new byte[] { (byte) 0xF1, 0x10, 0x11 };
        assertThrows(FramingException.class, () -> GenisysFraming.extractMessageBytes(datagram));
    }

    /**
     * Verifies that a terminator byte occurring before the header does not
     * satisfy framing; the implementation searches for a terminator only after
     * the header, so this case results in a FramingException when no later
     * terminator exists.
     */
    @Test
    void rejectsWhenTerminatorPrecedesHeader() {
        // Terminator appears before the header and there is no terminator after
        // the header. The framing code should treat this as a missing terminator
        // (it only searches for a terminator after the header).
        byte[] datagram = new byte[] { (byte) 0xF6, 0x00, (byte) 0xF1, 0x22 };
        assertThrows(FramingException.class, () -> GenisysFraming.extractMessageBytes(datagram));
    }

    // ---------------------------------------------------------------------
    // Structural Validity
    // ---------------------------------------------------------------------

    /**
     * Verifies that a message containing only a header immediately followed by
     * a terminator (i.e. no station/address byte) is rejected as structurally
     * invalid.
     */
    @Test
    void rejectsMessageWithOnlyHeaderAndTerminator() {
        byte[] datagram = new byte[] { (byte) 0xF1, (byte) 0xF6 };
        assertThrows(FramingException.class, () -> GenisysFraming.extractMessageBytes(datagram));
    }

    /**
     * Verifies that datagrams shorter than the minimum allowed length (less
     * than 2 bytes) are rejected immediately as too short to contain a header
     * and terminator.
     */
    @Test
    void rejectsTooShortDatagram() {
        byte[] datagram = new byte[] { (byte) 0xF1 }; // length 1
        assertThrows(FramingException.class, () -> GenisysFraming.extractMessageBytes(datagram));
    }

    // ---------------------------------------------------------------------
    // Header Byte Legality
    // ---------------------------------------------------------------------

    /**
     * Verifies that header/control bytes at the lower bound (0xF1) and upper
     * bound (0xFE) of the allowed range are accepted as valid headers and
     * produce the expected extraction result.
     */
    @Test
    void acceptsHeaderInF1ThroughFERange() throws FramingException {
        // Test lower bound
        byte[] datagram1 = new byte[] { (byte) 0xF1, 0x01, (byte) 0xF6 };
        byte[] extracted1 = GenisysFraming.extractMessageBytes(datagram1);
        assertArrayEquals(new byte[] { (byte) 0xF1, 0x01 }, extracted1);

        // Test upper bound (0xFE)
        byte[] datagram2 = new byte[] { (byte) 0xFE, 0x02, (byte) 0xF6 };
        byte[] extracted2 = GenisysFraming.extractMessageBytes(datagram2);
        assertArrayEquals(new byte[] { (byte) 0xFE, 0x02 }, extracted2);
    }

    /**
     * Verifies that reserved byte values are not accepted as headers: specifically
     * the terminator value 0xF6 must not be treated as a header, and 0xFF is
     * reserved/not used and must be rejected.
     */
    @Test
    void rejectsReservedHeaderValues() {
        // 0xF6 is the terminator and must not be treated as a header
        byte[] datagramTermFirst = new byte[] { (byte) 0xF6, 0x01, (byte) 0xF6 };
        assertThrows(FramingException.class, () -> GenisysFraming.extractMessageBytes(datagramTermFirst));

        // 0xFF is reserved/not used and should not be accepted as a header
        byte[] datagramReserved = new byte[] { (byte) 0xFF, 0x01, (byte) 0xF6 };
        assertThrows(FramingException.class, () -> GenisysFraming.extractMessageBytes(datagramReserved));
    }
}
