/**
 * GENISYS Codec — Phase 4 (Wire-Level Implementation)
 * =============================================================================
 *
 * <p>This class file contains the concrete GENISYS codec implementation that
 * bridges raw transport datagrams and protocol-internal representations.
 *
 * <h2>Normative Authority</h2>
 * <p>All wire-level behavior implemented here is derived directly from
 * <strong>GENISYS.pdf</strong>, in particular:</p>
 * <ul>
 *   <li>Section 5.1 — General Message Format</li>
 *   <li>Section 5.1.1 — Control Characters</li>
 *   <li>Sections 5.1.2 and 5.1.3 — Message Format Definitions</li>
 * </ul>
 *
 * <h2>Architectural Placement</h2>
 * <pre>
 *   byte[] datagram
 *        → GenisysFraming.extractMessageBytes
 *        → GenisysEscaping.unescape
 *        → GenisysCrc.validate / stripCrc
 *        → GenisysFrame
 *        → GenisysMessageDecoder
 *        → semantic events
 * </pre>
 *
 * <p>This codec layer is strictly:</p>
 * <ul>
 *   <li>protocol-faithful</li>
 *   <li>transport-agnostic</li>
 *   <li>semantics-free</li>
 * </ul>
 *
 * <p>Any failure at this layer results in the datagram being dropped.</p>
 */
package com.questrail.wayside.protocol.genisys.codec.impl;