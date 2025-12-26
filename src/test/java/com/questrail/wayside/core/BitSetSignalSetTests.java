package com.questrail.wayside.core;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.SignalSet;
import com.questrail.wayside.api.SignalState;
import com.questrail.wayside.mapping.ArraySignalIndex;
import com.questrail.wayside.mapping.SignalIndex;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BitSetSignalSetTests {

    enum TestControl implements ControlId {
        A(16), B(42), C(7);

        private final int number;

        TestControl(int number) {
            this.number = number;
        }

        @Override
        public int number() {
            return number;
        }

        @Override
        public Optional<String> label() {
            return Optional.empty();
        }
    }

    private static SignalIndex<TestControl> index() {
        return new ArraySignalIndex<>(TestControl.A, TestControl.B, TestControl.C);
    }

    @Test
    void defaultIsAllDontCare() {
        BitSetSignalSet<TestControl> set = new BitSetSignalSet<>(index());
        assertTrue(set.isEmpty());
        assertTrue(set.relevantSignals().isEmpty());
        assertEquals(SignalState.DONT_CARE, set.get(TestControl.A));
    }

    @Test
    void setAndGetWork() {
        BitSetSignalSet<TestControl> set = new BitSetSignalSet<>(index());
        set.set(TestControl.A, SignalState.TRUE);
        set.set(TestControl.B, SignalState.FALSE);

        assertEquals(SignalState.TRUE, set.get(TestControl.A));
        assertEquals(SignalState.FALSE, set.get(TestControl.B));
        assertEquals(SignalState.DONT_CARE, set.get(TestControl.C));
    }

    @Test
    void mergeRespectsDontCare() {
        BitSetSignalSet<TestControl> base = new BitSetSignalSet<>(index());
        base.set(TestControl.A, SignalState.TRUE);

        BitSetSignalSet<TestControl> update = new BitSetSignalSet<>(index());
        update.set(TestControl.B, SignalState.FALSE);

        SignalSet<TestControl> merged = base.merge(update);

        assertEquals(SignalState.TRUE, merged.get(TestControl.A));
        assertEquals(SignalState.FALSE, merged.get(TestControl.B));
        assertEquals(SignalState.DONT_CARE, merged.get(TestControl.C));
    }

    @Test
    void assertMaterializedFailsWhenPartial() {
        BitSetSignalSet<TestControl> set = new BitSetSignalSet<>(index());
        set.set(TestControl.A, SignalState.TRUE);

        assertThrows(IllegalStateException.class, set::assertMaterialized);
    }
}
