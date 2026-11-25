package com.grad.food.controller;

import com.grad.food.common.R;
import com.grad.food.dto.LoginDTO;
import com.grad.food.dto.PasswordDTO;
import com.grad.food.dto.ProfileDTO;
import com.grad.food.entity.SysMessage;
import com.grad.food.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 认证与个人中心接口。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/login")
    public R<?> login(@Valid @RequestBody LoginDTO dto, HttpServletRequest req) {
        return R.ok(authService.login(dto, req.getRemoteAddr()));
    }

    @GetMapping("/auth/info")
    public R<?> info() {
        return R.ok(authService.info());
    }

    @GetMapping("/auth/menus")
    public R<?> menus() {
        return R.ok(authService.menus());
    }

    @PutMapping("/profile")
    public R<?> profile(@RequestBody ProfileDTO dto) {
        authService.updateProfile(dto);
        return R.ok();
    }

    @PutMapping("/profile/password")
    public R<?> password(@Valid @RequestBody PasswordDTO dto) {
        authService.changePassword(dto);
        return R.ok();
    }

    @GetMapping("/messages")
    public R<List<SysMessage>> messages() {
        return R.ok(authService.messages());
    }

    @GetMapping("/messages/unread")
    public R<Long> unread() {
        return R.ok(authService.unread());
    }

    @PostMapping("/messages/read-all")
    public R<?> readAll() {
        authService.readAll();
        return R.ok();
    }
}
