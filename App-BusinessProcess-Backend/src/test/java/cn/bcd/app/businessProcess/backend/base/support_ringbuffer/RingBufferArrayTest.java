package cn.bcd.app.businessProcess.backend.base.support_ringbuffer;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RingBufferArrayTest {

    @Test
    void shouldRejectInvalidCapacity() {
        assertThrows(IllegalArgumentException.class, () -> new RingBufferArray<>(0));
        assertThrows(IllegalArgumentException.class, () -> new RingBufferArray<>(-1));
    }

    @Test
    void emptyBatchShouldNotChangeState() {
        RingBufferArray<Integer> buffer = new RingBufferArray<>(3);

        buffer.addAll(new Integer[0]);

        assertThrows(NoSuchElementException.class, buffer::getFirst);
        assertThrows(NoSuchElementException.class, buffer::getLast);
        assertEquals(List.of(), buffer.content());

        buffer.addAll(new Integer[]{1, 2});
        buffer.addAll(new Integer[0]);
        assertEquals(List.of(1, 2), buffer.content());
    }

    @Test
    void shouldAllowNullElements() {
        RingBufferArray<Integer> buffer = new RingBufferArray<>(3);

        buffer.add(null);
        buffer.addAll(new Integer[]{1, null});

        assertNull(buffer.getFirst());
        assertNull(buffer.getLast());
        assertEquals(Arrays.asList(null, 1, null), buffer.content());
        assertThrows(NullPointerException.class, () -> buffer.addAll(null));
    }

    @Test
    void shouldKeepNewestElementsAcrossWrapAround() {
        RingBufferArray<Integer> buffer = new RingBufferArray<>(5);

        buffer.addAll(new Integer[]{1, 2, 3});
        buffer.add(4);
        buffer.addAll(new Integer[]{5, 6, 7});

        assertEquals(3, buffer.getFirst());
        assertEquals(7, buffer.getLast());
        assertEquals(List.of(3, 4, 5, 6, 7), buffer.content());
    }

    @Test
    void oversizedBatchShouldReplaceExistingContent() {
        RingBufferArray<Integer> buffer = new RingBufferArray<>(3);
        buffer.addAll(new Integer[]{1, 2});

        buffer.addAll(new Integer[]{3, 4, 5, 6, 7});

        assertEquals(List.of(5, 6, 7), buffer.content());
    }

    @Test
    void capacityOneShouldAlwaysKeepLatestElement() {
        RingBufferArray<Integer> buffer = new RingBufferArray<>(1);

        buffer.add(1);
        buffer.addAll(new Integer[]{2, 3});

        assertEquals(3, buffer.getFirst());
        assertEquals(3, buffer.getLast());
        assertEquals(List.of(3), buffer.content());
    }
}
