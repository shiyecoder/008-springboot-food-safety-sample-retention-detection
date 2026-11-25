package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
/** 人工修正记录：原始计算结果不覆盖，修正单独留痕。 */
@Data
@TableName("eval_adjust")
public class EvalAdjust {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long scoreId;
    private String beforeGrade;
    private String afterGrade;
    private String reason;
    private String operator;
    private LocalDateTime createTime;
}
