package com.grad.food.controller;

import com.grad.food.common.R;
import com.grad.food.dto.InspectSaveDTO;
import com.grad.food.dto.ReviewDTO;
import com.grad.food.service.InspectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 检测登记与复核接口。 */
@RestController
@RequestMapping("/api/inspection")
@RequiredArgsConstructor
public class InspectionController {

    private final InspectionService inspectionService;

    @GetMapping("/page")
    public R<?> page(@RequestParam(defaultValue = "1") long page,
                     @RequestParam(defaultValue = "10") long size,
                     @RequestParam(required = false) String keyword,
                     @RequestParam(required = false) String type,
                     @RequestParam(required = false) String result,
                     @RequestParam(required = false) String status,
                     @RequestParam(required = false) String tab,
                     @RequestParam(required = false) String dateStart,
                     @RequestParam(required = false) String dateEnd) {
        return R.ok(inspectionService.page(page, size, keyword, type, result, status, tab, dateStart, dateEnd));
    }

    @GetMapping("/stats")
    public R<?> stats() {
        return R.ok(inspectionService.stats());
    }

    @GetMapping("/{id}")
    public R<?> detail(@PathVariable Long id) {
        return R.ok(inspectionService.detail(id));
    }

    @PostMapping
    public R<Long> create(@Valid @RequestBody InspectSaveDTO dto) {
        return R.ok(inspectionService.create(dto));
    }

    @PostMapping("/{id}/submit")
    public R<?> submit(@PathVariable Long id, @RequestBody InspectSaveDTO dto) {
        inspectionService.submit(id, dto);
        return R.ok();
    }

    @PostMapping("/{id}/review")
    public R<?> review(@PathVariable Long id, @RequestBody ReviewDTO dto) {
        Long incidentId = inspectionService.review(id, Boolean.TRUE.equals(dto.getPass()), dto.getRemark());
        return R.ok(incidentId == null ? null : java.util.Map.of("incidentId", incidentId));
    }
}
