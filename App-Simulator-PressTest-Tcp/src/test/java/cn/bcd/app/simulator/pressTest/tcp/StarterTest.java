package cn.bcd.app.simulator.pressTest.tcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StarterTest {
    static Starter validStarter() {
        Starter starter = new Starter();
        starter.period = 60;
        starter.num = 1;
        starter.startIndex = 0;
        starter.tcpServerAddress = "127.0.0.1:12345";
        return starter;
    }

    @Test
    void rejectsNonPositivePeriodAndVehicleCount() {
        Starter starter = validStarter();
        for (int value : new int[]{0, -1, Integer.MIN_VALUE}) {
            starter.period = value;
            assertThrows(IllegalArgumentException.class, starter::validate);
        }
        starter.period = 10;
        for (int value : new int[]{0, -1, Integer.MIN_VALUE}) {
            starter.num = value;
            assertThrows(IllegalArgumentException.class, starter::validate);
        }
    }

    @Test
    void rejectsIndexesThatCannotProduceUniqueSeventeenCharacterVins() {
        Starter starter = validStarter();
        for (int value : new int[]{-1, 10_000_000, Integer.MAX_VALUE}) {
            starter.startIndex = value;
            assertThrows(IllegalArgumentException.class, starter::validate);
        }
        starter.startIndex = 9_999_999;
        starter.num = 2;
        assertThrows(IllegalArgumentException.class, starter::validate);
    }

    @Test
    void validatesAddressBeforeConnecting() {
        Starter starter = validStarter();
        for (String value : new String[]{null, "", "localhost", ":80", "host:0",
                "host:-1", "host:65536", "::1:80"}) {
            starter.tcpServerAddress = value;
            assertThrows(IllegalArgumentException.class, starter::validate);
        }
        starter.tcpServerAddress = "[::1]:8080";
        assertEquals("::1", starter.validate().getHost());
        assertEquals(8080, starter.validate().getPort());
    }

    @Test
    void generatesVinsAtUpperBoundary() {
        Starter starter = validStarter();
        starter.startIndex = 9_999_998;
        starter.num = 2;
        starter.validate();
        PressTest<?> pressTest = new PressTestLifecycleTest.TestPressTest();
        pressTest.starter = starter;
        assertArrayEquals(new String[]{"TEST0000009999998", "TEST0000009999999"}, pressTest.getVins());
    }
}
