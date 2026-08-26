package cn.bcd.app.businessProcess.backend.base.support_ringbuffer;

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

    public int copyTo(Object[] target, int offset) {
        Objects.requireNonNull(target, "target");
        Objects.checkFromIndexSize(offset, elementCount, target.length);

        int firstPartLength = Math.min(elementCount, size - firstIndex);
        System.arraycopy(content, firstIndex, target, offset, firstPartLength);
        int secondPartLength = elementCount - firstPartLength;
        if (secondPartLength > 0) {
            System.arraycopy(content, 0, target, offset + firstPartLength, secondPartLength);
        }
        return elementCount;
    }

    public Object[] content() {
        Object[] result = new Object[elementCount];
        copyTo(result, 0);
        return result;
    }
}
