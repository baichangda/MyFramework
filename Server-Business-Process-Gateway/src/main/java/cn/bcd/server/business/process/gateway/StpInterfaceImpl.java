package cn.bcd.server.business.process.gateway;

import cn.bcd.lib.microservice.common.bean.AuthUser;
import cn.dev33.satoken.model.wrapperInfo.SaDisableWrapperInfo;
import cn.dev33.satoken.stp.StpInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class StpInterfaceImpl implements StpInterface {

    @Autowired
    CacheService cacheService;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return cacheService.getPermissionList(loginId.toString(), loginType);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return cacheService.getRoleList(loginId.toString(), loginType);
    }
}
