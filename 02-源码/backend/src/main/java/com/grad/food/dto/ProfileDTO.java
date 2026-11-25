package com.grad.food.dto;

import lombok.Data;

/** 个人资料修改入参。 */
@Data
public class ProfileDTO {
    private String realName;
    private String phone;
    private String email;
    private String avatar;
}
