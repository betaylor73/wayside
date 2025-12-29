package com.questrail.wayside.protocol.genisys.model;

import com.questrail.wayside.api.ControlSet;

/**
 * Control Data request.
 *
 * <p>
 * Delivers new or changed control data from the master to the slave.
 * </p>
 *
 * <p>
 * Depending on configuration, this message may participate in a
 * checkback / execute control sequence.
 * </p>
 *
 * <p>
 * Valid slave responses:
 * </p>
 * <ul>
 *   <li>{@link Acknowledge}</li>
 *   <li>{@link IndicationData}</li>
 *   <li>{@link ControlCheckback}</li>
 * </ul>
 *
 * GENISYS.pdf §5.1.2.4
 */
public record ControlData(
        GenisysStationAddress station,
        ControlSet controls
) implements GenisysMasterRequest
{
}
