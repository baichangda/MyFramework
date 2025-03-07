package cn.bcd.lib.microservice.common.fegin.user;

import org.springframework.cloud.openfeign.FeignClient;

import java.util.List;

@FeignClient(name = "business-process-backend")
public interface UserClient {
    List<String> getUserRoles(String username, String loginType);
    List<String> getUserPermissions(String username, String loginType);
}
