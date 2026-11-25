package com.grad.food.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 销毁登记入参：双人监督，执行人与监督人必须不同。 */
@Data
public class DisposeDTO {
    @NotNull(message = "请选择留样")
    private Long sampleId;
    @NotBlank(message = "请选择销毁方式")
    private String method;
    private LocalDateTime disposeTime;
    @NotBlank(message = "执行人必填")
    private String executor;
    @NotBlank(message = "监督人必填")
    private String supervisor;
    @NotNull(message = "销毁重量必填")
    private BigDecimal weight;
    @NotBlank(message = "请上传销毁现场照片")
    private String photo;
    private String remark;
}
