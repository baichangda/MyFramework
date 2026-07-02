package cn.bcd.app.businessProcess.backend.sys.service;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.spring.aws.s3.AwsS3Util;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;

@Service
public class FileService {
    public List<String> list(String dirPath, boolean recursive) {
        return AwsS3Util.listObjects(dirPath, recursive);

    }

    public void download(String path, OutputStream os) {
        AwsS3Util.getObject(path, os);
    }

    public void upload(String path, long contentLength, InputStream is) {
        AwsS3Util.putObject(is, contentLength, path);
    }

    public void upload(String path, Path file) {
        AwsS3Util.putObject(file, path);
    }

    public void upload(String dirPath, MultipartFile file) {
        String filename = file.getOriginalFilename();
        try (InputStream is = file.getInputStream()) {
            String path = dirPath == null ? filename : dirPath + "/" + filename;
            AwsS3Util.putObject(is, file.getSize(), path);
        } catch (Exception ex) {
            throw BaseException.get(ex);
        }
    }

    public void delete(String... paths) {
        try {
            AwsS3Util.removeObjects(paths);
        } catch (Exception ex) {
            throw BaseException.get(ex);
        }
    }
}
