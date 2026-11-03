package cn.bcd.app.bp.backend.user;

import cn.bcd.lib.base.result.Result;
import cn.bcd.lib.spring.auth.AuthenticatedUserContext;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/user")
@Tag(name = "用户-UserController")
public class UserController {
    private final UserService service;

    public UserController(UserService service) {
        this.service = service;
    }

    @GetMapping("/list")
    @Operation(summary = "查询用户列表")
    public Result<List<UserResponse>> list(
            @Valid @ParameterObject @ModelAttribute UserQueryRequest query) {
        return Result.success(service.listUser(query));
    }

    @GetMapping("/page")
    @Operation(summary = "查询用户分页")
    public Result<Page<UserResponse>> page(
            @Valid @ParameterObject @ModelAttribute UserQueryRequest query,
            @Parameter(description = "页码，从1开始") @RequestParam(defaultValue = "1") int pageNum,
            @Parameter(description = "每页条数") @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(service.pageUser(query, pageNum, pageSize));
    }

    @PostMapping("/save")
    @Operation(summary = "保存用户")
    public Result<?> save(@Valid @RequestBody UserSaveRequest request) {
        service.saveUser(request);
        return Result.success();
    }

    @DeleteMapping("/delete")
    @Operation(summary = "删除用户")
    public Result<?> delete(@RequestParam long[] ids) {
        service.deleteUser(ids);
        return Result.success();
    }
    /**
     * 重置密码
     *
     * @param userId
     * @return
     */
    @RequestMapping(value = "/resetPassword", method = RequestMethod.POST, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "重置密码")
    @ApiResponse(responseCode = "200", description = "重制密码结果")
    public Result<?> resetPassword(@Parameter(description = "用户主键") @RequestParam Long userId) {
        service.resetPassword(userId);
        return Result.successMessage("重置成功");
    }


    /**
     * 修改密码
     *
     * @param newPassword
     * @param oldPassword
     * @return
     */
    @RequestMapping(value = "/updatePassword", method = RequestMethod.POST, consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    @Operation(summary = "修改密码")
    @ApiResponse(responseCode = "200", description = "修改密码结果")
    public Result<?> updatePassword(
            @Parameter(description = "旧密码")
            @RequestParam String oldPassword,
            @Parameter(description = "新密码")
            @RequestParam String newPassword) {
        long userId = AuthenticatedUserContext.current()
                .orElseThrow(() -> new IllegalStateException("缺少当前用户信息")).id();
        boolean flag = service.updatePassword(userId, oldPassword, newPassword);
        if (flag) {
            return Result.successMessage("修改成功");
        } else {
            return Result.fail("密码错误");
        }
    }


    /**
     * 获取用户角色编码列表
     *
     * @return
     */
    @RequestMapping(value = "/getUserRoles", method = RequestMethod.GET)
    @Operation(summary = "获取用户角色编码列表")
    @ApiResponse(responseCode = "200", description = "用户角色编码列表")
    public Result<List<String>> getUserRoles(
            @Parameter(description = "用户名") @RequestParam String username,
            @Parameter(description = "登陆方式") @RequestParam String loginType
    ) {
        return Result.success(service.getUserRoles(username, loginType));
    }

    /**
     * 获取用户权限编码列表
     *
     * @return
     */
    @RequestMapping(value = "/getUserPermissions", method = RequestMethod.GET)
    @Operation(summary = "获取用户权限编码列表")
    @ApiResponse(responseCode = "200", description = "用户权限编码列表")
    public Result<List<String>> getUserPermissions(
            @Parameter(description = "用户名") @RequestParam String username,
            @Parameter(description = "登陆方式") @RequestParam String loginType
    ) {
        return Result.success(service.getUserPermissions(username, loginType));
    }

}
