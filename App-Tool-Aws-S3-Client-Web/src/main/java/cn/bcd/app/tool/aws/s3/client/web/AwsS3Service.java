package cn.bcd.app.tool.aws.s3.client.web;

import cn.bcd.lib.base.util.IOUtil;
import cn.bcd.lib.base.util.StringUtil;
import cn.bcd.lib.spring.aws.s3.AwsS3Prop;
import cn.bcd.lib.spring.aws.s3.AwsS3Util;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Service
public class AwsS3Service {

    static Logger logger = LoggerFactory.getLogger(AwsS3Service.class);

    @Autowired
    S3Client s3Client;

    @Autowired
    AwsS3Prop awsS3Prop;

    public String upload(MultipartFile file, String path) {
        String fileName = file.getOriginalFilename();
        if (path == null) {
            logger.info("path is null, use file name[{}]", fileName);
            path = fileName;
        }
        try {
            AwsS3Util.putObject(file.getInputStream(), file.getSize(), path);
            String res = "upload file[" + fileName + "] to path[" + path + "] succeed";
            logger.info(res);
            return res;
        } catch (IOException e) {
            logger.error("upload file[{}] to path[{}] error", fileName, path, e);
            return "upload file[" + fileName + "] to path[" + path + "] error:\n" + e.getMessage();
        }
    }

    public void download(String path, HttpServletResponse response) {
        AwsS3Util.getObject(path, is -> {
            try {
                if (is == null) {
                    response.setContentType(MediaType.TEXT_HTML_VALUE + ";utf-8");
                    response.getWriter().write(StringUtil.format("path[{}] not exists", path));
                } else {
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
                    IOUtil.copy(is, response.getOutputStream());
                    logger.info("download path[{}] succeed", path);
                }
            } catch (Exception ex) {
                logger.error("download path[{}] error", path, ex);
                try {
                    response.setContentType(MediaType.TEXT_HTML_VALUE + ";utf-8");
                    response.getWriter().write(StringUtil.format("download path[{}] error:\n{}", path, ex.getMessage()));
                } catch (IOException e) {
                    logger.error("error", e);
                }
            }
        });
    }
}
