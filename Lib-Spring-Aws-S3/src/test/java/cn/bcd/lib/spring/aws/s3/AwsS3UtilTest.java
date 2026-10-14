package cn.bcd.lib.spring.aws.s3;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class AwsS3UtilTest {
    @TempDir
    Path tempDir;
    private S3Client previousClient;
    private AwsS3Prop previousProp;

    @BeforeEach
    void saveState() {
        previousClient = AwsS3Util.s3Client;
        previousProp = AwsS3Util.awsS3Prop;
        AwsS3Util.awsS3Prop = new AwsS3Prop();
        AwsS3Util.awsS3Prop.bucket = "test";
    }

    @AfterEach
    void restoreState() {
        AwsS3Util.s3Client = previousClient;
        AwsS3Util.awsS3Prop = previousProp;
    }

    interface Call {
        Object invoke(String method, Object[] args) throws Exception;
    }

    private void client(Call call) {
        AwsS3Util.s3Client = (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(),
                new Class<?>[]{S3Client.class}, (proxy, method, args) -> call.invoke(method.getName(), args));
    }

    @Test
    void ordinaryUploadSupportsFileBytesAndStreamWithExactKey() throws Exception {
        byte[] expected = new byte[]{1, 2, 3};
        Path file = tempDir.resolve("source.bin");
        Files.write(file, expected);
        AtomicInteger calls = new AtomicInteger();
        client((method, args) -> {
            assertEquals("putObject", method);
            PutObjectRequest request = (PutObjectRequest) args[0];
            assertEquals("test", request.bucket());
            assertEquals("target/custom.bin", request.key());
            RequestBody body = (RequestBody) args[1];
            assertEquals(3L, body.contentLength());
            try (var stream = body.contentStreamProvider().newStream()) {
                assertArrayEquals(expected, stream.readAllBytes());
            }
            calls.incrementAndGet();
            return PutObjectResponse.builder().build();
        });
        AwsS3Util.putObject("target/custom.bin", RequestBody.fromFile(file));
        AwsS3Util.putObject("target/custom.bin", RequestBody.fromBytes(expected));
        try (var stream = new ByteArrayInputStream(expected)) {
            AwsS3Util.putObject("target/custom.bin", RequestBody.fromInputStream(stream, expected.length));
        }
        assertEquals(3, calls.get());
    }

    @Test
    void directoryKeysRespectPrefixAndOuterDirectory() throws Exception {
        Files.writeString(tempDir.resolve("root.txt"), "root");
        Files.createDirectories(tempDir.resolve("sub/deep"));
        Files.writeString(tempDir.resolve("sub/deep/file.txt"), "nested");
        List<String> keys = new ArrayList<>();
        client((method, args) -> {
            assertEquals("putObject", method);
            keys.add(((PutObjectRequest) args[0]).key());
            return PutObjectResponse.builder().build();
        });
        AwsS3Util.putDir("", false, tempDir);
        assertEquals(List.of("root.txt", "sub/deep/file.txt"), keys.stream().sorted().toList());
        keys.clear();
        AwsS3Util.putDir("prefix", true, tempDir);
        String prefix = "prefix/" + tempDir.getFileName() + "/";
        assertEquals(List.of(prefix + "root.txt", prefix + "sub/deep/file.txt"), keys.stream().sorted().toList());
    }

    @Test
    void emptyStreamUsesOrdinaryUpload() {
        AtomicInteger calls = new AtomicInteger();
        client((method, args) -> {
            assertEquals("putObject", method);
            RequestBody body = (RequestBody) args[1];
            assertEquals(0L, body.contentLength());
            assertEquals(-1, body.contentStreamProvider().newStream().read());
            calls.incrementAndGet();
            return PutObjectResponse.builder().build();
        });
        AwsS3Util.putObjectLarge(new ByteArrayInputStream(new byte[0]), "empty");
        assertEquals(1, calls.get());
    }

    @Test
    void multipartPreservesBytesAndProvidesRepeatableBodies() {
        int size = 5 * 1024 * 1024;
        byte[] data = new byte[size + 37];
        Arrays.fill(data, 0, size, (byte) 7);
        Arrays.fill(data, size, data.length, (byte) 9);
        AtomicInteger parts = new AtomicInteger();
        client((method, args) -> {
            switch (method) {
                case "createMultipartUpload":
                    return CreateMultipartUploadResponse.builder().uploadId("upload").build();
                case "uploadPart":
                    int number = parts.incrementAndGet();
                    UploadPartRequest request = (UploadPartRequest) args[0];
                    assertEquals(number, request.partNumber());
                    RequestBody body = (RequestBody) args[1];
                    byte[] expected = number == 1 ? Arrays.copyOf(data, size) : Arrays.copyOfRange(data, size, data.length);
                    assertArrayEquals(expected, body.contentStreamProvider().newStream().readAllBytes());
                    assertArrayEquals(expected, body.contentStreamProvider().newStream().readAllBytes());
                    return UploadPartResponse.builder().eTag("etag" + number).build();
                case "completeMultipartUpload":
                    assertEquals(List.of(1, 2), ((CompleteMultipartUploadRequest) args[0])
                            .multipartUpload().parts().stream().map(CompletedPart::partNumber).toList());
                    return CompleteMultipartUploadResponse.builder().build();
                default:
                    throw new AssertionError(method);
            }
        });
        AwsS3Util.putObjectLarge(new ByteArrayInputStream(data), "large", size);
        assertEquals(2, parts.get());
    }

    @Test
    void failedPartAbortsMultipart() {
        AtomicInteger aborted = new AtomicInteger();
        client((method, args) -> {
            return switch (method) {
                case "createMultipartUpload" -> CreateMultipartUploadResponse.builder().uploadId("upload").build();
                case "uploadPart" -> throw S3Exception.builder().statusCode(500).build();
                case "abortMultipartUpload" -> {
                    aborted.incrementAndGet();
                    yield AbortMultipartUploadResponse.builder().build();
                }
                default -> throw new AssertionError(method);
            };
        });
        assertThrows(RuntimeException.class, () -> AwsS3Util.putObjectLarge(new ByteArrayInputStream(new byte[]{1}), "key"));
        assertEquals(1, aborted.get());
    }

    @Test
    void invalidPartSizeMakesNoRequest() {
        client((method, args) -> { throw new AssertionError(method); });
        assertThrows(RuntimeException.class, () -> AwsS3Util.putObjectLarge(new ByteArrayInputStream(new byte[0]), "key", 1));
        assertThrows(RuntimeException.class, () -> AwsS3Util.putObjectLarge(new ByteArrayInputStream(new byte[0]), "key", Long.MAX_VALUE));
    }

    @Test
    void callback404IsNotMistakenForMissingObjectAndStreamCloses() {
        AtomicInteger closed = new AtomicInteger();
        ResponseInputStream<GetObjectResponse> stream = new ResponseInputStream<>(GetObjectResponse.builder().build(),
                new ByteArrayInputStream(new byte[]{1}) {
                    @Override
                    public void close() { closed.incrementAndGet(); }
                });
        client((method, args) -> stream);
        AtomicInteger callbacks = new AtomicInteger();
        assertThrows(RuntimeException.class, () -> AwsS3Util.getObject("key", response -> {
            assertSame(stream, response);
            callbacks.incrementAndGet();
            throw S3Exception.builder().statusCode(404).build();
        }));
        assertEquals(1, callbacks.get());
        assertEquals(1, closed.get());
    }

    @Test
    void missingDownloadReturnsFalseAndPreservesExistingFile() throws Exception {
        Path target = tempDir.resolve("existing.txt");
        Files.writeString(target, "old");
        client((method, args) -> { throw S3Exception.builder().statusCode(404).build(); });
        assertFalse(AwsS3Util.getObject("missing", target.toString()));
        assertEquals("old", Files.readString(target));
        AtomicInteger calls = new AtomicInteger();
        AwsS3Util.getObject("missing", stream -> {
            assertNull(stream);
            calls.incrementAndGet();
        });
        assertEquals(1, calls.get());
        client((method, args) -> GetObjectResponse.builder().build());
        assertTrue(AwsS3Util.getObject("present", tempDir.resolve("new.txt").toString()));
    }

    @Test
    void listingConsumesAllPagesAndSkipsDirectoryMarkers() {
        AtomicInteger pages = new AtomicInteger();
        S3Client pager = (S3Client) Proxy.newProxyInstance(S3Client.class.getClassLoader(), new Class<?>[]{S3Client.class},
                (proxy, method, args) -> {
                    ListObjectsV2Request request = (ListObjectsV2Request) args[0];
                    assertEquals("prefix/", request.prefix());
                    assertEquals("/", request.delimiter());
                    if (pages.getAndIncrement() == 0) {
                        return ListObjectsV2Response.builder().isTruncated(true).nextContinuationToken("next")
                                .contents(S3Object.builder().key("prefix/a").build(), S3Object.builder().key("prefix/dir/").build()).build();
                    }
                    assertEquals("next", request.continuationToken());
                    return ListObjectsV2Response.builder().isTruncated(false)
                            .contents(S3Object.builder().key("prefix/b").build()).build();
                });
        client((method, args) -> new ListObjectsV2Iterable(pager, (ListObjectsV2Request) args[0]));
        List<String> keys = new ArrayList<>();
        AwsS3Util.listObjects("prefix/", false, keys::add);
        assertEquals(List.of("prefix/a", "prefix/b"), keys);
        assertEquals(2, pages.get());
    }
}
