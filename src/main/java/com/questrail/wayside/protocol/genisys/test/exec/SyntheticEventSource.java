package com.questrail.wayside.protocol.genisys.test.exec;

import com.questrail.wayside.protocol.genisys.internal.events.GenisysEvent;

import java.util.List;

/**
 * SyntheticEventSource
 * --------------------
 *
 * Phase 2 abstraction: a deterministic producer of existing GenisysEvent values.
 *
 * IMPORTANT:
 * - Sources must not examine reducer state.
 * - Sources must not "invent" new event types.
 */
interface SyntheticEventSource
{
    List<GenisysEvent> events();
}