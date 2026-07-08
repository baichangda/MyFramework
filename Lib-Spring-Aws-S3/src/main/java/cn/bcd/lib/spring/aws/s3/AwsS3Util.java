package cn.bcd.lib.spring.aws.s3;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.base.util.IOUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

@Component
@ConditionalOnProperty(value = "lib.spring.aws.s3.endpoint")
public class AwsS3Util {

    static final Logger logger = LoggerFactory.getLogger(AwsS3Util.class);

    /**
     * S3 multipart upload 最小分片大小是 5 MiB。
     * 这里默认用 16 MiB，内存占用和上传效率比较均衡。
     */
    private static final long MIN_PART_SIZE = 5L * 1024 * 1024;
    private static final long DEFAULT_PART_SIZE = 16L * 1024 * 1024;
    private static final int MAX_PART_COUNT = 10_000;

    static AwsS3Prop awsS3Prop;
    static S3Client s3Client;

    public AwsS3Util(AwsS3Prop awsS3Prop, S3Client s3Client) {
        AwsS3Util.awsS3Prop = awsS3Prop;
        AwsS3Util.s3Client = s3Client;
    }

    public static List<String> listObjects(String prefix, boolean recursive) {
        try {
            List<String> keyList = new ArrayList<>();

            ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                    .bucket(awsS3Prop.bucket);

            if (prefix != null && !prefix.isBlank()) {
                builder.prefix(prefix);
            }

            if (!recursive) {
                builder.delimiter("/");
            }

            for (ListObjectsV2Response response : s3Client.listObjectsV2Paginator(builder.build())) {
                for (S3Object s3Object : response.contents()) {
                    String key = s3Object.key();

                    if (key.endsWith("/")) {
                        continue;
                    }

                    keyList.add(key);
                }
            }

            return keyList;
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    public static void removeObjects(String... paths) {
        try {
            if (paths == null || paths.length == 0) {
                return;
            }

            List<String> pathList = Arrays.stream(paths)
                    .filter(path -> path != null && !path.isBlank())
                    .toList();

            if (pathList.isEmpty()) {
                return;
            }

            int batchSize = 1000;

            for (int i = 0; i < pathList.size(); i += batchSize) {
                List<String> batch = pathList.subList(i, Math.min(i + batchSize, pathList.size()));

                List<ObjectIdentifier> objects = batch.stream()
                        .map(path -> ObjectIdentifier.builder().key(path).build())
                        .toList();

                Delete delete = Delete.builder()
                        .objects(objects)
                        .quiet(true)
                        .build();

                DeleteObjectsRequest request = DeleteObjectsRequest.builder()
                        .bucket(awsS3Prop.bucket)
                        .delete(delete)
                        .build();

                DeleteObjectsResponse response = s3Client.deleteObjects(request);

                if (response.hasErrors() && !response.errors().isEmpty()) {
                    throw BaseException.get("S3 delete objects failed: \n{}", response.errors());
                }
            }
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    record PutEle(Path path, String prefix) {
    }

    /**
     * 上传dir和其下的所有dir和文件
     * 文件路径为 pathPrefix+dir/.../dir/filename
     *
     * @param pathPrefix           aws s3文件路径前缀、不能以/开头和结尾、可以为null或空字符串
     * @param appendOuterDirToPath 是否将最外层的dir添加到路径中
     * @param dirPaths             上传的本地文件夹
     */
    public static void putDir(String pathPrefix, boolean appendOuterDirToPath, Path... dirPaths) {
        List<PutEle> filePathList = new ArrayList<>();
        List<PutEle> dirPathList = new ArrayList<>(Arrays.stream(dirPaths).map(e -> {
            String prefix;
            if (pathPrefix == null || pathPrefix.isEmpty()) {
                prefix = appendOuterDirToPath ? e.getFileName().toString() : "";
            } else {
                if (appendOuterDirToPath) {
                    prefix = pathPrefix + "/" + e.getFileName().toString();
                } else {
                    prefix = pathPrefix;
                }
            }
            return new PutEle(e, prefix);
        }).toList());
        try {
            for (int i = 0; i < dirPathList.size(); i++) {
                PutEle putEle = dirPathList.get(i);
                try (Stream<Path> stream = Files.list(putEle.path)) {
                    stream.forEach(e -> {
                        if (Files.isDirectory(e)) {
                            PutEle ele = new PutEle(e, putEle.prefix + "/" + e.getFileName().toString());
                            dirPathList.add(ele);
                        } else {
                            PutEle ele = new PutEle(e, putEle.prefix);
                            filePathList.add(ele);
                        }
                    });
                }
            }
            for (PutEle ele : filePathList) {
                putObject(ele.prefix, ele.path);
            }
        } catch (IOException ex) {
            throw BaseException.get(ex);
        }
    }

    /**
     * 上传文件
     *
     * @param filePath 本地文件路径
     * @param path     aws s3文件路径
     */
    public static void putObject(Path filePath, String path) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(awsS3Prop.bucket)
                    .key(path)
                    .build();
            s3Client.putObject(request, filePath);
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    /**
     * 上传多个文件、aws s3文件路径为 pathPrefix+文件名称
     *
     * @param pathPrefix aws s3文件路径前缀、不能以/开头和结尾、可以为null或空字符串
     * @param filePaths  上传的本地文件
     */
    public static void putObject(String pathPrefix, Path... filePaths) {
        for (Path filePath : filePaths) {
            String prefix;
            if (pathPrefix == null || pathPrefix.isEmpty()) {
                prefix = filePath.getFileName().toString();
            } else {
                prefix = pathPrefix + "/" + filePath.getFileName().toString();
            }
            putObject(filePath, prefix);
        }
    }

    /**
     * 小文件上传。
     * <p>
     * 注意：这个方法需要准确的 contentLength。
     * 如果是大文件，也可以调用这个方法，但它不是 multipart upload。
     */
    public static void putObject(InputStream is, long contentLength, String path) {
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(awsS3Prop.bucket)
                    .key(path)
                    .build();
            s3Client.putObject(request, RequestBody.fromInputStream(is, contentLength));
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    /**
     * 大文件上传。
     * <p>
     * 使用 S3 Multipart Upload。
     * 每次只读取 partSize 字节到内存，适合大文件。
     */
    public static void putObjectLarge(InputStream is, String path) {
        putObjectLarge(is, path, DEFAULT_PART_SIZE);
    }

    /**
     * 大文件上传，可自定义分片大小。
     *
     * @param is       输入流
     * @param path     S3 object key
     * @param partSize 分片大小，必须 >= 5 MiB，建议 16 MiB / 32 MiB / 64 MiB
     */
    public static void putObjectLarge(InputStream is, String path, long partSize) {
        String uploadId = null;

        try {
            if (partSize < MIN_PART_SIZE) {
                throw new IllegalArgumentException("partSize must be >= 5 MiB");
            }

            CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                    .bucket(awsS3Prop.bucket)
                    .key(path)
                    .build();

            CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
            uploadId = createResponse.uploadId();

            List<CompletedPart> completedParts = new ArrayList<>();

            int partNumber = 1;

            while (true) {
                byte[] partBytes = readPartBytes(is, partSize);

                if (partBytes.length == 0) {
                    break;
                }

                if (partNumber > MAX_PART_COUNT) {
                    throw new IllegalStateException("S3 multipart upload part count exceeds 10000, please increase partSize");
                }

                UploadPartRequest uploadPartRequest = UploadPartRequest.builder()
                        .bucket(awsS3Prop.bucket)
                        .key(path)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) partBytes.length)
                        .build();

                UploadPartResponse uploadPartResponse = s3Client.uploadPart(
                        uploadPartRequest,
                        RequestBody.fromBytes(partBytes)
                );

                CompletedPart completedPart = CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(uploadPartResponse.eTag())
                        .build();

                completedParts.add(completedPart);

                logger.debug("upload part success, path: {}, partNumber: {}, size: {}", path, partNumber, partBytes.length);

                partNumber++;
            }

            CompletedMultipartUpload completedMultipartUpload = CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build();

            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                    .bucket(awsS3Prop.bucket)
                    .key(path)
                    .uploadId(uploadId)
                    .multipartUpload(completedMultipartUpload)
                    .build();

            s3Client.completeMultipartUpload(completeRequest);
            logger.info("multipart upload success, path: {}, partCount: {}", path, completedParts.size());
        } catch (Exception e) {
            if (uploadId != null) {
                abortMultipartUpload(path, uploadId);
            }
            throw BaseException.get(e);
        }
    }

    private static byte[] readPartBytes(InputStream is, long partSize) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream((int) Math.min(partSize, Integer.MAX_VALUE));

        byte[] buffer = new byte[8192];
        long remaining = partSize;

        while (remaining > 0) {
            int readLength = (int) Math.min(buffer.length, remaining);
            int len = is.read(buffer, 0, readLength);

            if (len == -1) {
                break;
            }

            bos.write(buffer, 0, len);
            remaining -= len;
        }

        return bos.toByteArray();
    }

    private static void abortMultipartUpload(String path, String uploadId) {
        try {
            AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                    .bucket(awsS3Prop.bucket)
                    .key(path)
                    .uploadId(uploadId)
                    .build();

            s3Client.abortMultipartUpload(abortRequest);
            logger.warn("multipart upload aborted, path: {}, uploadId: {}", path, uploadId);
        } catch (Exception ex) {
            logger.error("abort multipart upload failed, path: {}, uploadId: {}", path, uploadId, ex);
        }
    }

    public static boolean objectExists(String path) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                    .bucket(awsS3Prop.bucket)
                    .key(path)
                    .build();

            s3Client.headObject(request);
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw BaseException.get(e);
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    /**
     * 获取对象
     *
     * @param path     aws对象路径
     * @param consumer 对象输入流消费者、当输入流为null时候代表对象不存在
     */
    public static void getObject(String path, Consumer<ResponseInputStream<GetObjectResponse>> consumer) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(awsS3Prop.bucket)
                .key(path)
                .build();
        try (ResponseInputStream<GetObjectResponse> responseInputStream = s3Client.getObject(request)) {
            consumer.accept(responseInputStream);
        } catch (NoSuchKeyException e) {
            consumer.accept(null);
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                consumer.accept(null);
                return;
            }
            throw BaseException.get(e);
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    /**
     * 获取对象
     *
     * @param path aws对象路径
     */
    public static byte[] getObject(String path) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(awsS3Prop.bucket)
                .key(path)
                .build();
        try {
            return s3Client.getObjectAsBytes(request).asByteArray();
        } catch (NoSuchKeyException e) {
            return null;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return null;
            }
            throw BaseException.get(e);
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    public static void getObject(String path, String destFilePath) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(awsS3Prop.bucket)
                .key(path)
                .build();
        try {
            s3Client.getObject(request, Paths.get(destFilePath));
        } catch (NoSuchKeyException _) {
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return;
            }
            throw BaseException.get(e);
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }


}