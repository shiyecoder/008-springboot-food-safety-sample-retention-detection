package com.grad.food.controller;

import com.grad.food.common.R;
import com.grad.food.service.BaseDataService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 基础数据接口：分类 / 单位 / 档口 / 仓位 / 批次 + 下拉选项 + 柜位图。 */
@RestController
@RequestMapping("/api/base")
@RequiredArgsConstructor
public class BaseDataController {

    private final BaseDataService baseDataService;

    @GetMapping("/{type}/page")
    public R<?> page(@PathVariable String type,
                     @RequestParam(defaultValue = "1") long page,
                     @RequestParam(defaultValue = "10") long size,
                     @RequestParam(required = false) String keyword) {
        return R.ok(baseDataService.page(type, page, size, keyword));
    }

    @PostMapping("/{type}")
    public R<?> create(@PathVariable String type, @RequestBody Map<String, Object> body) {
        body.remove("id");
        baseDataService.save(type, body);
        return R.ok();
    }

    @PutMapping("/{type}/{id}")
    public R<?> update(@PathVariable String type, @PathVariable Long id, @RequestBody Map<String, Object> body) {
        body.put("id", id);
        baseDataService.save(type, body);
        return R.ok();
    }

    @DeleteMapping("/{type}/{id}")
    public R<?> remove(@PathVariable String type, @PathVariable Long id) {
        baseDataService.remove(type, id);
        return R.ok();
    }

    @GetMapping("/options")
    public R<?> options() {
        return R.ok(baseDataService.options());
    }

    @GetMapping("/cabinets")
    public R<?> cabinets() {
        return R.ok(baseDataService.cabinets());
    }
}
