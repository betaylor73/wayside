package com.questrail.wayside.protocol.genisys.model;

/**
 * Canonical semantic representation of a GENISYS protocol message.
 *
 * <h2>Purpose</h2>
 * <p>
 * {@code GenisysMessage} represents a fully decoded, semantically meaningful
 * GENISYS protocol message. It is the ONLY form of message that the protocol
 * behavior layer (reducers, state machines, tests) is permitted to reason about.
 * </p>
 *
 * <p>
 * This interface deliberately abstracts away all wire-level concerns, including:
 * </p>
 * <ul>
 *   <li>Header/control byte values (e.g. {@code 0xF1}, {@code 0xFB})</li>
 *   <li>CRC presence or calculation</li>
 *   <li>Escape/unescape mechanics</li>
 *   <li>Byte ordering and framing</li>
 *   <li>Transport details (serial, UDP, Netty, etc.)</li>
 * </ul>
 *
 * <p>
 * Those concerns are handled strictly below the protocol behavior layer and must
 * be resolved <em>before</em> a {@code GenisysMessage} instance is created.
 * </p>
 *
 * <h2>Directionality</h2>
 * <p>
 * GENISYS is a strictly directional protocol:
 * </p>
 * <ul>
 *   <li>Master → Slave messages are requests</li>
 *   <li>Slave → Master messages are responses</li>
 * </ul>
 *
 * <p>
 * This directionality is enforced structurally via subinterfaces:
 * </p>
 * <ul>
 *   <li>{@link GenisysMasterRequest}</li>
 *   <li>{@link GenisysSlaveResponse}</li>
 * </ul>
 *
 * <p>
 * Illegal protocol flows should be made unrepresentable at this level.
 * </p>
 */
public sealed interface GenisysMessage
        permits GenisysMasterRequest, GenisysSlaveResponse {

    /**
     * Returns the GENISYS station address associated with this message.
     *
     * <ul>
     *   <li>For master requests, this is the addressed slave</li>
     *   <li>For slave responses, this is the responding slave</li>
     * </ul>
     *
     * @return the GENISYS station address
     */
    GenisysStationAddress station();
}
