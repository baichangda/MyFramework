package cn.bcd.app.businessProcess.backend.base.support_ringbuffer;

import java.util.ArrayList;
import java.util.NoSuchElementException;
import java.util.Objects;

/**
 * 供单线程使用的固定容量环形缓冲区。
 * 容量已满时，新元素会覆盖最早的元素。允许存储 {@code null}。
 *
 * @param <T> 元素类型
 */
public class RingBufferArray<T> {
    private int firstIndex;
    private int nextWriteIndex;
    private int elementCount;
    private final int size;
    private final Object[] content;

    public RingBufferArray(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("size must be greater than 0");
        }
        this.size = size;
        this.content = new Object[size];
    }

    public void add(T element) {
        content[nextWriteIndex] = element;
        if (++nextWriteIndex == size) {
            nextWriteIndex = 0;
        }
        if (elementCount == size) {
            firstIndex = nextWriteIndex;
        } else {
            elementCount++;
        }
    }

    public void addAll(T[] elements) {
        Objects.requireNonNull(elements, "elements");
        int length = elements.length;
        if (length == 0) {
            return;
        }

        if (length >= size) {
            System.arraycopy(elements, length - size, content, 0, size);
            firstIndex = 0;
            nextWriteIndex = 0;
            elementCount = size;
            return;
        }

        int firstPartLength = Math.min(length, size - nextWriteIndex);
        System.arraycopy(elements, 0, content, nextWriteIndex, firstPartLength);
        if (firstPartLength < length) {
            System.arraycopy(elements, firstPartLength, content, 0, length - firstPartLength);
        }

        nextWriteIndex += length;
        if (nextWriteIndex >= size) {
            nextWriteIndex -= size;
        }
        int overflow = elementCount + length - size;
        if (overflow > 0) {
            firstIndex += overflow;
            if (firstIndex >= size) {
                firstIndex -= size;
            }
            elementCount = size;
        } else {
            elementCount += length;
        }
    }

    @SuppressWarnings("unchecked")
    public T getFirst() {
        if (elementCount == 0) {
            throw new NoSuchElementException("ring buffer is empty");
        }
        return (T) content[firstIndex];
    }

    @SuppressWarnings("unchecked")
    public T getLast() {
        if (elementCount == 0) {
            throw new NoSuchElementException("ring buffer is empty");
        }
        int lastIndex = nextWriteIndex == 0 ? size - 1 : nextWriteIndex - 1;
        return (T) content[lastIndex];
    }

    @SuppressWarnings("unchecked")
    public ArrayList<T> content() {
        ArrayList<T> result = new ArrayList<>(elementCount);
        int firstPartLength = Math.min(elementCount, size - firstIndex);
        for (int i = 0; i < firstPartLength; i++) {
            result.add((T) content[firstIndex + i]);
        }
        int secondPartLength = elementCount - firstPartLength;
        for (int i = 0; i < secondPartLength; i++) {
            result.add((T) content[i]);
        }
        return result;
    }
}
