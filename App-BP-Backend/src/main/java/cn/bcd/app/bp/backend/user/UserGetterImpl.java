package cn.bcd.app.bp.backend.user;

import cn.bcd.lib.spring.auth.AuthenticatedUserContext;
import cn.bcd.lib.spring.database.jdbc.bean.UserInterface;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
public class UserGetterImpl implements Supplier<UserInterface> {
    @Override
    public UserInterface get() {
        return AuthenticatedUserContext.current().map(user -> {
            UserBean userBean = new UserBean();
            userBean.id = user.id();
            userBean.username = user.username();
            return userBean;
        }).orElse(null);
    }
}
