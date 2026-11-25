package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDate;import java.time.LocalDateTime;import java.math.BigDecimal;
/** 食品分类与留样标准（最低重量、存储条件、保留时长）。 */
@Data
@TableName("food_category")
public class FoodCategory {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String code;
    private String name;
    private BigDecimal minWeight;
    private String storageCond;
    private Integer retainHours;
    private String meals;
    private String owner;
    private Integer status;
    private Integer sort;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
