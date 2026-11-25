package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;import java.math.BigDecimal;
/** 销毁记录：双人监督，执行人与监督人不得相同。 */
@Data
@TableName("disposal_record")
public class DisposalRecord {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String disposalNo;
    private Long sampleId;
    private String sampleNo;
    private String foodName;
    private String method;
    private LocalDateTime disposeTime;
    private String executor;
    private String supervisor;
    private BigDecimal weight;
    private String photo;
    private String status;
    private String approveBy;
    private LocalDateTime approveTime;
    private String remark;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
