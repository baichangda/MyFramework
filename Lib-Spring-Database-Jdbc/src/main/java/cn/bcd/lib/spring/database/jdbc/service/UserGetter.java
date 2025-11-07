package cn.bcd.lib.spring.database.jdbc.service;

import cn.bcd.lib.spring.database.jdbc.bean.UserInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component("UserGetter-jdbc")
public class UserGetter {

    static Supplier<UserInterface> supplier;

    @Autowired(required = false)
    public void setConsumer(Supplier<UserInterface> supplier) {
        UserGetter.supplier = supplier;
    }

    public static UserInterface getUser() {
        return supplier == null ? null : supplier.get();
    }
}
