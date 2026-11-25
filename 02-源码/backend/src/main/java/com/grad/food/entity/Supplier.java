package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
/** 供应单位。 */
@Data
@TableName("supplier")
public class Supplier {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private String contact;
    private String phone;
    private String licenseNo;
    private String category;
    private Integer status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
