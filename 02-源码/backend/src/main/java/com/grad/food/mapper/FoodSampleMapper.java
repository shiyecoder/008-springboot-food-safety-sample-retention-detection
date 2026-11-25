package com.grad.food.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.grad.food.entity.FoodSample;
import org.apache.ibatis.annotations.Mapper;

/** FoodSample 的通用 CRUD，复杂统计走 XML/自定义方法。 */
@Mapper
public interface FoodSampleMapper extends BaseMapper<FoodSample> {
}
