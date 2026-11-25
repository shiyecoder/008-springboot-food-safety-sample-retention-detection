package com.grad.food.entity;

import com.baomidou.mybatisplus.annotation.*;import lombok.Data;
/** 菜单。系统内不提供菜单管理入口，菜单由脚本初始化。 */
@Data
@TableName("sys_menu")
public class SysMenu {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long parentId;
    private String name;
    private String path;
    private String perm;
    private String icon;
    private String menuType;
    private Integer sort;
    private Integer visible;
}
