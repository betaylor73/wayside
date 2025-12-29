package com.questrail.wayside.protocol.genisys.internal.decode;

import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;
import com.questrail.wayside.protocol.genisys.model.*;

import java.util.Objects;

/**
 * GenisysMessageDecoder
 * ============================================================================
 * Converts a decoded, wire-adjacent {@link GenisysFrame} into a semantic
 * {@link GenisysMessage}.
 *
 * <h2>Architectural Role</h2>
 * This class forms the <strong>explicit boundary</strong> between:
 *
 * <ul>
 *   <li><b>Protocol mechanics</b> (frames, headers, CRC presence)</li>
 *   <li><b>Protocol semantics</b> (GENISYS messages with defined meaning)</li>
 * </ul>
 *
 * Reducers, state machines, and unit tests MUST operate exclusively on
 * {@link GenisysMessage} and MUST NOT reason directly about:
 *
 * <ul>
 *   <li>Header byte values</li>
 *   <li>Payload byte layout</li>
 *   <li>CRC presence or absence</li>
 * </ul>
 *
 * <h2>What this decoder assumes</h2>
 * {@link GenisysFrame} instances passed to this decoder are assumed to be:
 *
 * <ul>
 *   <li>Fully framed</li>
 *   <li>Unescaped</li>
 *   <li>CRC-validated (if CRC is present)</li>
 * </ul>
 *
 * <h2>What this decoder does NOT do</h2>
 * <ul>
 *   <li>Perform transport I/O</li>
 *   <li>Validate CRCs</li>
 *   <li>Retry or schedule timeouts</li>
 *   <li>Transition protocol state</li>
 * </ul>
 *
 * Those responsibilities live elsewhere by design.
 *
 * <h2>Normative References</h2>
 * <ul>
 *   <li>GENISYS.pdf §5.1.2 — Master requests</li>
 *   <li>GENISYS.pdf §5.1.3 — Slave responses</li>
 *   <li>GENISYS_Message_Model.md</li>
 * </ul>
 */
public final class GenisysMessageDecoder
{
    /**
     * Strategy interface for decoding indication payloads (GENISYS $F2).
     *
     * This indirection allows:
     * <ul>
     *   <li>Semantic message taxonomy to be tested independently</li>
     *   <li>Payload decoding to evolve without touching reducer logic</li>
     * </ul>
     */
    @FunctionalInterface
    public interface IndicationPayloadDecoder {
        IndicationSet decode(byte[] payload);
    }

    /**
     * Strategy interface for decoding control payloads (GENISYS $FC, $F3).
     */
    @FunctionalInterface
    public interface ControlPayloadDecoder {
        ControlSet decode(byte[] payload);
    }

    private final IndicationPayloadDecoder indicationPayloadDecoder;
    private final ControlPayloadDecoder controlPayloadDecoder;

    /**
     * Creates a new {@code GenisysMessageDecoder}.
     *
     * @param indicationPayloadDecoder decoder for indication payloads
     * @param controlPayloadDecoder    decoder for control-related payloads
     */
    public GenisysMessageDecoder(IndicationPayloadDecoder indicationPayloadDecoder,
                                 ControlPayloadDecoder controlPayloadDecoder) {

        this.indicationPayloadDecoder =
                Objects.requireNonNull(indicationPayloadDecoder, "indicationPayloadDecoder");

        this.controlPayloadDecoder =
                Objects.requireNonNull(controlPayloadDecoder, "controlPayloadDecoder");
    }

    /**
     * Decodes a validated {@link GenisysFrame} into a semantic
     * {@link GenisysMessage}.
     *
     * @param frame decoded GENISYS frame
     * @return semantic GENISYS message
     *
     * @throws GenisysDecodeException if the frame cannot be mapped to a valid
     *         semantic message
     */
    public GenisysMessage decode(GenisysFrame frame) {
        Objects.requireNonNull(frame, "frame");

        // Convert primitive station address into semantic form.
        // Broadcast (0) is intentionally rejected here.
        GenisysStationAddress station =
                GenisysStationAddress.of(frame.stationAddress());

        // Treat header as unsigned for clarity and correctness.
        final int header = frame.header() & 0xFF;

        return switch (header) {

            // =============================================================
            // Slave → Master responses
            // =============================================================

            case 0xF1 -> decodeAcknowledge(station, frame);
            case 0xF2 -> decodeIndicationData(station, frame);
            case 0xF3 -> decodeControlCheckback(station, frame);

            // =============================================================
            // Master → Slave requests
            // =============================================================

            case 0xFB -> decodePoll(station, frame);
            case 0xFA -> decodeAcknowledgeAndPoll(station, frame);
            case 0xFD -> decodeRecall(station, frame);
            case 0xFC -> decodeControlData(station, frame);
            case 0xFE -> decodeExecuteControls(station, frame);

            default -> throw new GenisysDecodeException(
                    "Unknown GENISYS header byte: 0x"
                            + Integer.toHexString(header)
            );
        };
    }

    // ========================================================================
    // Slave → Master decoders
    // ========================================================================

    private Acknowledge decodeAcknowledge(
            GenisysStationAddress station,
            GenisysFrame frame) {

        // Per GENISYS spec, ACK carries no payload.
        if (frame.payload().length != 0) {
            throw new GenisysDecodeException(
                    "ACK ($F1) must not carry payload bytes"
            );
        }
        return new Acknowledge(station);
    }

    private IndicationData decodeIndicationData(
            GenisysStationAddress station,
            GenisysFrame frame) {

        try {
            IndicationSet indications =
                    indicationPayloadDecoder.decode(frame.payload());
            return new IndicationData(station, indications);
        } catch (RuntimeException e) {
            throw new GenisysDecodeException(
                    "Failed to decode IndicationData ($F2) payload", e
            );
        }
    }

    private ControlCheckback decodeControlCheckback(
            GenisysStationAddress station,
            GenisysFrame frame) {

        try {
            ControlSet echoed =
                    controlPayloadDecoder.decode(frame.payload());
            return new ControlCheckback(station, echoed);
        } catch (RuntimeException e) {
            throw new GenisysDecodeException(
                    "Failed to decode ControlCheckback ($F3) payload", e
            );
        }
    }

    // ========================================================================
    // Master → Slave decoders
    // ========================================================================

    private Poll decodePoll(
            GenisysStationAddress station,
            GenisysFrame frame) {

        if (frame.payload().length != 0) {
            throw new GenisysDecodeException(
                    "Poll ($FB) must not carry payload bytes"
            );
        }

        // CRC presence differentiates secure vs non-secure polling.
        return new Poll(station, frame.crcPresent());
    }

    private AcknowledgeAndPoll decodeAcknowledgeAndPoll(
            GenisysStationAddress station,
            GenisysFrame frame) {

        if (frame.payload().length != 0) {
            throw new GenisysDecodeException(
                    "AcknowledgeAndPoll ($FA) must not carry payload bytes"
            );
        }
        return new AcknowledgeAndPoll(station);
    }

    private Recall decodeRecall(
            GenisysStationAddress station,
            GenisysFrame frame) {

        if (frame.payload().length != 0) {
            throw new GenisysDecodeException(
                    "Recall ($FD) must not carry payload bytes"
            );
        }
        return new Recall(station);
    }

    private ControlData decodeControlData(
            GenisysStationAddress station,
            GenisysFrame frame) {

        try {
            ControlSet controls =
                    controlPayloadDecoder.decode(frame.payload());
            return new ControlData(station, controls);
        } catch (RuntimeException e) {
            throw new GenisysDecodeException(
                    "Failed to decode ControlData ($FC) payload", e
            );
        }
    }

    private ExecuteControls decodeExecuteControls(
            GenisysStationAddress station,
            GenisysFrame frame) {

        if (frame.payload().length != 0) {
            throw new GenisysDecodeException(
                    "ExecuteControls ($FE) must not carry payload bytes"
            );
        }
        return new ExecuteControls(station);
    }
}
