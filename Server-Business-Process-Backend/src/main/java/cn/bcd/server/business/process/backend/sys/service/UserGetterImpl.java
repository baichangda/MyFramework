package cn.bcd.server.business.process.backend.sys.service;

import cn.bcd.lib.database.jdbc.bean.UserInterface;
import cn.bcd.server.business.process.backend.base.support_satoken.SaTokenUtil;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class UserGetterImpl implements Supplier<UserInterface> {
    @Override
    public UserInterface get() {
        return SaTokenUtil.getLoginUser_cache();
    }
}
