package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;
/** 字典项。 */
@Data
@TableName("sys_dict_item")
public class SysDictItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String dictType;
    private String label;
    private String value;
    private Integer sort;
    private Integer status;
}
