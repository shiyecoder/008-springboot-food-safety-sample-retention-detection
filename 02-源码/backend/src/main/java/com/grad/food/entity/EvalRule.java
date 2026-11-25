package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
/** 校验规则：weights/thresholds 以 JSON 字符串保存，修改即生成新版本。 */
@Data
@TableName("eval_rule")
public class EvalRule {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String version;
    private String name;
    private String weights;
    private String thresholds;
    private LocalDateTime effectiveTime;
    private Integer status;
    private String remark;
    private String createBy;
    private LocalDateTime createTime;
}
