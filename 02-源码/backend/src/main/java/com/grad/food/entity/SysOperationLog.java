package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
/** 操作日志，关键流转与审批均落库。 */
@Data
@TableName("sys_operation_log")
public class SysOperationLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String userName;
    private String action;
    private String target;
    private String detail;
    private String result;
    private String ip;
    private LocalDateTime createTime;
}
