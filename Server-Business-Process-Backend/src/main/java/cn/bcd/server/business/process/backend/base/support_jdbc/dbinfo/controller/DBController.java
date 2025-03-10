package cn.bcd.server.business.process.backend.base.support_jdbc.dbinfo.controller;

import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.server.business.process.backend.base.controller.BaseController;
import cn.bcd.server.business.process.backend.base.support_jdbc.dbinfo.service.DBService;
import com.alibaba.excel.EasyExcel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/db")
@Tag(name = "数据库-DBController")
public class DBController extends BaseController {

    Logger logger = LoggerFactory.getLogger(DBController.class);

    @Autowired(required = false)
    private DBService dbService;

    @RequestMapping(value = "/exportSpringDBDesignerExcel", method = RequestMethod.GET)
    @Operation(summary = "导出spring数据库设计")
    @ApiResponse(responseCode = "200", description = "导出结果")
    public void exportSpringDBDesignerExcel(@Parameter(description = "数据库名称、默认为spring配置数据库") @RequestParam(required = false) String dbName, HttpServletResponse response) {
        try {
            dbService.exportSpringDBDesignerExcel(dbName, response.getOutputStream(), () -> {
                String fileName = "db-" + dbName + ".xlsx";
                doBeforeResponseFile(fileName, response);
            });
        } catch (IOException e) {
            throw BaseException.get(e);
        }
    }

    @RequestMapping(value = "/exportDBDesignerExcel", method = RequestMethod.GET)
    @Operation(summary = "导出数据库设计")
    @ApiResponse(responseCode = "200", description = "导出结果")
    public void exportDBDesignerExcel(@Parameter(description = "数据库url(例如:127.0.0.1:3306)") @RequestParam String url, @Parameter(description = "数据库用户名") @RequestParam String username, @Parameter(description = "数据库密码") @RequestParam String password, @Parameter(description = "数据库名称") @RequestParam String dbName, HttpServletResponse response) {
        try {
            dbService.exportDBDesignerExcel(url, username, password, dbName, response.getOutputStream(), () -> {
                String fileName = "db-" + dbName + ".xlsx";
                doBeforeResponseFile(fileName, response);
            });
        } catch (IOException e) {
            throw BaseException.get(e);
        }
    }

    @RequestMapping(value = "/test", method = RequestMethod.POST, consumes = "multipart/form-data")
    @Operation(summary = "文件上传示例", description = """
            必须设置 consumes = "multipart/form-data"
            否则当存在MultipartFile参数、同时需要导出xlsx文件时候、此时会导致导出的xlsx损坏无法打开
            """)
    @ApiResponse(responseCode = "200", description = "导出结果")
    public void testExportBug(@Parameter(description = "文件") @RequestParam MultipartFile file, HttpServletResponse response) {
        try {
            doBeforeResponseFile("test.xlsx", response);
            EasyExcel.write(response.getOutputStream()).sheet("test").doWrite(List.of(
                    List.of("123", "456"),
                    List.of("abc", "def")
            ));

        } catch (IOException e) {
            throw BaseException.get(e);
        }
    }
}
