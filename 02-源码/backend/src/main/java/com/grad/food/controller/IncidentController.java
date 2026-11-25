package com.grad.food.controller;

import com.grad.food.common.R;
import com.grad.food.service.IncidentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 不合格事件接口。 */
@RestController
@RequestMapping("/api/incident")
@RequiredArgsConstructor
public class IncidentController {

    private final IncidentService incidentService;

    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") long page,
                     @RequestParam(defaultValue = "10") long size,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) String severity,
                     @RequestParam(required = false) String keyword) {
        return R.ok(incidentService.page(page, size, status, severity, keyword));
    }

    @GetMapping("/stats")
    public R<?> stats() {
        return R.ok(incidentService.stats());
    }

    @GetMapping("/{id}")
    public R<?> detail(@PathVariable Long id) {
        return R.ok(incidentService.detail(id));
    }

    @PostMapping("/{id}/follow")
    public R<?> follow(@PathVariable Long id, @RequestBody Map<String, String> body) {
        incidentService.follow(id, body.get("conclusion"));
        return R.ok();
    }

    @PostMapping("/{id}/close")
    public R<?> close(@PathVariable Long id, @RequestBody Map<String, String> body) {
        incidentService.close(id, body.get("conclusion"));
        return R.ok();
    }
}
