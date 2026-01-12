package com.questrail.wayside.protocol.genisys.internal.codec;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.ControlSet;
import com.questrail.wayside.api.SignalState;
import com.questrail.wayside.core.ControlBitSetSignalSet;
import com.questrail.wayside.mapping.SignalIndex;
import com.questrail.wayside.protocol.genisys.internal.decode.GenisysMessageDecoder;
import com.questrail.wayside.protocol.genisys.internal.encode.GenisysMessageEncoder;

import java.util.Objects;

/**
 * Default implementation of GENISYS control payload encoding/decoding.
 * Maps controls to bits based on a SignalIndex.
 */
public final class DefaultControlCodec 
    implements GenisysMessageDecoder.ControlPayloadDecoder, 
               GenisysMessageEncoder.ControlPayloadEncoder {

    private final SignalIndex<ControlId> index;

    public DefaultControlCodec(SignalIndex<ControlId> index) {
        this.index = Objects.requireNonNull(index, "index");
    }

    @Override
    public ControlSet decode(byte[] payload) {
        ControlBitSetSignalSet set = new ControlBitSetSignalSet(index);
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
    public byte[] encode(ControlSet controls) {
        int byteCount = (index.size() + 7) / 8;
        byte[] payload = new byte[byteCount];

        for (int i = 0; i < index.size(); i++) {
            ControlId id = index.idAt(i);
            if (controls.get(id) == SignalState.TRUE) {
                int byteIdx = i / 8;
                int bitIdx = i % 8;
                payload[byteIdx] |= (byte) (1 << bitIdx);
            }
        }
        return payload;
    }
}
