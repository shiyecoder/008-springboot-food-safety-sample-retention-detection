package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
/** 文件元数据，只存相对路径，不存外链。 */
@Data
@TableName("sys_file")
public class SysFile {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String bizType;
    private Long bizId;
    private String fileName;
    private String originalName;
    private String path;
    private Long fileSize;
    private String contentType;
    private Long uploaderId;
    private String uploaderName;
    private LocalDateTime createTime;
}
