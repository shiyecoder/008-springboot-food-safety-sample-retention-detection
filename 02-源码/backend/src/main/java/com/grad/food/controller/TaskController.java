package com.grad.food.controller;

import com.grad.food.common.R;
import com.grad.food.service.TaskService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 留样任务接口：列表、今日待办、按批次生成、统计。 */
@RestController
@RequestMapping("/api/task")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") long page,
                     @RequestParam(defaultValue = "10") long size,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) String meal,
                     @RequestParam(required = false) String date,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) String tab) {
        return R.ok(taskService.page(page, size, status, meal, date, keyword, tab));
    }

    @GetMapping("/today")
    public R<?> today() {
        return R.ok(taskService.today());
    }

    @GetMapping("/stats")
    public R<?> stats() {
        return R.ok(taskService.stats());
    }

    /** 按批次生成任务；items 为空时按启用规则全量生成 */
    @PostMapping("/generate")
    public R<?> generate(@RequestBody Map<String, Object> body) {
        Long batchId = Long.valueOf(String.valueOf(body.get("batchId")));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");
        return R.ok(Map.of("created", taskService.generate(batchId, items)));
    }
}
