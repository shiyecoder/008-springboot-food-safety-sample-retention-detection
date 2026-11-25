package com.grad.food.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grad.food.entity.SysMessage;
import org.apache.ibatis.annotations.Mapper;

/** SysMessage 的通用 CRUD，复杂统计走 XML/自定义方法。 */
@Mapper
public interface SysMessageMapper extends BaseMapper<SysMessage> {
}
