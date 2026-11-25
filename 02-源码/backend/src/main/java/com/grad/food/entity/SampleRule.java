package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;import java.math.BigDecimal;
/** 留样规则：某分类某餐次的最低重量、存储条件与保留时长。 */
@Data
@TableName("sample_rule")
public class SampleRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long categoryId;
    private String meal;
    private BigDecimal minWeight;
    private String storageCond;
    private Integer retainHours;
    private String version;
    private LocalDateTime effectiveTime;
    private Integer status;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
