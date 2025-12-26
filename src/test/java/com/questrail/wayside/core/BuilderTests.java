package com.questrail.wayside.core;

import com.questrail.wayside.api.ControlId;
import com.questrail.wayside.api.IndicationId;
import com.questrail.wayside.api.SignalState;
import com.questrail.wayside.mapping.ArraySignalIndex;
import com.questrail.wayside.mapping.SignalIndex;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class BuilderTests
{
    enum TestControl implements ControlId {
        A(1), B(2);
        private final int number;
        TestControl(int number) { this.number = number; }
        @Override public int number() { return number; }
        @Override public Optional<String> label() { return Optional.empty(); }
    }

    enum TestIndication implements IndicationId {
        X(10), Y(11);
        private final int number;
        TestIndication(int number) { this.number = number; }
        @Override public int number() { return number; }
        @Override public Optional<String> label() { return Optional.empty(); }
    }

    private SignalIndex<ControlId> controlIndex() {
        return new ArraySignalIndex<>(TestControl.A, TestControl.B);
    }

    private SignalIndex<IndicationId> indicationIndex() {
        return new ArraySignalIndex<>(TestIndication.X, TestIndication.Y);
    }

    @Test
    void controlBuilderSetsStatesCorrectly() {
        ControlSetBuilder builder = new ControlSetBuilder(controlIndex());

        var set = builder
                .set(TestControl.A)
                .clear(TestControl.B)
                .build();

        assertEquals(SignalState.TRUE, set.get(TestControl.A));
        assertEquals(SignalState.FALSE, set.get(TestControl.B));
    }

    @Test
    void indicationBuilderDefaultsToDontCare() {
        IndicationSetBuilder builder = new IndicationSetBuilder(indicationIndex());
        var set = builder.build();

        assertEquals(SignalState.DONT_CARE, set.get(TestIndication.X));
        assertTrue(set.isEmpty());
    }
}
