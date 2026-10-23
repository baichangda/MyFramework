package cn.bcd.lib.base.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class CompressUtilRegressionTest {
    @TempDir(factory = FileUtilTest.WorkspaceTempDirFactory.class)
    Path tempDir;

    @Test
    void emptyArraysAndEmptySlicesProduceValidGzip() {
        byte[][] compressed = {
                CompressUtil.gzip(new byte[0]),
                CompressUtil.gzip(new byte[0], 0, 0),
                CompressUtil.gzip(new byte[]{1}, 1, 0)
        };
        for (byte[] bytes : compressed) {
            assertTrue(bytes.length > 0);
            assertArrayEquals(new byte[0], CompressUtil.unGzip(bytes));
        }
        assertThrows(IndexOutOfBoundsException.class, () -> CompressUtil.gzip(new byte[0], 1, 0));
    }

    @Test
    void splitterReturnsBlockWhenBufferedCompressedDataReachesThreshold() throws Exception {
        byte[] data = new byte[1024 * 1024];
        try (var splitter = new CompressUtil.GzipSplitter(100)) {
            byte[] block = splitter.write(data);
            assertNotNull(block);
            assertArrayEquals(data, CompressUtil.unGzip(block));
            assertNull(splitter.finish());
        }
    }

    @Test
    void splitterFlushesWhenWritingSlice() throws Exception {
        byte[] data = new byte[1024 * 1024];
        data[0] = 1;
        data[data.length - 1] = 2;
        try (var splitter = new CompressUtil.GzipSplitter(100)) {
            byte[] block = splitter.write(data, 1, data.length - 2);
            assertNotNull(block);
            assertArrayEquals(new byte[data.length - 2], CompressUtil.unGzip(block));
            assertNull(splitter.finish());
        }
    }

    @Test
    void splitterPreservesAllDataAcrossBlocksAndFinalRemainder() throws Exception {
        Random random = new Random(42);
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        ByteArrayOutputStream actual = new ByteArrayOutputStream();
        int blocks = 0;
        try (var splitter = new CompressUtil.GzipSplitter(200)) {
            assertNull(splitter.finish());
            for (int i = 0; i < 10; i++) {
                byte[] data = new byte[64];
                random.nextBytes(data);
                expected.write(data);
                byte[] block = splitter.write(data);
                if (block != null) {
                    actual.write(CompressUtil.unGzip(block));
                    blocks++;
                }
            }
            byte[] last = splitter.finish();
            assertNotNull(last);
            actual.write(CompressUtil.unGzip(last));
            assertNull(splitter.finish());
        }
        assertTrue(blocks > 1);
        assertArrayEquals(expected.toByteArray(), actual.toByteArray());
    }

    @Test
    void splitterRejectsNonPositiveThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new CompressUtil.GzipSplitter(0));
        assertThrows(IllegalArgumentException.class, () -> new CompressUtil.GzipSplitter(-1));
    }

    @Test
    void nonAsciiDelimitersAreRejectedBeforeReadingInput() {
        for (char delimiter : new char[]{128, 255, '\u4e2d'}) {
            ByteArrayInputStream input = new ByteArrayInputStream(new byte[]{1, 2, 3});
            assertThrows(IllegalArgumentException.class, () -> CompressUtil.unGzip(input, delimiter, bytes -> {
                fail("invalid delimiter must not invoke callback");
                return true;
            }));
            assertEquals(3, input.available());
        }
    }

    @Test
    void asciiBoundaryDelimitersSplitCorrectly() {
        for (char delimiter : new char[]{0, 127}) {
            byte[] compressed = CompressUtil.gzip(new byte[]{'a', (byte) delimiter, 'b'});
            List<String> values = new ArrayList<>();
            CompressUtil.unGzip(new ByteArrayInputStream(compressed), delimiter, bytes -> {
                values.add(new String(bytes, StandardCharsets.US_ASCII));
                return true;
            });
            assertEquals(List.of("a", "b"), values);
        }
    }

    @Test
    void sameSourceAndTargetAreRejectedWithoutChangingSource() throws IOException {
        byte[] compressed = CompressUtil.gzip(new byte[]{1, 2, 3});
        Path source = Files.write(tempDir.resolve("source.gz"), compressed);
        Path alias = source.getParent().resolve(".").resolve(source.getFileName());
        for (Path target : List.of(source, alias)) {
            assertThrows(IllegalArgumentException.class,
                    () -> CompressUtil.unGzip(source.toString(), target.toString()));
            assertArrayEquals(compressed, Files.readAllBytes(source));
        }
    }

    @Test
    void hardLinkedTargetIsRejectedWithoutChangingSource() throws IOException {
        byte[] compressed = CompressUtil.gzip(new byte[]{1, 2, 3});
        Path source = Files.write(tempDir.resolve("source.gz"), compressed);
        Path target = Files.createLink(tempDir.resolve("alias.gz"), source);
        assertThrows(IllegalArgumentException.class,
                () -> CompressUtil.unGzip(source.toString(), target.toString()));
        assertArrayEquals(compressed, Files.readAllBytes(source));
        assertArrayEquals(compressed, Files.readAllBytes(target));
    }

    @Test
    void distinctTargetReceivesDecompressedData() throws IOException {
        byte[] data = {1, 2, 3};
        byte[] compressed = CompressUtil.gzip(data);
        Path source = Files.write(tempDir.resolve("source.gz"), compressed);
        Path target = tempDir.resolve("target.bin");
        CompressUtil.unGzip(source.toString(), target.toString());
        assertArrayEquals(data, Files.readAllBytes(target));
        assertArrayEquals(compressed, Files.readAllBytes(source));
    }
}
