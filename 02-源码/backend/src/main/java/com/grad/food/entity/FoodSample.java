package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;import java.math.BigDecimal;
/** 食品留样主档，核心业务表。 */
@Data
@TableName("food_sample")
public class FoodSample {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String sampleNo;
    private String foodName;
    private Long categoryId;
    private String meal;
    private String batchNo;
    private Long batchId;
    private Long taskId;
    private BigDecimal weight;
    private String containerNo;
    private Long locationId;
    private String locationCode;
    private BigDecimal temperature;
    private LocalDateTime storeTime;
    private LocalDateTime expireTime;
    private Integer retainHours;
    private String photo;
    private String proofPhoto;
    private Long samplerId;
    private String samplerName;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private String reviewRemark;
    private String status;
    private String inspectResult;
    private Integer locked;
    private Integer disposed;
    private String remark;
    @TableLogic
    private Integer deleted;
    @Version
    private Integer version;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
