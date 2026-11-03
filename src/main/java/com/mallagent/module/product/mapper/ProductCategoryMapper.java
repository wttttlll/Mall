package com.mallagent.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.product.entity.ProductCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductCategoryMapper extends BaseMapper<ProductCategory> {

    @Select("SELECT COUNT(*) FROM product_spu WHERE category_id = #{categoryId} AND deleted = 0")
    long countActiveSpus(@Param("categoryId") Long categoryId);

    @Select("SELECT id FROM product_spu WHERE category_id = #{categoryId} AND deleted = 0")
    List<Long> selectActiveSpuIds(@Param("categoryId") Long categoryId);
}
