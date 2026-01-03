package com.questrail.wayside.protocol.genisys.codec;

import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;

/**
 * GenisysFrameEncoder
 * -----------------------------------------------------------------------------
 * Byte-level encoder for GENISYS framing.
 *
 * <p>This interface defines the outbound wire-mechanics boundary between a
 * validated, structured {@link GenisysFrame} and raw transport bytes.</p>
 *
 * <p><strong>Layering note:</strong> This encoder does NOT decide what message
 * to send and does NOT encode semantic message payload structures. It only
 * applies the mechanical rules of:</p>
 * <ul>
 *   <li>CRC append (if {@link GenisysFrame#crcPresent()} is true)</li>
 *   <li>Escaping</li>
 *   <li>Terminator framing</li>
 * </ul>
 *
 * <p>The semantic outbound step (GenisysMessage → GenisysFrame) is performed by
 * {@code com.questrail.wayside.protocol.genisys.internal.encode.GenisysMessageEncoder}.</p>
 */
public interface GenisysFrameEncoder
{
    /**
     * Encode a validated {@link GenisysFrame} into a wire-ready datagram payload.
     *
     * <p>The returned byte array must be suitable for immediate transmission by
     * a UDP (or other) transport adapter without further modification.</p>
     */
    byte[] encode(GenisysFrame frame);
}

