package com.questrail.wayside.mapping;

import com.questrail.wayside.api.ControlId;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class SignalIndexTests
{
    enum TestControl implements ControlId {
        A(16, "1LK_PK"),
        B(42, null),
        C(7, "SW7_N");

        private final int number;
        private final String label;

        TestControl(int number, String label) {
            this.number = number;
            this.label = label;
        }

        @Override
        public int number() {
            return number;
        }

        @Override
        public Optional<String> label() {
            return Optional.ofNullable(label);
        }
    }

    @Test
    void arraySignalIndexProvidesBidirectionalLookup() {
        ArraySignalIndex<TestControl> index = new ArraySignalIndex<>(
                TestControl.A,
                TestControl.B,
                TestControl.C
        );

        assertEquals(0, index.indexOf(TestControl.A));
        assertEquals(1, index.indexOf(TestControl.B));
        assertEquals(2, index.indexOf(TestControl.C));

        assertEquals(TestControl.A, index.idAt(0));
        assertEquals(TestControl.B, index.idAt(1));
        assertEquals(TestControl.C, index.idAt(2));

        assertEquals(3, index.size());
        assertTrue(index.allSignals().contains(TestControl.B));
    }

    @Test
    void resolveByNumberAndLabelAreBestEffortConveniences() {
        ArraySignalIndex<TestControl> index = new ArraySignalIndex<>(
                TestControl.A,
                TestControl.B,
                TestControl.C
        );

        assertEquals(TestControl.B, index.tryResolveByNumber(42).orElseThrow());
        assertTrue(index.tryResolveByNumber(999).isEmpty());

        assertEquals(TestControl.A, index.tryResolveByLabel("1LK_PK").orElseThrow());
        assertTrue(index.tryResolveByLabel("NOPE").isEmpty());

        // Label is optional; resolving by label should skip unlabeled IDs.
        assertTrue(index.tryResolveByLabel("42").isEmpty());
    }

    @Test
    void unknownIdThrows() {
        ArraySignalIndex<TestControl> index = new ArraySignalIndex<>(
                TestControl.A,
                TestControl.B,
                TestControl.C
        );

        ControlId fake = new ControlId() {
            @Override public int number() { return 123; }
            @Override public Optional<String> label() { return Optional.of("FAKE"); }
        };

        assertFalse(index.allSignals().contains(fake));

        @SuppressWarnings("unchecked")
        SignalIndex<ControlId> view = (SignalIndex<ControlId>) (SignalIndex) index;
        assertThrows(IllegalArgumentException.class, () -> view.indexOf(fake));
    }
}
