package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
/** 检测项目明细。 */
@Data
@TableName("inspection_item")
public class InspectionItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long recordId;
    private String itemName;
    private String standardValue;
    private String actualValue;
    private String judge;
    private LocalDateTime createTime;
}
