package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDate;import java.time.LocalDateTime;import java.math.BigDecimal;
/** 校验计算结果：保存输入快照与明细，保证同输入同输出、可追溯。 */
@Data
@TableName("eval_score")
public class EvalScore {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String evalNo;
    private LocalDate bizDate;
    private Long ruleId;
    private String ruleVersion;
    private String inputJson;
    private String detailJson;
    private BigDecimal totalScore;
    private String grade;
    private String recommend;
    private String unmetJson;
    private String improveJson;
    private LocalDateTime calcTime;
    private Integer adopted;
    private LocalDateTime createTime;
}
