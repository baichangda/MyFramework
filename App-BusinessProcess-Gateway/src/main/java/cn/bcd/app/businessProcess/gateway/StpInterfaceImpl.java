package cn.bcd.app.businessProcess.gateway;

import cn.dev33.satoken.stp.StpInterface;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StpInterfaceImpl implements StpInterface {

    private final CacheService cacheService;

    public StpInterfaceImpl(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return cacheService.getPermissionList(loginId.toString(), loginType);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return cacheService.getRoleList(loginId.toString(), loginType);
    }
}
