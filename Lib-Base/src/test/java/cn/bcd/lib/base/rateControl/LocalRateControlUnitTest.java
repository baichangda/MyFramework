package cn.bcd.lib.base.rateControl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRateControlUnitTest {

    @Test
    void rejectsNonPositiveAccessCount() {
        try (LocalRateControlUnit unit = new LocalRateControlUnit("test", 1, 1, 1)) {
            assertThrows(IllegalArgumentException.class, () -> unit.tryAdd(0));
            assertThrows(IllegalArgumentException.class, () -> unit.tryAdd(-1));
            assertTrue(unit.tryAdd(1));
        }
    }
}
