package com.grad.food.controller;

import com.grad.food.common.R;
import com.grad.food.dto.UserSaveDTO;
import com.grad.food.service.SystemService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 系统管理接口：用户、角色、分配菜单、操作日志。注意没有菜单增删改接口。 */
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    @GetMapping("/users")
    public R<?> users(@RequestParam(defaultValue = "1") long page,
                      @RequestParam(defaultValue = "10") long size,
                      @RequestParam(required = false) String keyword,
                      @RequestParam(required = false) Long roleId,
                      @RequestParam(required = false) Long orgId,
                      @RequestParam(required = false) Integer status) {
        return R.ok(systemService.users(page, size, keyword, roleId, orgId, status));
    }

    @PostMapping("/users")
    public R<?> saveUser(@RequestBody UserSaveDTO dto) {
        systemService.saveUser(dto);
        return R.ok();
    }

    @PostMapping("/users/{id}/unlock")
    public R<?> unlock(@PathVariable Long id) {
        systemService.unlock(id);
        return R.ok();
    }

    @DeleteMapping("/users/{id}")
    public R<?> removeUser(@PathVariable Long id) {
        systemService.removeUser(id);
        return R.ok();
    }

    @GetMapping("/roles")
    public R<?> roles() {
        return R.ok(systemService.roles());
    }

    @PostMapping("/roles")
    public R<?> saveRole(@RequestBody Map<String, Object> body) {
        systemService.saveRole(body);
        return R.ok();
    }

    @DeleteMapping("/roles/{id}")
    public R<?> removeRole(@PathVariable Long id) {
        systemService.removeRole(id);
        return R.ok();
    }

    @GetMapping("/menus")
    public R<?> menus() {
        return R.ok(systemService.menuTree());
    }

    @GetMapping("/roles/{roleId}/menus")
    public R<List<Long>> roleMenus(@PathVariable Long roleId) {
        return R.ok(systemService.roleMenus(roleId));
    }

    @PostMapping("/roles/{roleId}/menus")
    public R<?> assign(@PathVariable Long roleId, @RequestBody Map<String, List<Long>> body) {
        systemService.assignMenus(roleId, body.get("menuIds"));
        return R.ok();
    }

    @GetMapping("/logs")
    public R<?> logs(@RequestParam(defaultValue = "1") long page,
                     @RequestParam(defaultValue = "10") long size,
                     @RequestParam(required = false) String keyword) {
        return R.ok(systemService.logs(page, size, keyword));
    }

    @GetMapping("/orgs")
    public R<?> orgs() {
        return R.ok(systemService.orgs());
    }
}
