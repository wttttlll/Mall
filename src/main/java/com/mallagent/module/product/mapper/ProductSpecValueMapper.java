package com.mallagent.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.product.entity.ProductSpecValue;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ProductSpecValueMapper extends BaseMapper<ProductSpecValue> {

    @Select("""
            SELECT COUNT(*)
            FROM product_sku_spec ss
            JOIN product_sku sku ON sku.id = ss.sku_id AND sku.deleted = 0
            WHERE ss.spec_value_id = #{specValueId}
            """)
    long countSkuUsage(@Param("specValueId") Long specValueId);
}
