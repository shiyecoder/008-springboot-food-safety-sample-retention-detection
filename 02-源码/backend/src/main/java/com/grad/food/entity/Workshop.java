package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
/** 加工档口。 */
@Data
@TableName("workshop")
public class Workshop {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String name;
    private Long orgId;
    private String manager;
    private String phone;
    private Integer status;
    @TableLogic
    private Integer deleted;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
