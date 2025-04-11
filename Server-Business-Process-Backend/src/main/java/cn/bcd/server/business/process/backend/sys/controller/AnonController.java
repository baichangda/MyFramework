package cn.bcd.server.business.process.backend.sys.controller;

import cn.bcd.lib.base.common.Result;
import cn.bcd.server.business.process.backend.sys.keys.KeysConst;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;


/**
 * Created by Administrator on 2017/6/15.
 */
@RestController
@RequestMapping("/api/anon")
@Tag(name = "匿名-AnonController")
public class AnonController{
    Logger logger = LoggerFactory.getLogger(AnonController.class);

    @RequestMapping(value = "/getPublicKey", method = RequestMethod.GET)
    @Operation(summary = "获取公钥")
    @ApiResponse(responseCode = "200", description = "公钥信息")
    public Result<String> getPublicKey() {
        return Result.success(KeysConst.PUBLIC_KEY_BASE64);
    }

    @RequestMapping(value = "/getCookie", method = RequestMethod.GET)
    @Operation(summary = "获取cookie")
    @ApiResponse(responseCode = "200", description = "当前浏览器的cookie")
    public Result<String> getCookie() {
        final SaSession session = StpUtil.getSession();
        String cookie = Optional.ofNullable(session).map(SaSession::getId).orElse("");
        return Result.success(cookie);
    }

}
