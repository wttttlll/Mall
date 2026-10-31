package com.mallagent.integration;

import com.mallagent.module.product.api.ProductSkuQueryApi;
import com.mallagent.module.product.api.dto.SkuAvailability;
import com.mallagent.module.product.api.dto.SkuSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSkuQueryApiIntegrationTest extends AbstractProductIntegrationTest {

    @Autowired
    private ProductSkuQueryApi productSkuQueryApi;

    @Test
    @DisplayName("上架商品返回可购买快照与规格文本")
    void availableSkuSnapshot() throws Exception {
        TokenPair admin = adminLogin();
        String suffix = uniqueSuffix();
        Long categoryId = createCategory(admin.accessToken(), "查询分类-" + suffix, 0L, 1);
        Long brandId = createBrand(admin.accessToken(), "查询品牌-" + suffix);
        Long spuId = createSpu(admin.accessToken(), "查询商品-" + suffix, categoryId, brandId,
                "https://example.com/query-main-" + suffix + ".png");
        Long specId = createSpec(admin.accessToken(), spuId, "颜色");
        Long valueId = createSpecValue(admin.accessToken(), spuId, specId, "黑色");
        Long skuId = createSku(admin.accessToken(), spuId, "QUERY-SKU-" + suffix, "88.80", List.of(valueId));
        assertThat(publish(admin.accessToken(), spuId).getResponse().getStatus()).isEqualTo(200);

        SkuSnapshot snapshot = productSkuQueryApi.findSku(skuId).orElseThrow();

        assertThat(snapshot.purchasable()).isTrue();
        assertThat(snapshot.availability()).isEqualTo(SkuAvailability.AVAILABLE);
        assertThat(snapshot.price()).isEqualByComparingTo("88.80");
        assertThat(snapshot.spuName()).isEqualTo("查询商品-" + suffix);
        assertThat(snapshot.specText()).contains("颜色=黑色");
    }

    @Test
    @DisplayName("下架商品快照标记为不可购买")
    void offShelfSkuIsUnavailable() throws Exception {
        TokenPair admin = adminLogin();
        String suffix = uniqueSuffix();
        Long categoryId = createCategory(admin.accessToken(), "下架分类-" + suffix, 0L, 1);
        Long spuId = createSpu(admin.accessToken(), "下架商品-" + suffix, categoryId, null,
                "https://example.com/off-main-" + suffix + ".png");
        Long skuId = createSku(admin.accessToken(), spuId, "OFF-SKU-" + suffix, "10.00", List.of());
        assertThat(publish(admin.accessToken(), spuId).getResponse().getStatus()).isEqualTo(200);
        assertThat(offShelf(admin.accessToken(), spuId).getResponse().getStatus()).isEqualTo(200);

        SkuSnapshot snapshot = productSkuQueryApi.findSku(skuId).orElseThrow();

        assertThat(snapshot.purchasable()).isFalse();
        assertThat(snapshot.availability()).isEqualTo(SkuAvailability.SPU_NOT_ON_SHELF);
    }

    @Test
    @DisplayName("不存在的 SKU 返回空")
    void missingSkuReturnsEmpty() {
        assertThat(productSkuQueryApi.findSku(999_999_999L)).isEmpty();
        assertThat(productSkuQueryApi.findSkus(List.of())).isEmpty();
    }
}
