package cn.bcd.app.tool.minio.client.server;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class MinioController {

    @Autowired
    MinioService minioService;

    @GetMapping(value = "/download")
    public void download(@RequestParam String path, HttpServletResponse response) {
        minioService.download(path, response);
    }

    @PostMapping(value = "/upload")
    public String upload(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String path) {
        return minioService.upload(file,path);
    }
}
