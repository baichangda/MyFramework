package cn.bcd.app.business.process.backend.base.util;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class HttpResponseUtil {
    /**
     * 设置下载响应
     * @param fileName
     * @param response
     */
    public static void setDownloadResponse(String fileName, HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        String encode = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + encode);
    }
}
