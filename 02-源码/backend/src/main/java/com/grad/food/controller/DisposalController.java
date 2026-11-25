package com.grad.food.controller;

import com.grad.food.common.R;
import com.grad.food.dto.DisposeDTO;
import com.grad.food.dto.ReviewDTO;
import com.grad.food.service.DisposalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/** 到期处置与销毁接口。 */
@RestController
@RequestMapping("/api/disposal")
@RequiredArgsConstructor
public class DisposalController {

    private final DisposalService disposalService;

    @GetMapping("/pending")
    public R<?> pending(@RequestParam(defaultValue = "1") long page,
                        @RequestParam(defaultValue = "10") long size,
                        @RequestParam(required = false) String tab,
                        @RequestParam(required = false) String keyword) {
        return R.ok(disposalService.pending(page, size, tab, keyword));
    }

    @GetMapping("/records")
    public R<?> records(@RequestParam(defaultValue = "1") long page,
                        @RequestParam(defaultValue = "10") long size,
                        @RequestParam(required = false) String status) {
        return R.ok(disposalService.records(page, size, status));
    }

    @GetMapping("/stats")
    public R<?> stats() {
        return R.ok(disposalService.stats());
    }

    @PostMapping
    public R<Long> create(@Valid @RequestBody DisposeDTO dto) {
        return R.ok(disposalService.create(dto));
    }

    @PostMapping("/{id}/approve")
    public R<?> approve(@PathVariable Long id, @RequestBody ReviewDTO dto) {
        disposalService.approve(id, Boolean.TRUE.equals(dto.getPass()), dto.getRemark());
        return R.ok();
    }
}
