package com.questrail.wayside.protocol.genisys.model;

import com.questrail.wayside.api.ControlSet;

/**
 * Control Checkback response.
 *
 * <p>
 * Echoes control data previously received by the slave.
 * </p>
 *
 * <p>
 * This message is used to verify control delivery before execution.
 * Control data MUST NOT be executed unless:
 * </p>
 * <ul>
 *   <li>The echoed control data matches what was sent</li>
 *   <li>An {@link ExecuteControls} request follows</li>
 * </ul>
 *
 * GENISYS.pdf §5.1.3.3
 */
public record ControlCheckback(
        GenisysStationAddress station,
        ControlSet echoedControls
) implements GenisysSlaveResponse
{
}
