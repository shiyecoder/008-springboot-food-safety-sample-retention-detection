package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
/** 留样操作时间轴，只追加不修改。 */
@Data
@TableName("food_sample_record")
public class FoodSampleRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sampleId;
    private String action;
    private String fromStatus;
    private String toStatus;
    private Long operatorId;
    private String operatorName;
    private String reason;
    private LocalDateTime createTime;
}
