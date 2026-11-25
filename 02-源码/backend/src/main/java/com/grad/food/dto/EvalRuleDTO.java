package com.grad.food.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/** 校验规则保存入参：权重合计必须为 100。 */
@Data
public class EvalRuleDTO {
    private Map<String, Integer> weights;
    private Map<String, Integer> thresholds;
    private LocalDateTime effectiveTime;
    private String remark;
}
