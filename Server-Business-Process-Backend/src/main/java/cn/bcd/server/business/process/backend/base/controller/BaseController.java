package cn.bcd.server.business.process.backend.base.controller;

import cn.bcd.lib.base.util.DateZoneUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * Created by Administrator on 2017/4/11.
 */
public class BaseController {

    protected Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 响应文件流之前设置response
     *
     * @param fileName
     * @param response
     */
    protected void doBeforeResponseFile(String fileName, HttpServletResponse response) {
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        String encode = URLEncoder.encode(fileName,StandardCharsets.UTF_8);
        response.addHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment;filename=" + encode);
    }


    /**
     * 文件名字带上 '-时间数字'
     * 例如:
     * name.xlsx
     * name-20181111112359.xlsx
     *
     * @param fileName
     * @return
     */
    protected String toDateFileName(String fileName) {
        int index = fileName.lastIndexOf('.');
        long dateNum = Long.parseLong(DateZoneUtil.dateToString_second(new Date()));
        if (index == -1) {
            return fileName + "-" + dateNum;
        } else {
            return fileName.substring(0, index) + "-" + dateNum + fileName.substring(index);
        }
    }

}
