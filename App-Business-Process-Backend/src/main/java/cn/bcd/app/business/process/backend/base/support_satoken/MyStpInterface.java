package cn.bcd.app.business.process.backend.base.support_satoken;

import cn.bcd.app.business.process.backend.sys.service.CacheService;
import cn.dev33.satoken.stp.StpInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class MyStpInterface implements StpInterface {


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
