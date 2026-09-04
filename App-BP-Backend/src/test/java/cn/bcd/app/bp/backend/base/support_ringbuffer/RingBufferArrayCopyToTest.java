package cn.bcd.app.bp.backend.base.support_ringbuffer;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RingBufferArrayCopyToTest {

    @Test
    void shouldCopyWrappedContentIntoRequestedOffset() {
        RingBufferArray<Integer> buffer = new RingBufferArray<>(5);
        buffer.addAll(new Integer[]{1, 2, 3, 4});
        buffer.addAll(new Integer[]{5, null, 7});
        Object[] target = new Object[7];
        Arrays.fill(target, -1);

        int copied = buffer.copyTo(target, 1);

        assertEquals(5, copied);
        assertArrayEquals(new Object[]{-1, 3, 4, 5, null, 7, -1}, target);
    }

    @Test
    void shouldValidateTargetRange() {
        RingBufferArray<Integer> buffer = new RingBufferArray<>(3);
        buffer.addAll(new Integer[]{1, 2, 3});

        assertThrows(NullPointerException.class, () -> buffer.copyTo(null, 0));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.copyTo(new Object[3], 1));
        assertThrows(IndexOutOfBoundsException.class, () -> buffer.copyTo(new Object[3], -1));
    }
}
