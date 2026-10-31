package com.mallagent.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.product.entity.ProductBrand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ProductBrandMapper extends BaseMapper<ProductBrand> {

    @Select("SELECT COUNT(*) FROM product_spu WHERE brand_id = #{brandId} AND deleted = 0")
    long countActiveSpus(@Param("brandId") Long brandId);

    @Select("SELECT id FROM product_spu WHERE brand_id = #{brandId} AND deleted = 0")
    List<Long> selectActiveSpuIds(@Param("brandId") Long brandId);
}
