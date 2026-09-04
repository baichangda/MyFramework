package cn.bcd.app.businessProcess.backend.sys.service;

import cn.bcd.app.businessProcess.backend.base.util.HttpResponseUtil;
import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.lib.spring.aws.s3.AwsS3Util;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

@Service
public class FileService {
    Logger logger = LoggerFactory.getLogger(FileService.class);

    public List<String> list(String dirPath, boolean recursive) {
        return AwsS3Util.listObjects(dirPath, recursive);

    }

    public void download(String path, HttpServletResponse response) {
        AwsS3Util.getObject(path, is -> {
            try {
                if (is == null) {
                    HttpResponseUtil.responseText(response, "path[{}] not exists", path);
                } else {
                    HttpResponseUtil.responseFile(path.substring(path.lastIndexOf("/") + 1), response, is);
                }
            } catch (Exception ex) {
                logger.error("download path[{}] error", path, ex);
                try {
                    HttpResponseUtil.responseText(response, "download path[{}] error:\n{}", path, ex.getMessage());
                } catch (IOException e) {
                    logger.error("error", e);
                }
            }
        });
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
