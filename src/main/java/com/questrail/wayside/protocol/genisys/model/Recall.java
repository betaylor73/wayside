package com.questrail.wayside.protocol.genisys.model;

/**
 * Indication Recall request.
 *
 * <p>
 * Requests the slave to transmit its entire indication database.
 * </p>
 *
 * <p>
 * This message is used during:
 * </p>
 * <ul>
 *   <li>Initial protocol startup</li>
 *   <li>Recovery after communication loss</li>
 *   <li>Re-synchronization following errors or reconfiguration</li>
 * </ul>
 *
 * <p>
 * Valid slave response:
 * </p>
 * <ul>
 *   <li>{@link IndicationData} (full image)</li>
 * </ul>
 *
 * GENISYS.pdf §5.1.2.5
 */
public record Recall(
        GenisysStationAddress station
) implements GenisysMasterRequest
{
}
