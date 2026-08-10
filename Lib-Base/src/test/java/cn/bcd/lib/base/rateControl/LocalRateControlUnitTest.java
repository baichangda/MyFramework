package cn.bcd.lib.base.rateControl;

import org.junit.jupiter.api.Test;

import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRateControlUnitTest {

    @Test
    void rejectsInvalidConfiguration() {
        assertThrows(NullPointerException.class, () -> new LocalRateControlUnit(null, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new LocalRateControlUnit("test", 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new LocalRateControlUnit("test", 1, 0));
    }

    @Test
    void rejectsInvalidIncrement() {
        try (LocalRateControlUnit unit = new LocalRateControlUnit("test", 1, 1)) {
            assertThrows(IllegalArgumentException.class, () -> unit.tryAdd(0));
            assertThrows(IllegalArgumentException.class, () -> unit.tryAdd(-1));
            assertThrows(IllegalArgumentException.class, () -> unit.tryAdd(2));
            assertThrows(IllegalArgumentException.class, () -> unit.add(2));
        }
    }

    @Test
    void doesNotOverflowWhenCountApproachesIntegerLimit() {
        try (LocalRateControlUnit unit = new LocalRateControlUnit("test", 1, Integer.MAX_VALUE)) {
            assertTrue(unit.tryAdd(Integer.MAX_VALUE));
            assertFalse(unit.tryAdd(1));
        }
    }

    @Test
    void doesNotExceedLimitUnderHighConcurrency() {
        try (LocalRateControlUnit unit = new LocalRateControlUnit("test", 60, 1_000)) {
            long accepted = IntStream.range(0, 10_000)
                    .parallel()
                    .filter(ignored -> unit.tryAdd(1))
                    .count();

            assertEquals(1_000, accepted);
        }
    }

    @Test
    void closeIsIdempotent() {
        LocalRateControlUnit unit = new LocalRateControlUnit("test", 1, 1);

        assertDoesNotThrow(unit::close);
        assertDoesNotThrow(unit::close);
        assertThrows(RuntimeException.class, () -> unit.tryAdd(1));
    }
}
