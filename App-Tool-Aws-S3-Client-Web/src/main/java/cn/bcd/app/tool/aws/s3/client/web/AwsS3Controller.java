package cn.bcd.app.tool.aws.s3.client.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class AwsS3Controller {

    @Autowired
    AwsS3Service awsS3Service;

    @GetMapping(value = "/download")
    public void download(@RequestParam String path, HttpServletResponse response) {
        awsS3Service.download(path, response);
    }

    @PostMapping(value = "/upload")
    public String upload(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String path) {
        return awsS3Service.upload(file,path);
    }
}
