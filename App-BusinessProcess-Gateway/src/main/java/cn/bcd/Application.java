package cn.bcd;

import cn.bcd.lib.base.common.Initializable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.event.ContextRefreshedEvent;

import java.util.List;

@SpringBootApplication(scanBasePackages = {"cn.bcd"})
@EnableAspectJAutoProxy(proxyTargetClass = true, exposeProxy = true)
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "cn.bcd")
public class Application implements ApplicationListener<ContextRefreshedEvent> {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }

    @Autowired
    List<Initializable> initList;

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        //初始化组件
        Initializable.initByOrder(initList);
    }
}