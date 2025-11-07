package cn.bcd.app.businessProcess.backend.sys.service;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.spring.minio.MinioUtil;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Service
public class FileService {
    public List<String> list(String dirPath, boolean recursive) {
        return MinioUtil.listObjects(dirPath, recursive);

    }

    public void download(String path, OutputStream os) {
        MinioUtil.getObject(path, os);
    }

    public void upload(String path, InputStream is) {
        MinioUtil.putObject(is, path);
    }

    public void upload(String path, Path file) {
        try (InputStream is = Files.newInputStream(file)) {
            MinioUtil.putObject(is, path);
        } catch (Exception ex) {
            throw BaseException.get(ex);
        }
    }

    public void upload(String dirPath, MultipartFile file) {
        String filename = file.getOriginalFilename();
        try (InputStream is = file.getInputStream()) {
            String path = dirPath == null ? filename : dirPath + "/" + filename;
            MinioUtil.putObject(is, path);
        } catch (Exception ex) {
            throw BaseException.get(ex);
        }
    }

    public void delete(String... paths) {
        try {
            MinioUtil.removeObjects(paths);
        } catch (Exception ex) {
            throw BaseException.get(ex);
        }
    }
}
