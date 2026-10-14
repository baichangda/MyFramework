package cn.bcd.lib.spring.aws.s3;

import cn.bcd.lib.base.exception.BaseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.FileVisitResult;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * 默认 bucket 的同步 S3 操作工具，支持兼容 S3 协议的对象存储。
 * <p>
 * 配置 {@code lib.spring.aws.s3.endpoint} 并由 Spring 创建本组件后，才能调用静态方法。
 * 对象路径均为 bucket 内的完整 key，不是本地路径或 URL；key 中的 {@code /} 仅用于表示逻辑层级。
 * 上传同名 key 的行为由存储服务及 bucket 版本配置决定。
 * <p>
 * 本类使用静态配置和客户端，不支持多个 Spring 上下文之间隔离；不要在调用期间替换它们。
 * 各方法均同步执行，服务端、网络和回调异常通常包装为 {@link BaseException} 后抛出。
 */
@Component
@ConditionalOnProperty(value = "lib.spring.aws.s3.endpoint")
public class AwsS3Util {

    static final Logger logger = LoggerFactory.getLogger(AwsS3Util.class);

    /**
     * 非最后一个分片的最小大小；最后一个分片允许小于 5 MiB。
     */
    private static final long MIN_PART_SIZE = 5L * 1024 * 1024;
    /** 默认分片大小，同时决定每次分片上传分配的缓冲区容量。 */
    private static final long DEFAULT_PART_SIZE = 16L * 1024 * 1024;
    /** 单次 multipart upload 的最大分片数量。 */
    private static final int MAX_PART_COUNT = 10_000;

    static AwsS3Prop awsS3Prop;
    static S3Client s3Client;

    /**
     * 保存 Spring 注入的默认配置和客户端，供静态方法使用。
     *
     * @param awsS3Prop 包含默认 bucket 的 S3 配置
     * @param s3Client  由 Spring 管理生命周期的同步客户端
     */
    public AwsS3Util(AwsS3Prop awsS3Prop, S3Client s3Client) {
        AwsS3Util.awsS3Prop = awsS3Prop;
        AwsS3Util.s3Client = s3Client;
    }

    /**
     * 列举匹配前缀的对象并收集全部 key，自动读取所有分页。
     * <p>
     * 不返回以 {@code /} 结尾的目录占位对象；对象很多时应使用回调重载，避免完整列表占用内存。
     *
     * @param prefix    key 的字面前缀，null 或空白表示不限制；列举目录时应自行带上结尾的 {@code /}
     * @param recursive true 表示包含所有层级；false 表示以 {@code /} 分隔，仅返回当前层的对象
     * @return 匹配对象的完整 key 列表，无匹配对象时为空列表，不包含子目录前缀
     * @throws BaseException 列举失败
     */
    public static List<String> listObjects(String prefix, boolean recursive) {
        List<String> keyList = new ArrayList<>();
        listObjects(prefix, recursive, keyList::add);
        return keyList;
    }

    /**
     * 分页列举对象，在调用线程中逐个消费 key，不收集完整列表。
     * <p>
     * 跳过目录占位对象和子目录前缀。回调抛出异常后立即停止，已经执行的回调不会回滚。
     *
     * @param prefix    key 的字面前缀，null 或空白表示不限制，不会自动追加 {@code /}
     * @param recursive true 表示包含所有层级；false 表示仅列举当前层对象
     * @param consumer  对完整 key 的同步处理逻辑，不能为 null
     * @throws BaseException 列举或回调执行失败
     */
    public static void listObjects(String prefix, boolean recursive, Consumer<String> consumer) {
        try {

            ListObjectsV2Request.Builder builder = ListObjectsV2Request.builder()
                    .bucket(awsS3Prop.bucket);

            if (prefix != null && !prefix.isBlank()) {
                builder.prefix(prefix);
            }

            if (!recursive) {
                // 服务端将更深层的 key 折叠为 commonPrefixes，本方法只处理 contents。
                builder.delimiter("/");
            }

            // paginator 按需获取下一页，回调消费完当前页后才继续。
            for (ListObjectsV2Response response : s3Client.listObjectsV2Paginator(builder.build())) {
                for (S3Object s3Object : response.contents()) {
                    String key = s3Object.key();

                    if (key.endsWith("/")) {
                        continue;
                    }

                    consumer.accept(key);
                }
            }

        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    /**
     * 按完整 key 批量删除对象，每个请求最多包含 1000 个 key。
     * <p>
     * null、空数组以及数组中的 null 或空白 key 会被忽略；不会按前缀递归删除。
     * 不指定版本 ID，启用版本控制时遵循服务端的删除标记语义，并非删除所有历史版本。
     * 任一批次失败或返回对象级错误时停止，已成功删除的对象不会恢复。
     *
     * @param paths 要删除的完整对象 key
     * @throws BaseException 请求失败或响应包含对象级删除错误
     */
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
                        // quiet 只省略成功项，仍需检查响应中的逐项错误。
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

    /**
     * 递归上传本地目录中的普通文件，保留各文件相对于根目录的层级。
     * <p>
     * 边遍历边同步上传，不预先保存完整文件列表；不跟随目录符号链接，也不上传符号链接文件。
     * 不创建目录占位对象，因此空目录不会出现在 S3 中。发生错误后停止，已上传对象不会回滚。
     * <p>
     * 例如根目录 {@code data} 中的 {@code sub/a.txt}，前缀为 {@code backup} 时，
     * appendOuterDirToPath 为 false 的 key 为 {@code backup/sub/a.txt}，为 true 时为
     * {@code backup/data/sub/a.txt}。多个根目录映射到相同 key 时可能覆盖对象。
     *
     * @param pathPrefix           对象 key 前缀，不应以 / 开头或结尾，可以为 null 或空字符串
     * @param appendOuterDirToPath 是否在前缀后追加每个根目录的名称
     * @param dirPaths             待上传的本地目录，数组和目录元素不能为 null
     * @throws BaseException 目录不存在、遍历失败或上传失败
     */
    public static void putDir(String pathPrefix, boolean appendOuterDirToPath, Path... dirPaths) {
        try {
            for (Path dirPath : dirPaths) {
                Path root = dirPath.toAbsolutePath().normalize();
                if (!Files.isDirectory(root)) {
                    throw new IOException("Not a directory: " + root);
                }
                String prefix = appendOuterDirToPath
                        ? joinKey(pathPrefix, root.getFileName().toString()) : pathPrefix;
                Files.walkFileTree(root, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile()) {
                            String key = root.relativize(file).toString()
                                    // 将 Windows 等平台的路径分隔符统一为 S3 key 使用的 /。
                                    .replace(file.getFileSystem().getSeparator(), "/");
                            putObject(file, joinKey(prefix, key));
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException ex) {
            throw BaseException.get(ex);
        }
    }

    /**
     * 连接逻辑前缀和相对 key；空前缀不添加 /，其余内容保持原样，不进行路径规范化。
     *
     * @param prefix 可为 null 或空字符串的前缀
     * @param name   相对 key
     * @return 拼接后的 key
     */
    private static String joinKey(String prefix, String name) {
        return prefix == null || prefix.isEmpty() ? name : prefix + "/" + name;
    }

    /**
     * 将本地文件通过单次 PutObject 请求上传到指定 key。
     * <p>
     * 不自动选择分片上传；大文件可通过输入流调用 {@link #putObjectLarge(InputStream, String)}。
     *
     * @param filePath 可读取的本地文件路径
     * @param path     目标对象的完整 key
     * @throws BaseException 文件读取或上传失败
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
     * 依次上传多个本地文件，key 为前缀与文件名的拼接，不保留本地父目录层级。
     * <p>
     * 空前缀时直接使用文件名。不同目录中的同名文件会映射到同一个 key。
     * 任一文件失败后停止，已上传对象不会回滚；每个文件使用单次 PutObject 请求。
     *
     * @param pathPrefix 对象 key 前缀，不应以 / 开头或结尾，可以为 null 或空字符串
     * @param filePaths  可读取的本地文件，数组和文件元素不能为 null
     * @throws BaseException 文件读取或上传失败
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
     * 使用单次 PutObject 请求上传已知长度的输入流。
     * <p>
     * contentLength 必须与输入流剩余数据的字节数一致，长度不正确可能导致数据截断或请求失败。
     * 本方法不负责关闭调用方输入流；调用方应在同步调用完成后关闭流。
     * 重试能否重新读取数据取决于输入流的可重置能力，非可重置的大流建议使用分片上传。
     *
     * @param is            位于待上传数据起始位置的输入流
     * @param contentLength 待上传数据的准确字节数，必须非负
     * @param path          目标对象的完整 key
     * @throws BaseException 输入流读取或上传失败
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
     * 使用默认 16 MiB 分片大小上传输入流，无需预先知道数据总长度。
     * <p>
     * 同步串行上传，复用一个分片缓冲区；空流上传为空对象。调用方负责关闭输入流。
     *
     * @param is   位于待上传数据起始位置的输入流
     * @param path 目标对象的完整 key
     * @throws BaseException 读取、上传或完成分片上传失败
     * @see #putObjectLarge(InputStream, String, long)
     */
    public static void putObjectLarge(InputStream is, String path) {
        putObjectLarge(is, path, DEFAULT_PART_SIZE);
    }

    /**
     * 使用指定大小的分片缓冲区上传输入流，所有分片按编号串行上传。
     * <p>
     * 缓冲区内的数据可重复读取以供 SDK 重试，不要求原始输入流支持 reset。
     * 最后一片允许不足 partSize；空流使用普通 PutObject 上传，不创建分片任务。
     * 最多允许 10000 个分片，调用方应根据数据大小选择分片大小，避免上传到末尾才超限。
     * <p>
     * 取得 uploadId 后发生异常时尝试终止分片任务；终止失败仅记录日志，保留原始异常。
     * 本方法不负责关闭调用方输入流；缓冲区容量约为 partSize，应结合可用堆内存选择大小。
     *
     * @param is       位于待上传数据起始位置的输入流
     * @param path     目标对象的完整 key
     * @param partSize 分片字节数，范围为 [5 MiB, Integer.MAX_VALUE - 8]，建议 16/32/64 MiB
     * @throws BaseException 参数范围不合法、分片数超限、读取或 S3 操作失败
     */
    public static void putObjectLarge(InputStream is, String path, long partSize) {
        String uploadId = null;

        try {
            if (partSize < MIN_PART_SIZE || partSize > Integer.MAX_VALUE - 8L) {
                throw new IllegalArgumentException("partSize must be between 5 MiB and Integer.MAX_VALUE - 8");
            }

            // 先探测空流，避免创建无法用零个分片完成的 multipart upload。
            int firstByte = is.read();
            if (firstByte == -1) {
                putObject(new ByteArrayInputStream(new byte[0]), 0, path);
                return;
            }
            // 只分配一次；补齐首片时保留前瞻读取的第一个字节。
            byte[] buffer = new byte[(int) partSize];
            buffer[0] = (byte) firstByte;
            int partLength = 1 + is.readNBytes(buffer, 1, buffer.length - 1);

            CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
                    .bucket(awsS3Prop.bucket)
                    .key(path)
                    .build();

            CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
            uploadId = createResponse.uploadId();

            List<CompletedPart> completedParts = new ArrayList<>();

            int partNumber = 1;

            while (true) {
                if (partLength == 0) {
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
                        .contentLength((long) partLength)
                        .build();

                // 每次重试创建独立流；必须等同步 uploadPart 返回后才能覆盖 buffer。
                UploadPartResponse uploadPartResponse = s3Client.uploadPart(
                        uploadPartRequest,
                        RequestBody.fromContentProvider(
                                () -> new ByteArrayInputStream(buffer, 0, uploadPartRequest.contentLength().intValue()),
                                partLength, "application/octet-stream")
                );

                // 完成请求需要按分片编号提交每个上传响应返回的 ETag。
                CompletedPart completedPart = CompletedPart.builder()
                        .partNumber(partNumber)
                        .eTag(uploadPartResponse.eTag())
                        .build();

                completedParts.add(completedPart);

                logger.debug("upload part success, path: {}, partNumber: {}, size: {}", path, partNumber, partLength);

                partNumber++;
                partLength = is.readNBytes(buffer, 0, buffer.length);
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

    /**
     * 尝试清理未完成的分片任务；失败仅记录日志，避免覆盖触发清理的原始异常。
     *
     * @param path     分片任务对应的完整对象 key
     * @param uploadId 创建分片任务时返回的 ID
     */
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

    /**
     * 通过 HeadObject 检查对象是否存在，不下载对象内容。
     * <p>
     * 仅将 NoSuchKey 或 HTTP 404 视为不存在；权限不足、网络错误等仍抛出异常。
     * 检查结果只代表该请求时刻的状态，不能保证后续下载时对象仍存在。
     *
     * @param path 对象的完整 key
     * @return 请求成功为 true，NoSuchKey 或 HTTP 404 为 false
     * @throws BaseException 检查失败且不是不存在错误
     */
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
     * 下载对象并在调用线程中将响应输入流交给回调处理。
     * <p>
     * 对象不存在时以 null 调用回调；成功获取响应时以输入流调用回调。
     * 回调结束后自动关闭响应流，因此必须在回调内完成读取，不应将流交给异步任务。
     * 回调自身抛出的异常不会被当作下载 404 处理，也不会触发第二次回调。
     *
     * @param path     对象的完整 key
     * @param consumer 响应流消费者，必须处理 null，不能为 null
     * @throws BaseException 下载、回调执行或响应流关闭失败
     */
    public static void getObject(String path, Consumer<ResponseInputStream<GetObjectResponse>> consumer) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(awsS3Prop.bucket)
                .key(path)
                .build();
        ResponseInputStream<GetObjectResponse> stream;
        try {
            stream = s3Client.getObject(request);
        } catch (NoSuchKeyException e) {
            stream = null;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                stream = null;
            } else {
                throw BaseException.get(e);
            }
        } catch (Exception e) {
            throw BaseException.get(e);
        }
        // 下载的异常判断已结束，回调异常统一传播，防止误判 404 后重复调用。
        try (ResponseInputStream<GetObjectResponse> responseInputStream = stream) {
            consumer.accept(responseInputStream);
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    /**
     * 下载整个对象到内存，适用于小对象。
     * <p>
     * 字节数组和 SDK 内部缓冲会占用堆内存，大对象应使用流回调或文件下载重载。
     *
     * @param path 对象的完整 key
     * @return 对象字节数组；空对象返回空数组，NoSuchKey 或 HTTP 404 返回 null
     * @throws BaseException 下载失败且不是不存在错误
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

    /**
     * 将对象直接下载到本地文件，不将完整对象加载到内存。
     * <p>
     * 使用 SDK 的文件响应转换器：目标文件必须不存在，父目录应由调用方提前创建。
     * 本方法不覆盖已有文件，也不自动创建父目录；这类文件系统错误会作为下载失败抛出。
     * 返回 false 时表示服务端报告对象不存在，调用方应检查返回值再使用目标文件。
     *
     * @param path         对象的完整 key
     * @param destFilePath 本地目标文件路径
     * @return 下载成功为 true，NoSuchKey 或 HTTP 404 为 false
     * @throws BaseException 下载或本地文件写入失败且不是不存在错误
     */
    public static boolean getObject(String path, String destFilePath) {
        GetObjectRequest request = GetObjectRequest.builder()
                .bucket(awsS3Prop.bucket)
                .key(path)
                .build();
        try {
            s3Client.getObject(request, Paths.get(destFilePath));
            return true;
        } catch (NoSuchKeyException _) {
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


}
