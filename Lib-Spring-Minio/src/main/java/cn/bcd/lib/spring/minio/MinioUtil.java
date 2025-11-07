package cn.bcd.lib.spring.minio;

import cn.bcd.lib.base.exception.BaseException;
import io.minio.*;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "lib.spring.minio.endpoint")
public class MinioUtil {

    static final Logger logger = LoggerFactory.getLogger(MinioUtil.class);

    static MinioProp minioProp;
    static MinioClient minioClient;

    public MinioUtil(MinioProp minioProp, MinioClient minioClient) {
        MinioUtil.minioProp = minioProp;
        MinioUtil.minioClient = minioClient;
    }

    public static List<String> listObjects(String prefix, boolean recursive) {
        try {
            List<String> keyList = new ArrayList<>();
            ListObjectsArgs.Builder builder = ListObjectsArgs.builder()
                    .bucket(minioProp.bucket)
                    .recursive(recursive);
            if (prefix != null) {
                builder.prefix(prefix);
            }
            Iterable<Result<Item>> results = minioClient.listObjects(builder.build());
            for (Result<Item> result : results) {
                Item item = result.get();
                if (item.isDir() || item.isDeleteMarker()) {
                    continue;
                }
                keyList.add(item.objectName());
            }
            return keyList;
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    public static void removeObjects(String... paths) {
        try {
            List<DeleteObject> deleteObjects = Arrays.stream(paths).map(DeleteObject::new).toList();
            RemoveObjectsArgs removeObjectsArgs = RemoveObjectsArgs.builder().bucket(minioProp.bucket).objects(deleteObjects).build();
            minioClient.removeObjects(removeObjectsArgs);
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    public static void putObject(InputStream is, String path) {
        try {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(minioProp.bucket)
                    .object(path)
                    .stream(is, is.available(), 5 * 1024 * 1024)
                    .build();
            minioClient.putObject(args);
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    public static void getObject(String path, OutputStream os) {
        try {
            GetObjectArgs args = GetObjectArgs.builder()
                    .bucket(minioProp.bucket)
                    .object(path)
                    .build();
            GetObjectResponse resp = minioClient.getObject(args);
            resp.transferTo(os);
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }
}
