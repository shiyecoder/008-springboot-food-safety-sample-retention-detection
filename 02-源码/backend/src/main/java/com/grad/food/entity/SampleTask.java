package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;import java.math.BigDecimal;
/** 留样任务。规则快照（minWeight/storageCond）在生成时固化，后续改规则不影响已生成任务。 */
@Data
@TableName("sample_task")
public class SampleTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String taskNo;
    private Long batchId;
    private String batchNo;
    private String foodName;
    private Long categoryId;
    private String meal;
    private Long ruleId;
    private BigDecimal minWeight;
    private String storageCond;
    private LocalDateTime deadline;
    private String status;
    private Long samplerId;
    private String samplerName;
    private Long sampleId;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
