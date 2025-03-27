package cn.bcd.lib.database.mongo.service;

import cn.bcd.lib.database.mongo.bean.UserInterface;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component("UserGetter-mongo")
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
