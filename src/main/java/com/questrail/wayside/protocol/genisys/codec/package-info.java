/**
 * GENISYS Codec — Phase 4 (Wire-Level) Implementation
 * =============================================================================
 *
 * <p>This file defines the <strong>codec layer</strong> for the GENISYS protocol.
 * The codec layer is responsible for implementing the <em>wire-level rules</em>
 * defined normatively in <strong>GENISYS.pdf</strong>, including:</p>
 *
 * <ul>
 *   <li>Frame delimiting / detection</li>
 *   <li>Byte escaping and unescaping</li>
 *   <li>CRC presence detection and CRC validation</li>
 *   <li>Extraction of frame fields into a {@link GenisysFrame}</li>
 * </ul>
 *
 * <h2>Normative Authority</h2>
 * <p><strong>GENISYS.pdf</strong> is the authoritative definition of all wire
 * encoding rules (framing, escaping, CRC algorithm, CRC placement, etc.).
 * This codec layer exists solely to implement those rules faithfully.</p>
 *
 * <h2>Architectural Placement</h2>
 * <p>The codec layer sits <strong>below</strong> protocol semantics and
 * <strong>above</strong> transport I/O:</p>
 *
 * <pre>
 *   byte[] datagram
 *        → GenisysFrameDecoder   (wire rules applied here)
 *            → GenisysFrame      (post-framing, post-unescape, CRC-validated)
 *                → GenisysMessageDecoder
 *                    → semantic events
 * </pre>
 *
 * <h2>Important Boundaries</h2>
 * <ul>
 *   <li>{@link GenisysFrame} is <strong>not</strong> a wire parser or encoder.</li>
 *   <li>{@link GenisysFrame} represents a frame <em>after</em> framing,
 *       escaping removal, and CRC validation have already occurred.</li>
 *   <li>All byte-level mechanics live exclusively in this codec layer.</li>
 * </ul>
 *
 * <h2>Configuration-Dependent Behavior</h2>
 * <p>Some GENISYS messages conditionally include a CRC (e.g. secure poll enabled).
 * That decision is <strong>protocol-configuration dependent</strong> and is
 * intentionally <em>not</em> modeled yet. As a result:</p>
 *
 * <ul>
 *   <li>The decoder must accept both CRC-present and CRC-absent frames.</li>
 *   <li>The encoder implementation is intentionally deferred until protocol
 *       configuration is introduced.</li>
 * </ul>
 *
 * <p>This reflects the current project phase and avoids inventing configuration
 * policy prematurely.</p>
 */
package com.questrail.wayside.protocol.genisys.codec;
