package cn.bcd.app.tool.minio.client.server;

import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class MinioService {

    static Logger logger = LoggerFactory.getLogger(MinioService.class);

    @Autowired
    MinioClient minioClient;

    @Autowired
    MinioProp minioProp;

    public String upload(MultipartFile file, String path) {
        String fileName = file.getOriginalFilename();
        if (path == null) {
            logger.info("path is null, use file name[{}]", fileName);
            path = fileName;
        }
        try {
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(minioProp.bucket)
                    .object(path)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM_VALUE)
                    .stream(file.getInputStream(), file.getSize(), -1)
                    .build();
            minioClient.putObject(args);
            String res = "upload file[" + fileName + "] to path[" + path + "] succeed";
            logger.info(res);
            return res;
        } catch (Exception e) {
            logger.error("upload file[{}] to path[{}] error", fileName, path, e);
            return "upload file[" + fileName + "] to path[" + path + "] error:\n" + e.getMessage();
        }
    }

    public void download(String path, HttpServletResponse response) {
        try {
            String fileName;
            int lastIndexOf = path.lastIndexOf("/");
            if (lastIndexOf == -1) {
                fileName = path;
            } else {
                fileName = path.substring(lastIndexOf + 1);
            }
            response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
            String encode = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + encode);
            GetObjectArgs args = GetObjectArgs.builder()
                    .bucket(minioProp.bucket)
                    .object(path)
                    .build();
            GetObjectResponse resp = minioClient.getObject(args);
            resp.transferTo(response.getOutputStream());
            logger.info("download path[{}] succeed", path);
        } catch (Exception e) {
            logger.error("download path[{}] error", path, e);
        }
    }
}
