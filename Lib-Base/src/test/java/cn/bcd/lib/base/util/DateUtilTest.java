package cn.bcd.lib.base.util;

import cn.bcd.lib.base.exception.BaseException;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DateUtilTest {

    @Test
    void rangeRejectsNonPositiveAmount() {
        Date start = new Date(0);
        Date end = new Date(60_000);

        assertThrows(BaseException.class,
                () -> DateUtil.range(start, end, 0, ChronoUnit.MINUTES, ZoneOffset.UTC));
    }

    @Test
    void rangeReturnsEmptyForEmptyInterval() {
        Date date = new Date(0);

        assertTrue(DateUtil.range(date, date, 1, ChronoUnit.MINUTES, ZoneOffset.UTC).isEmpty());
    }

    @Test
    void getDiffDoesNotOverflowIntRange() {
        Date start = new Date(0);
        Date end = new Date(3_000_000_000_000L);

        assertEquals(3_000_000_000L, DateUtil.getDiff(start, end, ChronoUnit.SECONDS, false));
    }
}
