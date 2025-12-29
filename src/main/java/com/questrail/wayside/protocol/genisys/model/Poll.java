package com.questrail.wayside.protocol.genisys.model;

/**
 * Poll request.
 *
 * <p>
 * Requests the slave to return any new or changed indication data.
 * </p>
 *
 * <p>
 * The poll may be sent in either:
 * </p>
 * <ul>
 *   <li>Secure form (CRC present)</li>
 *   <li>Non-secure form (no CRC)</li>
 * </ul>
 *
 * <p>
 * Selection of secure vs non-secure polling is a configuration decision and
 * does not affect the semantic meaning of the poll itself.
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
 * GENISYS.pdf §5.1.2.3
 */
public record Poll(
        GenisysStationAddress station,
        boolean secure
) implements GenisysMasterRequest
{
}
