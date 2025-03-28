package cn.bcd.lib.microservice.common.fegin.user;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "business-process-backend")
public interface UserClient {
    @GetMapping("/api/sys/user/getUserRoles")
    List<String> getUserRoles(@RequestParam String username, @RequestParam String loginType);

    @GetMapping("/api/sys/user/getUserPermissions")
    List<String> getUserPermissions(@RequestParam String username, @RequestParam String loginType);
}
