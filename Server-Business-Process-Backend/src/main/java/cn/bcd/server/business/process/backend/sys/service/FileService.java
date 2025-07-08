package cn.bcd.server.business.process.backend.sys.service;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.server.business.process.backend.base.support_minio.MinioProp;
import io.minio.*;
import io.minio.messages.DeleteObject;
import io.minio.messages.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
public class FileService {

    @Autowired
    public MinioClient minioClient;

    @Autowired
    public MinioProp minioProp;

    public List<String> list(String dirPath, boolean recursive) {
        try {
            if (dirPath == null) {
                dirPath = "";
            }
            if (recursive) {
                List<String> list = new ArrayList<>();
                List<String> dirList = new ArrayList<>();
                dirList.add(dirPath);
                for (int i = 0; i < dirList.size(); i++) {
                    String dir = dirList.get(i);
                    for (Result<Item> r : minioClient.listObjects(ListObjectsArgs.builder().bucket(minioProp.bucket).maxKeys(1000).prefix(dir).build())) {
                        Item item = r.get();
                        if (item.isDir()) {
                            dirList.add(item.objectName());
                        } else {
                            list.add(item.objectName());
                        }
                    }
                }
                return list;
            } else {
                List<String> list = new ArrayList<>();
                for (Result<Item> r : minioClient.listObjects(ListObjectsArgs.builder().bucket(minioProp.bucket).maxKeys(1000).prefix(dirPath).build())) {
                    Item item = r.get();
                    if (!item.isDir()) {
                        list.add(item.objectName());
                    }
                }
                return list;
            }
        } catch (Exception e) {
            throw BaseException.get(e);
        }
    }

    public void download(String path, OutputStream os) {
        try (GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder().bucket(minioProp.bucket).object(path).build())) {
            response.transferTo(os);
        } catch (Exception ex) {
            throw BaseException.get(ex);
        }
    }

    public void upload(String path, InputStream is) {
        try {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProp.bucket)
                    .object(path)
                    .stream(is, -1, 5 * 1024 * 1024)
                    .build());
        } catch (Exception ex) {
            throw BaseException.get(ex);
        }
    }

    public void upload(String path, Path file) {
        try (InputStream is = Files.newInputStream(file)) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProp.bucket)
                    .object(path)
                    .stream(is, is.available(), -1)
                    .build());
        } catch (Exception ex) {
            throw BaseException.get(ex);
        }
    }

    public void upload(String dirPath, MultipartFile file) {
        String filename = file.getOriginalFilename();
        try (InputStream is = file.getInputStream()) {
            minioClient.putObject(PutObjectArgs.builder()
                    .bucket(minioProp.bucket)
                    .object(dirPath == null ? filename : dirPath + "/" + filename)
                    .stream(is, file.getSize(), -1)
                    .build());
        } catch (Exception ex) {
            throw BaseException.get(ex);
        }
    }

    public void delete(String... paths) {
        try {
            minioClient.removeObjects(RemoveObjectsArgs.builder()
                    .bucket(minioProp.bucket)
                    .objects(Arrays.stream(paths).map(DeleteObject::new).toList())
                    .build());
        } catch (Exception ex) {
            throw BaseException.get(ex);
        }
    }
}
