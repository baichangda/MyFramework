package cn.bcd.app.businessProcess.backend.base.util;

import cn.bcd.lib.base.util.IOUtil;
import cn.bcd.lib.base.util.StringUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

public class HttpResponseUtil {
    /**
     * 响应文件
     *
     * @param fileName
     * @param response
     * @param is
     */
    public static void responseFile(String fileName, HttpServletResponse response, InputStream is) throws IOException {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        String encode = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + encode);
        IOUtil.copy(is, response.getOutputStream());
    }

    /**
     * 响应文件
     *
     * @param fileName
     * @param response
     * @param bytes
     */
    public static void responseFile(String fileName, HttpServletResponse response, byte[] bytes) throws IOException {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        String encode = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
        response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + encode);
        response.getOutputStream().write(bytes);
    }

    /**
     * 响应文本
     *
     * @param response
     * @param content
     * @param args
     */
    public static void responseText(HttpServletResponse response, String content, Object... args) throws IOException {
        response.setContentType(MediaType.TEXT_HTML_VALUE + ";utf-8");
        response.getWriter().write(StringUtil.format(content, args));
    }
}
