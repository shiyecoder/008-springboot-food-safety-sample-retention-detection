package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
/** 食品生产批次，留样任务按批次与规则自动生成。 */
@Data
@TableName("food_batch")
public class FoodBatch {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String batchNo;
    private String name;
    private String meal;
    private Long categoryId;
    private Long workshopId;
    private Long supplierId;
    private LocalDateTime produceTime;
    private String manager;
    private String menuCode;
    private Integer dishCount;
    private String status;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
