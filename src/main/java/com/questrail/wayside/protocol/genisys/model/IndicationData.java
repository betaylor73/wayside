package com.questrail.wayside.protocol.genisys.model;

import com.questrail.wayside.api.IndicationSet;

/**
 * Indication Data response.
 *
 * <p>
 * Delivers new or changed indication data from the slave to the master.
 * This may represent either:
 * </p>
 * <ul>
 *   <li>A delta update</li>
 *   <li>A full database image (e.g. in response to {@link Recall})</li>
 * </ul>
 *
 * <p>
 * Protocol obligation:
 * </p>
 * <ul>
 *   <li>The master MUST acknowledge receipt of this message</li>
 * </ul>
 *
 * <p>
 * Failure to acknowledge will cause the slave to resend the data
 * when next addressed.
 * </p>
 *
 * GENISYS.pdf §5.1.3.2
 */
public record IndicationData(
        GenisysStationAddress station,
        IndicationSet indications
) implements GenisysSlaveResponse
{
}
