package com.questrail.wayside.protocol.genisys.internal.decode;

import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.protocol.genisys.internal.frame.GenisysFrame;
import com.questrail.wayside.protocol.genisys.model.*;

import java.util.Objects;

/**
 * GenisysMessageDecoder
 * ============================================================================
 * Inbound semantic decoder for GENISYS protocol messages.
 *
 * <h2>Architectural Role</h2>
 * This class represents the <b>semantic decoding boundary</b> between:
 *
 * <pre>
 *   GenisysFrame  →  GenisysMessage
 * </pre>
 *
 * Responsibilities:
 * <ul>
 *   <li>Interpret GENISYS header bytes</li>
 *   <li>Enforce protocol-mandated CRC presence rules</li>
 *   <li>Decode message payloads via injected payload decoders</li>
 *   <li>Construct semantic {@link GenisysMessage} instances</li>
 * </ul>
 *
 * Non-responsibilities:
 * <ul>
 *   <li>CRC correctness validation (handled by {@code GenisysFrameDecoder})</li>
 *   <li>Framing / escaping / terminator handling</li>
 *   <li>Transport or scheduling concerns</li>
 * </ul>
 *
 * <h2>Protocol Anchoring</h2>
 * CRC presence rules enforced here are mandated by GENISYS.pdf §5.1.x:
 * <ul>
 *   <li>$F1 ACK: CRC forbidden</li>
 *   <li>$FB Poll: CRC indicates secure poll</li>
 *   <li>All other messages: CRC required</li>
 * </ul>
 */
public final class GenisysMessageDecoder
{
    @FunctionalInterface
    public interface IndicationPayloadDecoder {
        IndicationSet decode(byte[] payload);
    }

    @FunctionalInterface
    public interface ControlPayloadDecoder {
        ControlSet decode(byte[] payload);
    }

    private final IndicationPayloadDecoder indicationPayloadDecoder;
    private final ControlPayloadDecoder controlPayloadDecoder;

    public GenisysMessageDecoder(IndicationPayloadDecoder indicationPayloadDecoder,
                                 ControlPayloadDecoder controlPayloadDecoder)
    {
        this.indicationPayloadDecoder =
                Objects.requireNonNull(indicationPayloadDecoder, "indicationPayloadDecoder");
        this.controlPayloadDecoder =
                Objects.requireNonNull(controlPayloadDecoder, "controlPayloadDecoder");
    }

    /**
     * Decode a validated {@link GenisysFrame} into a semantic {@link GenisysMessage}.
     *
     * @throws GenisysDecodeException if the frame violates GENISYS protocol rules
     */
    public GenisysMessage decode(GenisysFrame frame)
    {
        Objects.requireNonNull(frame, "frame");

        final GenisysStationAddress station =
                GenisysStationAddress.of(frame.stationAddress());

        return switch (frame.header()) {
            case (byte) 0xF1 -> decodeAcknowledge(station, frame);
            case (byte) 0xFB -> decodePoll(station, frame);
            case (byte) 0xF2 -> decodeIndicationData(station, frame);
            case (byte) 0xF3 -> decodeControlCheckback(station, frame);
            case (byte) 0xFC -> decodeControlData(station, frame);
            case (byte) 0xFD -> decodeRecall(station, frame);
            case (byte) 0xFE -> decodeExecuteControls(station, frame);
            case (byte) 0xFA -> decodeAcknowledgeAndPoll(station, frame);
            default -> throw new GenisysDecodeException(
                    "Unknown GENISYS header: 0x" +
                            Integer.toHexString(frame.header() & 0xFF)
            );
        };
    }

    // ---------------------------------------------------------------------
    // Message-specific decoders
    // ---------------------------------------------------------------------

    private Acknowledge decodeAcknowledge(
            GenisysStationAddress station,
            GenisysFrame frame)
    {
        // Protocol-mandated: ACK must never include CRC.
        if (frame.crcPresent()) {
            throw new GenisysDecodeException(
                    "ACK ($F1) must not include CRC"
            );
        }

        if (frame.payload().length != 0) {
            throw new GenisysDecodeException(
                    "ACK ($F1) must not carry payload bytes"
            );
        }

        return new Acknowledge(station);
    }

    private Poll decodePoll(
            GenisysStationAddress station,
            GenisysFrame frame)
    {
        // CRC presence indicates secure poll
        if (frame.payload().length != 0) {
            throw new GenisysDecodeException(
                    "Poll ($FB) must not carry payload bytes"
            );
        }
        return new Poll(station, frame.crcPresent());
    }

    private IndicationData decodeIndicationData(
            GenisysStationAddress station,
            GenisysFrame frame)
    {
        requireCrc(frame, "$F2 IndicationData");
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
            GenisysFrame frame)
    {
        requireCrc(frame, "$F3 ControlCheckback");
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

    private ControlData decodeControlData(
            GenisysStationAddress station,
            GenisysFrame frame)
    {
        requireCrc(frame, "$FC ControlData");
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

    private Recall decodeRecall(
            GenisysStationAddress station,
            GenisysFrame frame)
    {
        requireCrc(frame, "$FD Recall");
        if (frame.payload().length != 0) {
            throw new GenisysDecodeException(
                    "Recall ($FD) must not carry payload bytes"
            );
        }
        return new Recall(station);
    }

    private ExecuteControls decodeExecuteControls(
            GenisysStationAddress station,
            GenisysFrame frame)
    {
        requireCrc(frame, "$FE ExecuteControls");
        if (frame.payload().length != 0) {
            throw new GenisysDecodeException(
                    "ExecuteControls ($FE) must not carry payload bytes"
            );
        }
        return new ExecuteControls(station);
    }

    private AcknowledgeAndPoll decodeAcknowledgeAndPoll(
            GenisysStationAddress station,
            GenisysFrame frame)
    {
        requireCrc(frame, "$FA AcknowledgeAndPoll");
        if (frame.payload().length != 0) {
            throw new GenisysDecodeException(
                    "AcknowledgeAndPoll ($FA) must not carry payload bytes"
            );
        }
        return new AcknowledgeAndPoll(station);
    }

    // ---------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------

    private static void requireCrc(GenisysFrame frame, String messageName)
    {
        if (!frame.crcPresent()) {
            throw new GenisysDecodeException(
                    messageName + " requires CRC, but frame has none"
            );
        }
    }
}
