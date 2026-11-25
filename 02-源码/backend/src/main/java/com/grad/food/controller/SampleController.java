package com.grad.food.controller;

import com.grad.food.common.R;
import com.grad.food.dto.ReviewDTO;
import com.grad.food.dto.SampleSaveDTO;
import com.grad.food.dto.TransitionDTO;
import com.grad.food.service.DashboardService;
import com.grad.food.service.SampleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/** 留样主档接口，路径沿用需求文档的 /api/food-sample 约定。 */
@RestController
@RequestMapping("/api/food-sample")
@RequiredArgsConstructor
public class SampleController {

    private final SampleService sampleService;
    private final DashboardService dashboardService;

    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") long page,
                     @RequestParam(defaultValue = "10") long size,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) String meal,
                     @RequestParam(required = false) String dateStart,
                     @RequestParam(required = false) String dateEnd,
                     @RequestParam(required = false) Long locationId,
                     @RequestParam(required = false) String expireState,
                     @RequestParam(required = false) String inspectResult,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) String tab) {
        return R.ok(sampleService.page(page, size, keyword, meal, dateStart, dateEnd, locationId,
                expireState, inspectResult, status, tab));
    }

    /** 统计看板（文档约定路径），与 /api/dashboard 等价 */
    @GetMapping("/dashboard")
    public R<?> dashboard(@RequestParam(required = false) String range) {
        return R.ok(dashboardService.overview(range));
    }

    @GetMapping("/{id}")
    public R<Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(sampleService.detail(id));
    }

    @PostMapping
    public R<Long> create(@Valid @RequestBody SampleSaveDTO dto) {
        return R.ok(sampleService.create(dto));
    }

    @PutMapping("/{id}")
    public R<?> update(@PathVariable Long id, @RequestBody SampleSaveDTO dto) {
        sampleService.update(id, dto);
        return R.ok();
    }

    @PostMapping("/{id}/review")
    public R<?> review(@PathVariable Long id, @RequestBody ReviewDTO dto) {
        sampleService.review(id, Boolean.TRUE.equals(dto.getPass()), dto.getRemark());
        return R.ok();
    }

    @PostMapping("/{id}/transition")
    public R<?> transition(@PathVariable Long id, @RequestBody TransitionDTO dto) {
        sampleService.transition(id, dto.getTarget(), dto.getReason());
        return R.ok();
    }

    @DeleteMapping("/{id}")
    public R<?> remove(@PathVariable Long id) {
        sampleService.remove(id);
        return R.ok();
    }

    @GetMapping("/{id}/attachments")
    public R<?> attachments(@PathVariable Long id) {
        return R.ok(sampleService.attachments(id));
    }

    @PostMapping("/{id}/attachments")
    public R<?> addAttachment(@PathVariable Long id, @RequestBody Map<String, String> body) {
        sampleService.addAttachment(id, body.getOrDefault("fileName", "附件"), body.get("path"));
        return R.ok();
    }

    @DeleteMapping("/attachments/{attachmentId}")
    public R<?> removeAttachment(@PathVariable Long attachmentId) {
        sampleService.removeAttachment(attachmentId);
        return R.ok();
    }
}
