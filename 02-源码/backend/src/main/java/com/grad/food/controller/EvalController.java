package com.grad.food.controller;

import com.grad.food.common.R;
import com.grad.food.dto.EvalAdjustDTO;
import com.grad.food.dto.EvalRuleDTO;
import com.grad.food.service.EvalService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;

/** 创新点：留样覆盖率与到期处置校验接口（规则配置 / 计算 / 解释 / 人工修正 / 效果统计）。 */
@RestController
@RequestMapping("/api/eval")
@RequiredArgsConstructor
public class EvalController {

    private final EvalService evalService;

    @GetMapping("/rule")
    public R<?> rule() {
        return R.ok(evalService.ruleView());
    }

    @GetMapping("/rule/versions")
    public R<?> versions() {
        return R.ok(evalService.versions());
    }

    @PutMapping("/rule")
    public R<?> saveRule(@RequestBody EvalRuleDTO dto) {
        return R.ok(evalService.saveRule(dto));
    }

    /** 执行校验并返回完整解释（推荐结果、主要因素、未满足条件、可改进项） */
    @PostMapping("/evaluate")
    public R<?> evaluate(@RequestBody(required = false) Map<String, String> body) {
        LocalDate date = body == null || body.get("bizDate") == null || body.get("bizDate").isBlank()
                ? LocalDate.now() : LocalDate.parse(body.get("bizDate"));
        return R.ok(evalService.evaluate(date));
    }

    @GetMapping("/latest")
    public R<?> latest() {
        return R.ok(evalService.latest());
    }

    @GetMapping("/history")
    public R<?> history() {
        return R.ok(evalService.history());
    }

    @PostMapping("/scores/{id}/adjust")
    public R<?> adjust(@PathVariable Long id, @RequestBody EvalAdjustDTO dto) {
        evalService.adjust(id, dto.getGrade(), dto.getReason());
        return R.ok();
    }

    @PostMapping("/scores/{id}/adopt")
    public R<?> adopt(@PathVariable Long id) {
        evalService.adopt(id);
        return R.ok();
    }

    @GetMapping("/stats")
    public R<?> stats() {
        return R.ok(evalService.stats());
    }
}
