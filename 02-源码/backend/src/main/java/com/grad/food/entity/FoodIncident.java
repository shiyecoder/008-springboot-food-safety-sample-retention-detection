package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
/** 不合格事件，关闭前锁定关联留样。 */
@Data
@TableName("food_incident")
public class FoodIncident {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String incidentNo;
    private Long sampleId;
    private String sampleNo;
    private String foodName;
    private String incidentType;
    private String severity;
    private String status;
    private LocalDateTime occurTime;
    private String discoverer;
    private String sealLocation;
    private String stopSupply;
    private String traceBatch;
    private String conclusion;
    private LocalDateTime closeTime;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
