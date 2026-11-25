package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;
/** 留样附件（照片/PDF），路径为本地相对路径。 */
@Data
@TableName("food_sample_attachment")
public class FoodSampleAttachment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long sampleId;
    private String fileName;
    private String path;
    private String fileType;
    private Long fileSize;
    private String uploaderName;
    private LocalDateTime createTime;
}
