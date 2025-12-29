package com.questrail.wayside.protocol.genisys.model;

/**
 * Acknowledge response.
 *
 * <p>
 * Indicates that the slave has no data to return to the master.
 * </p>
 *
 * <p>
 * This message is non-secure and carries no CRC.
 * Loss or misinterpretation of an acknowledge does not compromise
 * data security.
 * </p>
 *
 * GENISYS.pdf §5.1.3.1
 */
public record Acknowledge(
        GenisysStationAddress station
) implements GenisysSlaveResponse
{
}
