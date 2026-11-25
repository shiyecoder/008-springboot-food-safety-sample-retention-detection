package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
/** 站内消息，含业务编号与跳转定位。 */
@Data
@TableName("sys_message")
public class SysMessage {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String title;
    private String content;
    private String bizType;
    private Long bizId;
    private Integer isRead;
    private LocalDateTime createTime;
}
