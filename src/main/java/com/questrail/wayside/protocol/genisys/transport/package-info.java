/**
 * GENISYS Transport Ports — Phase 4
 * =============================================================================
 *
 * These interfaces define the <em>framework-agnostic transport boundary</em>
 * between a concrete networking implementation (e.g., Netty UDP, java.nio UDP,
 * a simulator, or a test double) and the GENISYS Phase 4 integration wiring.
 *
 * <h2>Why these ports exist</h2>
 * We intend to fully leverage Netty in production (event loop model, mature UDP
 * support, robust lifecycle handling) <strong>without</strong> allowing Netty types
 * to leak into the GENISYS protocol core.
 *
 * <p>These ports enforce that separation by ensuring that everything above the
 * transport adapter sees only:</p>
 * <ul>
 *   <li>Raw datagram payloads as {@code byte[]}</li>
 *   <li>Remote endpoints as standard {@link SocketAddress}</li>
 *   <li>Transport lifecycle notifications (up/down)</li>
 * </ul>
 *
 * <h2>Architectural constraints (binding)</h2>
 * Implementations of these ports MUST:
 * <ul>
 *   <li>Perform transport I/O only (no protocol interpretation)</li>
 *   <li>Not decode frames or messages</li>
 *   <li>Not emit {@code GenisysEvent} instances directly</li>
 *   <li>Not schedule retries, polls, or timeouts</li>
 * </ul>
 *
 * <p>All protocol behavior continues to live in the reducer/executor/controller.</p>
 */
package com.questrail.wayside.protocol.genisys.transport;
