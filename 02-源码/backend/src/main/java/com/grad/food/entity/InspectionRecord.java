package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
/** 检测记录，核心过程单据。 */
@Data
@TableName("inspection_record")
public class InspectionRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String inspectNo;
    private Long sampleId;
    private String sampleNo;
    private String foodName;
    private String inspectType;
    private String inspectMethod;
    private Integer itemCount;
    private String result;
    private String status;
    private Long inspectorId;
    private String inspectorName;
    private LocalDateTime inspectTime;
    private Long reviewerId;
    private LocalDateTime reviewTime;
    private String reportFile;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
