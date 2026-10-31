package com.mallagent.module.product.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mallagent.module.product.entity.ProductSkuSpec;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

@Mapper
public interface ProductSkuSpecMapper extends BaseMapper<ProductSkuSpec> {

    @Select("""
            <script>
            SELECT ss.sku_id,
                   ss.spec_id,
                   s.name AS spec_name,
                   ss.spec_value_id,
                   sv.spec_value AS spec_value_name
            FROM product_sku_spec ss
            JOIN product_spec s ON s.id = ss.spec_id AND s.deleted = 0
            JOIN product_spec_value sv ON sv.id = ss.spec_value_id AND sv.deleted = 0
            WHERE ss.sku_id IN
            <foreach collection="skuIds" item="skuId" open="(" separator="," close=")">
              #{skuId}
            </foreach>
            ORDER BY ss.sku_id, ss.spec_id
            </script>
            """)
    List<SkuSpecValueRow> selectRowsBySkuIds(@Param("skuIds") Collection<Long> skuIds);
}
