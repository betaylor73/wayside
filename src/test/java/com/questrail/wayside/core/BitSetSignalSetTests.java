package com.questrail.wayside.core;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.SignalSet;
import com.questrail.wayside.api.SignalState;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BitSetSignalSetTests
{
    enum TestControl implements ControlId {
        A(0), B(1), C(2);

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

    private static TestControl idForIndex(int i) {
        return TestControl.values()[i];
    }

    @Test
    void defaultIsAllDontCare() {
        BitSetSignalSet<TestControl> set = new BitSetSignalSet<>(3, BitSetSignalSetTests::idForIndex);
        assertTrue(set.isEmpty());
        assertTrue(set.relevantSignals().isEmpty());
        assertEquals(SignalState.DONT_CARE, set.get(TestControl.A));
    }

    @Test
    void setAndGetWork() {
        BitSetSignalSet<TestControl> set = new BitSetSignalSet<>(3, BitSetSignalSetTests::idForIndex);
        set.set(0, SignalState.TRUE);
        set.set(1, SignalState.FALSE);

        assertEquals(SignalState.TRUE, set.get(TestControl.A));
        assertEquals(SignalState.FALSE, set.get(TestControl.B));
        assertEquals(SignalState.DONT_CARE, set.get(TestControl.C));
    }

    @Test
    void mergeRespectsDontCare() {
        BitSetSignalSet<TestControl> base = new BitSetSignalSet<>(3, BitSetSignalSetTests::idForIndex);
        base.set(0, SignalState.TRUE);

        BitSetSignalSet<TestControl> update = new BitSetSignalSet<>(3, BitSetSignalSetTests::idForIndex);
        update.set(1, SignalState.FALSE);

        SignalSet<TestControl> merged = base.merge(update);

        assertEquals(SignalState.TRUE, merged.get(TestControl.A));
        assertEquals(SignalState.FALSE, merged.get(TestControl.B));
        assertEquals(SignalState.DONT_CARE, merged.get(TestControl.C));
    }

    @Test
    void assertMaterializedFailsWhenPartial() {
        BitSetSignalSet<TestControl> set = new BitSetSignalSet<>(3, BitSetSignalSetTests::idForIndex);
        set.set(0, SignalState.TRUE);

        assertThrows(IllegalStateException.class, set::assertMaterialized);
    }
}
