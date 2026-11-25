package com.grad.food.dto;

import lombok.Data;

/** 人工修正入参：原因不少于 5 个字。 */
@Data
public class EvalAdjustDTO {
    private String grade;
    private String reason;
}
