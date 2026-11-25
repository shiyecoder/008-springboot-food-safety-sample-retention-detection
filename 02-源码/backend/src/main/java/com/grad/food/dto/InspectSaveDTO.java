package com.grad.food.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/** 检测登记入参，含项目明细。 */
@Data
public class InspectSaveDTO {
    @NotNull(message = "请选择留样")
    private Long sampleId;
    private String inspectType;
    private String inspectMethod;
    private String result;
    private String reportFile;
    private String remark;
    private List<Item> items;

    /** 检测项目行 */
    @Data
    public static class Item {
        private String itemName;
        private String standardValue;
        private String actualValue;
        private String judge;
    }
}
