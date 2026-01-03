package com.questrail.wayside.protocol.genisys.codec.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class GenisysEscapingTest
{
    // ---------------------------------------------------------------------
    // Identity Tests
    // ---------------------------------------------------------------------

    /**
     * Verifies that a buffer containing no escape bytes (no values in $F0-$FF)
     * is returned unchanged by the unescape routine (content and length preserved).
     */
    @Test
    void noEscapeBytesReturnsSameArray() throws EscapeException {
        byte[] input = new byte[] { 0x01, 0x02, 0x7F, 0x20 };
        byte[] out = GenisysEscaping.unescape(input);
        assertArrayEquals(input, out);
    }

    /**
     * Verifies that an empty byte array is returned as-is (no modification or
     * exception) by the unescape routine.
     */
    @Test
    void emptyArrayReturnsEmptyArray() throws EscapeException {
        byte[] input = new byte[0];
        byte[] out = GenisysEscaping.unescape(input);
        assertArrayEquals(input, out);
    }

    // ---------------------------------------------------------------------
    // Single Escape
    // ---------------------------------------------------------------------

    /**
     * Verifies that a single escaped byte sequence (0xF0 followed by offset)
     * reconstructs to the original high-value byte when unescaped.
     * Example: original 0xF2 is encoded as {0xF0, 0x02} and should be restored.
     */
    @Test
    void singleEscapedByteReconstructsOriginalValue() throws EscapeException {
        // Build escaped sequence: header byte, escaped representation of 0xF2, tail
        byte[] escaped = new byte[] { 0x11, (byte) GenisysFraming.ESCAPE, (byte) 0x02, 0x33 };
        byte[] unescaped = GenisysEscaping.unescape(escaped);
        // Expect reconstructed 0xF2 in place of the two-byte escape
        byte[] expected = new byte[] { 0x11, (byte) 0xF2, 0x33 };
        assertArrayEquals(expected, unescaped);
    }

    // ---------------------------------------------------------------------
    // Multiple Escapes
    // ---------------------------------------------------------------------

    /**
     * Verifies that multiple escaped sequences in the input are all reconstructed
     * in the correct order and positions in the output array.
     */
    @Test
    void multipleEscapedBytesReconstructsAllInOrder() throws EscapeException {
        // Original bytes to reconstruct: 0xF1, 0xF5, 0x20
        // Escaped representations: {F0,01}, {F0,05}
        byte[] escaped = new byte[] {
                (byte) GenisysFraming.ESCAPE, 0x01,
                0x55,
                (byte) GenisysFraming.ESCAPE, 0x05,
                0x10
        };

        byte[] unescaped = GenisysEscaping.unescape(escaped);
        byte[] expected = new byte[] { (byte) 0xF1, 0x55, (byte) 0xF5, 0x10 };
        assertArrayEquals(expected, unescaped);
    }

    // ---------------------------------------------------------------------
    // Boundary Values
    // ---------------------------------------------------------------------

    /**
     * Verifies that an escaped representation reconstructs the escape byte
     * itself: original 0xF0 is encoded as {0xF0, 0x00} and should be restored
     * to a single 0xF0 byte in the output.
     */
    @Test
    void escapedF0ReconstructsF0() throws EscapeException {
        byte[] escaped = new byte[] { (byte) GenisysFraming.ESCAPE, 0x00 };
        byte[] out = GenisysEscaping.unescape(escaped);
        assertArrayEquals(new byte[] { (byte) GenisysFraming.ESCAPE }, out);
    }

    /**
     * Verifies that an escaped representation reconstructs 0xFF correctly:
     * 0xFF is encoded as {0xF0, 0x0F} and should be restored to 0xFF.
     */
    @Test
    void escapedFFReconstructsFF() throws EscapeException {
        byte[] escaped = new byte[] { (byte) GenisysFraming.ESCAPE, 0x0F };
        byte[] out = GenisysEscaping.unescape(escaped);
        assertArrayEquals(new byte[] { (byte) 0xFF }, out);
    }

    // ---------------------------------------------------------------------
    // Error Handling
    // ---------------------------------------------------------------------

    /**
     * Verifies that a dangling escape byte (0xF0 at end of input with no
     * following offset byte) causes an EscapeException to be thrown.
     */
    @Test
    void danglingEscapeByte_throwsEscapeException() {
        byte[] escaped = new byte[] { 0x10, (byte) GenisysFraming.ESCAPE };
        assertThrows(EscapeException.class, () -> GenisysEscaping.unescape(escaped));
    }

}
