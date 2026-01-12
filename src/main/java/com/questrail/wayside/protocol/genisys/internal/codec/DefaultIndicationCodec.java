package com.questrail.wayside.protocol.genisys.internal.codec;

import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.api.IndicationSet;
import com.questrail.wayside.api.SignalState;
import com.questrail.wayside.core.IndicationBitSetSignalSet;
import com.questrail.wayside.mapping.SignalIndex;
import com.questrail.wayside.protocol.genisys.internal.decode.GenisysMessageDecoder;
import com.questrail.wayside.protocol.genisys.internal.encode.GenisysMessageEncoder;

import java.util.Objects;

/**
 * Default implementation of GENISYS indication payload encoding/decoding.
 * Maps indications to bits based on a SignalIndex.
 */
public final class DefaultIndicationCodec 
    implements GenisysMessageDecoder.IndicationPayloadDecoder, 
               GenisysMessageEncoder.IndicationPayloadEncoder {

    private final SignalIndex<IndicationId> index;

    public DefaultIndicationCodec(SignalIndex<IndicationId> index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    @Override
    public IndicationSet decode(byte[] payload) {
        IndicationBitSetSignalSet set = new IndicationBitSetSignalSet(index);
        if (payload == null) return set;

        for (int i = 0; i < Math.min(index.size(), payload.length * 8); i++) {
            int byteIdx = i / 8;
            int bitIdx = i % 8;
            boolean value = (payload[byteIdx] & (1 << bitIdx)) != 0;
            set.set(index.idAt(i), value ? SignalState.TRUE : SignalState.FALSE);
        }
        return set;
    }

    @Override
    public byte[] encode(IndicationSet indications) {
        int byteCount = (index.size() + 7) / 8;
        byte[] payload = new byte[byteCount];

        for (int i = 0; i < index.size(); i++) {
            IndicationId id = index.idAt(i);
            if (indications.get(id) == SignalState.TRUE) {
                int byteIdx = i / 8;
                int bitIdx = i % 8;
                payload[byteIdx] |= (byte) (1 << bitIdx);
            }
        }
        return payload;
    }
}
