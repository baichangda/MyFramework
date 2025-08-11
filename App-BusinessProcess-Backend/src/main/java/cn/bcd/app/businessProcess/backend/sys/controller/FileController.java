package cn.bcd.app.businessProcess.backend.sys.controller;

import cn.bcd.lib.base.common.Result;
import cn.bcd.lib.base.exception.BaseException;
import cn.bcd.app.businessProcess.backend.base.util.HttpResponseUtil;
import cn.bcd.app.businessProcess.backend.sys.service.FileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/sys/file")
@Tag(name = "文件-FileController")
public class FileController {

    @Autowired
    FileService fileService;

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @Operation(summary = "查询文件列表")
    @ApiResponse(responseCode = "200", description = "文件列表结果")
    public Result<List<String>> get(
            @Parameter(description = "文件夹路径") @RequestParam(required = false) String dirPath,
            @Parameter(description = "是否递归查询") @RequestParam(required = true, defaultValue = "true") boolean recursive
    ) {
        return Result.success(fileService.list(dirPath, recursive));
    }

    @RequestMapping(value = "/upload", method = RequestMethod.POST, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "上传文件")
    @ApiResponse(responseCode = "200", description = "上传结果")
    public Result<?> download(@Parameter(description = "文件夹路径") @RequestParam(required = false) String dirPath,
                              @Parameter(description = "文件") @RequestParam(required = true) MultipartFile file) {
        fileService.upload(dirPath, file);
        return Result.success();
    }

    @RequestMapping(value = "/download", method = RequestMethod.GET)
    @Operation(summary = "下载文件")
    @ApiResponse(responseCode = "200", description = "文件流")
    public void download(@Parameter(description = "文件路径") @RequestParam(required = true) String path,
                         HttpServletResponse response) {
        try {
            HttpResponseUtil.setDownloadResponse(path.substring(path.lastIndexOf("/") + 1), response);
            fileService.download(path, response.getOutputStream());
        } catch (IOException e) {
            throw BaseException.get(e);
        }
    }

    @RequestMapping(value = "/delete", method = RequestMethod.GET)
    @Operation(summary = "删除文件")
    @ApiResponse(responseCode = "200", description = "删除结果")
    public void delete(@Parameter(description = "文件路径") @RequestParam(required = true) String path) {
        fileService.delete(path);
    }
}
