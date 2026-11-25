package com.grad.food.dto;

import lombok.Data;

/** 复核入参：pass=true 通过转保留中，false 驳回。 */
@Data
public class ReviewDTO {
    private Boolean pass;
    private String remark;
}
