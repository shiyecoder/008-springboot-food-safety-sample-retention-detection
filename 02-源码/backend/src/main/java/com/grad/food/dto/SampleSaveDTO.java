package com.grad.food.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 留样登记/编辑入参。
 * sampleNo 由后端生成，前端只读；version 用于并发覆盖校验。
 */
@Data
public class SampleSaveDTO {
    private Long id;
    @NotBlank(message = "食品名称必填")
    private String foodName;
    @NotNull(message = "请选择食品分类")
    private Long categoryId;
    @NotBlank(message = "餐次必填")
    private String meal;
    @NotBlank(message = "生产批次必填")
    private String batchNo;
    private Long batchId;
    private Long taskId;
    @NotNull(message = "留样重量必填")
    @DecimalMin(value = "0", message = "重量不得为负数")
    private BigDecimal weight;
    private String containerNo;
    @NotNull(message = "请选择存放仓位")
    private Long locationId;
    private BigDecimal temperature;
    private LocalDateTime storeTime;
    /** 留样图片，必须是上传组件返回的本地相对路径 */
    private String photo;
    private String proofPhoto;
    private String remark;
    private Integer version;
}
