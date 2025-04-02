package cn.bcd.lib.microservice.common.fegin.user;

import cn.bcd.lib.base.common.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@FeignClient(name = "business-process-backend")
public interface UserClient {
    @GetMapping("/api/sys/user/getUserRoles")
    Result<List<String>> getUserRoles(@RequestParam String username, @RequestParam String loginType);

    @GetMapping("/api/sys/user/getUserPermissions")
    Result<List<String>> getUserPermissions(@RequestParam String username, @RequestParam String loginType);

    @GetMapping("/api/sys/user/getAuthUser")
    Result<AuthUser> getAuthUser(@RequestParam String username);
}
