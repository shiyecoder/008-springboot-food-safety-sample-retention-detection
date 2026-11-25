package com.grad.food.dto;

import lombok.Data;

/** 通用状态流转入参，reason 用于驳回/取消等可追溯操作。 */
@Data
public class TransitionDTO {
    private String target;
    private String reason;
}
