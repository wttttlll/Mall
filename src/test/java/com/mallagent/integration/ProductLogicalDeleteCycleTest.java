package com.mallagent.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 逻辑删除循环回归：创建 -> 删除 -> 同业务键重建 -> 再次删除。
 */
class ProductLogicalDeleteCycleTest extends AbstractProductIntegrationTest {

    @Test
    @DisplayName("分类支持多次删除与同业务键重建")
    void categoryCanBeDeletedAndRebuiltRepeatedly() throws Exception {
        TokenPair admin = adminLogin();
        String name = "cycle_cat_" + uniqueSuffix();

        Long first = createCategory(admin.accessToken(), name, 0L, 1);
        assertThat(deleteJson("/api/v1/admin/product/categories/" + first,
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);

        Long second = createCategory(admin.accessToken(), name, 0L, 1);
        assertThat(deleteJson("/api/v1/admin/product/categories/" + second,
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);

        Long third = createCategory(admin.accessToken(), name, 0L, 1);
        MvcResult duplicateActive = postJson("/api/v1/admin/product/categories",
                Map.of("name", name, "parentId", 0L, "sort", 0, "status", 1),
                admin.accessToken());
        assertThat(duplicateActive.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(duplicateActive).path("code").asInt()).isEqualTo(12002);

        assertThat(deleteJson("/api/v1/admin/product/categories/" + third,
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("品牌支持多次删除与同业务键重建")
    void brandCanBeDeletedAndRebuiltRepeatedly() throws Exception {
        TokenPair admin = adminLogin();
        String name = "cycle_brand_" + uniqueSuffix();

        Long first = createBrand(admin.accessToken(), name);
        assertThat(deleteJson("/api/v1/admin/product/brands/" + first,
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);

        Long second = createBrand(admin.accessToken(), name);
        assertThat(deleteJson("/api/v1/admin/product/brands/" + second,
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("规格与规格值支持多次删除与同业务键重建")
    void specAndValueCanBeDeletedAndRebuiltRepeatedly() throws Exception {
        TokenPair admin = adminLogin();
        String suffix = uniqueSuffix();
        Long categoryId = createCategory(admin.accessToken(), "cycle_spec_cat_" + suffix, 0L, 1);
        Long spuId = createSpu(admin.accessToken(), "cycle_spec_spu_" + suffix,
                categoryId, null, "https://example.com/cycle-spec-" + suffix + ".png");

        Long spec = createSpec(admin.accessToken(), spuId, "颜色");
        Long value = createSpecValue(admin.accessToken(), spuId, spec, "黑");
        assertThat(deleteJson("/api/v1/admin/product/spus/" + spuId
                + "/specs/" + spec + "/values/" + value,
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);

        Long rebuiltValue = createSpecValue(admin.accessToken(), spuId, spec, "黑");
        assertThat(deleteJson("/api/v1/admin/product/spus/" + spuId
                + "/specs/" + spec + "/values/" + rebuiltValue,
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);

        assertThat(deleteJson("/api/v1/admin/product/spus/" + spuId + "/specs/" + spec,
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);

        Long rebuiltSpec = createSpec(admin.accessToken(), spuId, "颜色");
        assertThat(deleteJson("/api/v1/admin/product/spus/" + spuId + "/specs/" + rebuiltSpec,
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("SKU 支持多次删除与同业务键重建")
    void skuCanBeDeletedAndRebuiltRepeatedly() throws Exception {
        TokenPair admin = adminLogin();
        String suffix = uniqueSuffix();
        Long categoryId = createCategory(admin.accessToken(), "cycle_sku_cat_" + suffix, 0L, 1);
        Long spuId = createSpu(admin.accessToken(), "cycle_sku_spu_" + suffix,
                categoryId, null, "https://example.com/cycle-sku-" + suffix + ".png");
        String skuCode = "CYCLE_SKU_" + suffix;

        Long first = createSku(admin.accessToken(), spuId, skuCode, "10.00", List.of());
        assertThat(deleteJson("/api/v1/admin/product/spus/" + spuId + "/skus/" + first,
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);

        Long second = createSku(admin.accessToken(), spuId, skuCode, "10.00", List.of());
        assertThat(deleteJson("/api/v1/admin/product/spus/" + spuId + "/skus/" + second,
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);

        Long third = createSku(admin.accessToken(), spuId, skuCode, "10.00", List.of());
        assertThat(deleteJson("/api/v1/admin/product/spus/" + spuId + "/skus/" + third,
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);
    }
}
