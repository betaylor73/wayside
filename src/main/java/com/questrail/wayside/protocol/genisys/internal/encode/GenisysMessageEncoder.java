package com.questrail.wayside.protocol.genisys.internal.encode;

import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;
import com.questrail.wayside.protocol.genisys.model.*;

import java.util.Objects;

/**
 * GenisysMessageEncoder
 * ============================================================================
 * Converts a semantic {@link GenisysMessage} into a wire-adjacent
 * {@link GenisysFrame}.
 *
 * <h2>Architectural Role</h2>
 * This class forms the <strong>explicit outbound boundary</strong> between:
 *
 * <ul>
 *   <li><b>Protocol semantics</b> (GENISYS messages with defined meaning)</li>
 *   <li><b>Protocol mechanics</b> (header bytes, payload bytes, CRC presence)</li>
 * </ul>
 *
 * The outbound pipeline is therefore:
 *
 * <pre>
 *   GenisysMessage  ->  GenisysFrame  ->  byte[]
 *         (this)         (frame encoder)
 * </pre>
 *
 * <h2>What this encoder does</h2>
 * <ul>
 *   <li>Selects the correct GENISYS header byte for the message subtype</li>
 *   <li>Encodes payload bytes for messages that carry payloads</li>
 *   <li>Determines CRC presence per protocol rules</li>
 * </ul>
 *
 * <h2>What this encoder does NOT do</h2>
 * <ul>
 *   <li>Compute or append the CRC bytes (handled by GenisysFrameEncoder)</li>
 *   <li>Apply escaping or terminators (handled by GenisysFrameEncoder)</li>
 *   <li>Perform transport I/O or scheduling</li>
 * </ul>
 *
 * <h2>Normative References</h2>
 * <ul>
 *   <li>GENISYS.pdf §5.1.2 — Master requests</li>
 *   <li>GENISYS.pdf §5.1.3 — Slave responses</li>
 *   <li>GenisysPackageArchitecture.md (internal.encode responsibility)</li>
 * </ul>
 */
public final class GenisysMessageEncoder
{
    /**
     * Strategy interface for encoding indication payloads (GENISYS $F2).
     */
    @FunctionalInterface
    public interface IndicationPayloadEncoder {
        byte[] encode(IndicationSet indications);
    }

    /**
     * Strategy interface for encoding control payloads (GENISYS $FC, $F3).
     */
    @FunctionalInterface
    public interface ControlPayloadEncoder {
        byte[] encode(ControlSet controls);
    }

    private final IndicationPayloadEncoder indicationPayloadEncoder;
    private final ControlPayloadEncoder controlPayloadEncoder;

    public GenisysMessageEncoder(IndicationPayloadEncoder indicationPayloadEncoder,
                                 ControlPayloadEncoder controlPayloadEncoder)
    {
        this.indicationPayloadEncoder =
                Objects.requireNonNull(indicationPayloadEncoder, "indicationPayloadEncoder");
        this.controlPayloadEncoder =
                Objects.requireNonNull(controlPayloadEncoder, "controlPayloadEncoder");
    }

    /**
     * Encode a semantic {@link GenisysMessage} into a wire-adjacent {@link GenisysFrame}.
     */
    public GenisysFrame encode(GenisysMessage message)
    {
        Objects.requireNonNull(message, "message");

        // Station as primitive for frame layer.
        final int station = message.station().value();

        // Header selection + payload construction.
        final byte header;
        final byte[] payload;
        final boolean crcPresent;

        if (message instanceof Acknowledge ack) {
            header = (byte) 0xF1;
            payload = new byte[0];
            // Protocol-mandated: ACK never includes CRC.
            crcPresent = false;
        }
        else if (message instanceof IndicationData m) {
            header = (byte) 0xF2;
            payload = safe(indicationPayloadEncoder.encode(m.indications()));
            // Protocol-mandated: CRC required.
            crcPresent = true;
        }
        else if (message instanceof ControlCheckback m) {
            header = (byte) 0xF3;
            payload = safe(controlPayloadEncoder.encode(m.echoedControls()));
            crcPresent = true;
        }
        else if (message instanceof Poll m) {
            header = (byte) 0xFB;
            payload = new byte[0];
            // Protocol-mandated: Poll CRC present iff secure poll enabled.
            crcPresent = m.secure();
        }
        else if (message instanceof AcknowledgeAndPoll m) {
            header = (byte) 0xFA;
            payload = new byte[0];
            crcPresent = true;
        }
        else if (message instanceof Recall m) {
            header = (byte) 0xFD;
            payload = new byte[0];
            crcPresent = true;
        }
        else if (message instanceof ControlData m) {
            header = (byte) 0xFC;
            payload = safe(controlPayloadEncoder.encode(m.controls()));
            crcPresent = true;
        }
        else if (message instanceof ExecuteControls m) {
            header = (byte) 0xFE;
            payload = new byte[0];
            crcPresent = true;
        }
        else {
            // Sealed interface should make this unreachable, but keep defensive.
            throw new IllegalArgumentException("Unsupported GENISYS message type: " + message.getClass());
        }

        return new GenisysFrame(header, station, payload, crcPresent);
    }

    private static byte[] safe(byte[] bytes)
    {
        return (bytes == null) ? new byte[0] : bytes;
    }
}
