package com.questrail.wayside.protocol.genisys.model;

/**
 * Execute Controls request.
 *
 * <p>
 * Causes execution of previously delivered control data.
 * </p>
 *
 * <p>
 * This message is valid ONLY if:
 * </p>
 * <ul>
 *   <li>Checkback mode is enabled</li>
 *   <li>A matching {@link ControlCheckback} was received immediately prior</li>
 * </ul>
 *
 * <p>
 * If these conditions are not met, the request is invalid and MUST NOT
 * result in control execution.
 * </p>
 *
 * GENISYS.pdf §5.1.2.6
 */
public record ExecuteControls(
        GenisysStationAddress station
) implements GenisysMasterRequest
{
}
