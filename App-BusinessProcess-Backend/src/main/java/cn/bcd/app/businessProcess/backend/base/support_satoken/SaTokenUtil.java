package cn.bcd.app.businessProcess.backend.base.support_satoken;

import cn.bcd.lib.base.common.Const;
import cn.bcd.lib.base.json.JsonUtil;
import cn.bcd.lib.spring.cloud.common.fegin.user.AuthUser;
import cn.bcd.app.businessProcess.backend.sys.bean.UserBean;
import cn.bcd.app.businessProcess.backend.sys.service.CacheService;
import cn.bcd.app.businessProcess.backend.sys.service.UserService;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class SaTokenUtil {

    static Logger logger = LoggerFactory.getLogger(SaTokenUtil.class);

    static CacheService cacheService;

    static UserService userService;

    @Autowired
    public void setCacheService(CacheService cacheService) {
        SaTokenUtil.cacheService = cacheService;
    }

    public static UserBean getLoginUser_cache() {
        try {
            final String loginIdAsString = StpUtil.getLoginIdAsString();
            return Optional.ofNullable(loginIdAsString).map(e -> cacheService.getUser(e)).orElse(null);
        } catch (SaTokenException ex) {
            return null;
        }
    }

    public static UserBean getLoginUser() {
        try {
            final String loginIdAsString = StpUtil.getLoginIdAsString();
            return Optional.ofNullable(loginIdAsString).map(e -> userService.getUser(e)).orElse(null);
        } catch (SaTokenException ex) {
            return null;
        }
    }

    public static AuthUser getLoginUserFromRequest(HttpServletRequest request) {
        return Optional.ofNullable(request.getHeader(Const.request_header_authUser)).map(e -> {
            try {
                return JsonUtil.OBJECT_MAPPER.readValue(e, AuthUser.class);
            } catch (JsonProcessingException ex) {
                logger.error("error", ex);
                return null;
            }
        }).orElse(null);
    }
}
