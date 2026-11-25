package com.grad.food.controller;

import com.grad.food.common.R;
import com.grad.food.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 食品安全看板接口。 */
@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public R<?> overview(@RequestParam(required = false) String range) {
        return R.ok(dashboardService.overview(range));
    }
}
