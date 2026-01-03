package com.questrail.wayside.protocol.genisys.codec;

import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;

import java.util.Optional;

/**
 * GenisysFrameDecoder
 * -----------------------------------------------------------------------------
 * Byte-level decoder for GENISYS framing.
 *
 * <p>This interface defines the inbound Phase 4 boundary between raw transport
 * bytes (e.g., a UDP datagram payload) and a structured {@link GenisysFrame}.</p>
 *
 * <p>The decoder is responsible only for:</p>
 * <ul>
 *   <li>Validating frame-level structure</li>
 *   <li>Detecting truncation or corruption</li>
 *   <li>Constructing a {@link GenisysFrame} on success</li>
 * </ul>
 *
 * <p>The decoder is <strong>not</strong> responsible for:</p>
 * <ul>
 *   <li>Interpreting protocol semantics</li>
 *   <li>Mapping frames to messages</li>
 *   <li>Producing {@code GenisysEvent} instances</li>
 *   <li>Retrying or buffering partial data</li>
 * </ul>
 *
 * <p>All failures at this layer are classified as <em>transport or framing
 * defects</em> and must not influence protocol behavior.</p>
 */
public interface GenisysFrameDecoder
{
    /**
     * Attempt to decode a single GENISYS frame from a complete datagram payload.
     *
     * <p>This method is invoked with exactly one transport datagram. The decoder
     * must treat the input as a complete unit; streaming or accumulation across
     * calls is explicitly disallowed.</p>
     *
     * @param datagram raw bytes received from the transport
     * @return a decoded {@link GenisysFrame} if the datagram is well-formed;
     *         {@link Optional#empty()} if the datagram is invalid or corrupt
     */
    Optional<GenisysFrame> decode(byte[] datagram);
}
