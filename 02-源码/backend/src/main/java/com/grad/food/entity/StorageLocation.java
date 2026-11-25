package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;import java.time.LocalDateTime;import java.math.BigDecimal;
/** 留样仓位，occupied 由入柜/销毁维护，用于复核时的冲突校验。 */
@Data
@TableName("storage_location")
public class StorageLocation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String cabinetNo;
    private Integer layerNo;
    private Integer slotNo;
    private String code;
    private String locationDesc;
    private BigDecimal tempMin;
    private BigDecimal tempMax;
    private Integer status;
    private Integer occupied;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
