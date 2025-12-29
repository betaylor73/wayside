package com.questrail.wayside.protocol.genisys.model;

/**
 * Marker interface for all GENISYS master → slave protocol messages.
 *
 * <p>
 * These messages initiate protocol actions and are always sent by the master
 * to a specific slave station.
 * </p>
 *
 * <p>
 * GENISYS.pdf §5.1.2 is normative for the semantics of these messages.
 * </p>
 */
public sealed interface GenisysMasterRequest extends GenisysMessage
        permits
        Poll,
        AcknowledgeAndPoll,
        Recall,
        ControlData,
        ExecuteControls
{
}
