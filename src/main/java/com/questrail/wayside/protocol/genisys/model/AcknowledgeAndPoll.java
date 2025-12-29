package com.questrail.wayside.protocol.genisys.model;

/**
 * Acknowledge-and-Poll request.
 *
 * <p>
 * Semantically equivalent to:
 * </p>
 * <ol>
 *   <li>Acknowledging previously received indication data</li>
 *   <li>Immediately polling the slave for additional data</li>
 * </ol>
 *
 * <p>
 * This message MUST be used whenever the master has an outstanding
 * acknowledgment obligation.
 * </p>
 *
 * <p>
 * Valid slave responses:
 * </p>
 * <ul>
 *   <li>{@link Acknowledge}</li>
 *   <li>{@link IndicationData}</li>
 * </ul>
 *
 * GENISYS.pdf §5.1.2.2
 */
public record AcknowledgeAndPoll(
        GenisysStationAddress station
) implements GenisysMasterRequest
{
}
