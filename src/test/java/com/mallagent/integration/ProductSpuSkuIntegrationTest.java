package com.mallagent.integration;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.mallagent.module.product.entity.ProductSkuSpec;
import com.mallagent.module.product.mapper.ProductSkuSpecMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductSpuSkuIntegrationTest extends AbstractProductIntegrationTest {

    @Autowired
    private ProductSkuSpecMapper skuSpecMapper;

    @Test
    void createSpecSkuPublishAndUserVisibility() throws Exception {
        TokenPair admin = adminLogin();
        TokenPair customer = register(uniqueUsername());
        String suffix = uniqueSuffix();
        Long categoryId = createCategory(admin.accessToken(), "cat_flow_" + suffix, null, 1);
        Long brandId = createBrand(admin.accessToken(), "brand_flow_" + suffix);
        String productName = "product_flow_" + suffix;
        Long spuId = createSpu(admin.accessToken(), productName, categoryId, brandId,
                "https://example.com/main.jpg");

        Long colorSpec = createSpec(admin.accessToken(), spuId, "颜色");
        Long red = createSpecValue(admin.accessToken(), spuId, colorSpec, "红");
        Long blue = createSpecValue(admin.accessToken(), spuId, colorSpec, "蓝");
        Long sizeSpec = createSpec(admin.accessToken(), spuId, "尺寸");
        Long small = createSpecValue(admin.accessToken(), spuId, sizeSpec, "S");
        Long medium = createSpecValue(admin.accessToken(), spuId, sizeSpec, "M");

        String skuCode = "SKU_" + suffix;
        createSku(admin.accessToken(), spuId, skuCode, "99.90", List.of(red, small));

        JsonNode adminDetail = body(getJson("/api/v1/admin/product/spus/" + spuId,
                admin.accessToken())).path("data");
        assertThat(adminDetail.path("status").asText()).isEqualTo("DRAFT");
        assertThat(adminDetail.path("skus").get(0).path("price").decimalValue())
                .isEqualByComparingTo("99.90");
        assertThat(adminDetail.path("skus").get(0).path("specValues").size()).isEqualTo(2);

        String skuPath = "/api/v1/admin/product/spus/" + spuId + "/skus";
        MvcResult duplicateCombo = postJson(skuPath,
                skuRequest("SKU_DUP_" + suffix, "99.90", List.of(red, small)), admin.accessToken());
        assertThat(duplicateCombo.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(duplicateCombo).path("code").asInt()).isEqualTo(12016);

        MvcResult duplicateCode = postJson(skuPath,
                skuRequest(skuCode, "99.90", List.of(blue, medium)), admin.accessToken());
        assertThat(duplicateCode.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(duplicateCode).path("code").asInt()).isEqualTo(12014);

        MvcResult invalidSpec = postJson(skuPath,
                skuRequest("SKU_INVALID_" + suffix, "99.90", List.of(red)), admin.accessToken());
        assertThat(invalidSpec.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(invalidSpec).path("code").asInt()).isEqualTo(12015);

        JsonNode draftPage = body(getJson(
                "/api/v1/products?pageNum=1&pageSize=10&keyword=" + productName,
                customer.accessToken())).path("data");
        assertThat(draftPage.path("total").asLong()).isZero();

        MvcResult draftDetail = getJson("/api/v1/products/" + spuId, customer.accessToken());
        assertThat(draftDetail.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(draftDetail).path("code").asInt()).isEqualTo(12012);

        MvcResult published = publish(admin.accessToken(), spuId);
        assertThat(published.getResponse().getStatus()).isEqualTo(200);

        JsonNode page = body(getJson(
                "/api/v1/products?pageNum=1&pageSize=10&keyword=" + productName,
                customer.accessToken())).path("data");
        assertThat(page.path("total").asLong()).isGreaterThanOrEqualTo(1);
        JsonNode item = findById(page.path("list"), spuId);
        assertThat(item).isNotNull();
        assertThat(item.path("minPrice").decimalValue()).isEqualByComparingTo("99.90");
        assertThat(item.path("maxPrice").decimalValue()).isEqualByComparingTo("99.90");

        JsonNode detail = userDetail(customer.accessToken(), spuId).path("data");
        assertThat(detail.path("name").asText()).isEqualTo(productName);
        assertThat(detail.path("skus").size()).isEqualTo(1);
        assertThat(detail.path("skus").get(0).path("price").decimalValue())
                .isEqualByComparingTo("99.90");
        assertThat(detail.path("specs").size()).isEqualTo(2);

        String cacheKey = spuDetailCacheKey(spuId);
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isTrue();
        Long ttl = stringRedisTemplate.getExpire(cacheKey);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0);
        assertThat(productCacheService.getSpuDetail(spuId)).isPresent();
        assertThat(productCacheService.getSpuDetail(spuId).orElseThrow().name()).isEqualTo(productName);

        MvcResult offShelf = offShelf(admin.accessToken(), spuId);
        assertThat(offShelf.getResponse().getStatus()).isEqualTo(200);
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isFalse();

        MvcResult hidden = getJson("/api/v1/products/" + spuId, customer.accessToken());
        assertThat(hidden.getResponse().getStatus()).isEqualTo(404);

        JsonNode hiddenPage = body(getJson(
                "/api/v1/products?pageNum=1&pageSize=10&keyword=" + productName,
                customer.accessToken())).path("data");
        assertThat(hiddenPage.path("total").asLong()).isZero();

        MvcResult republished = publish(admin.accessToken(), spuId);
        assertThat(republished.getResponse().getStatus()).isEqualTo(200);
        assertThat(userDetail(customer.accessToken(), spuId).path("code").asInt()).isZero();
    }

    @Test
    void statusMachineAndPublishValidation() throws Exception {
        TokenPair admin = adminLogin();
        TokenPair customer = register(uniqueUsername());
        String suffix = uniqueSuffix();
        Long categoryId = createCategory(admin.accessToken(), "cat_state_" + suffix, null, 1);
        Long brandId = createBrand(admin.accessToken(), "brand_state_" + suffix);
        String productName = "product_state_" + suffix;
        Long spuId = createSpu(admin.accessToken(), productName, categoryId, brandId, null);
        Long skuId = createSku(admin.accessToken(), spuId, "SKU_STATE_" + suffix,
                "10.00", List.of());

        MvcResult noImage = publish(admin.accessToken(), spuId);
        assertThat(noImage.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(noImage).path("code").asInt()).isEqualTo(12027);

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("name", productName);
        update.put("subtitle", "有主图");
        update.put("categoryId", categoryId);
        update.put("brandId", brandId);
        update.put("mainImage", "https://example.com/main.jpg");
        update.put("images", List.of("https://example.com/main.jpg"));
        update.put("description", "状态机测试");
        update.put("sort", 0);
        MvcResult updated = putJson("/api/v1/admin/product/spus/" + spuId, update, admin.accessToken());
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);

        assertThat(publish(admin.accessToken(), spuId).getResponse().getStatus()).isEqualTo(200);

        MvcResult repeatedOnShelf = publish(admin.accessToken(), spuId);
        assertThat(repeatedOnShelf.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(repeatedOnShelf).path("code").asInt()).isEqualTo(12011);

        MvcResult toDraftFromOnShelf = putEmpty(
                "/api/v1/admin/product/spus/" + spuId + "/draft", admin.accessToken());
        assertThat(toDraftFromOnShelf.getResponse().getStatus()).isEqualTo(409);

        assertThat(offShelf(admin.accessToken(), spuId).getResponse().getStatus()).isEqualTo(200);

        MvcResult repeatedOffShelf = offShelf(admin.accessToken(), spuId);
        assertThat(repeatedOffShelf.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(repeatedOffShelf).path("code").asInt()).isEqualTo(12011);

        MvcResult toDraft = putEmpty(
                "/api/v1/admin/product/spus/" + spuId + "/draft", admin.accessToken());
        assertThat(toDraft.getResponse().getStatus()).isEqualTo(200);

        MvcResult offShelfFromDraft = offShelf(admin.accessToken(), spuId);
        assertThat(offShelfFromDraft.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(offShelfFromDraft).path("code").asInt()).isEqualTo(12011);

        assertThat(publish(admin.accessToken(), spuId).getResponse().getStatus()).isEqualTo(200);

        MvcResult updateOnShelf = putJson("/api/v1/admin/product/spus/" + spuId,
                update, admin.accessToken());
        assertThat(updateOnShelf.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(updateOnShelf).path("code").asInt()).isEqualTo(12010);

        MvcResult specOnShelf = postJson(
                "/api/v1/admin/product/spus/" + spuId + "/specs",
                Map.of("name", "上架后规格", "sort", 0), admin.accessToken());
        assertThat(specOnShelf.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(specOnShelf).path("code").asInt()).isEqualTo(12010);

        MvcResult deleteOnShelf = deleteJson("/api/v1/admin/product/spus/" + spuId,
                admin.accessToken());
        assertThat(deleteOnShelf.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(deleteOnShelf).path("code").asInt()).isEqualTo(12028);

        assertThat(offShelf(admin.accessToken(), spuId).getResponse().getStatus()).isEqualTo(200);

        Map<String, Object> disableSku = new LinkedHashMap<>();
        disableSku.put("price", new BigDecimal("10.00"));
        disableSku.put("marketPrice", new BigDecimal("20.00"));
        disableSku.put("imageUrl", "https://example.com/sku.png");
        disableSku.put("specValueIds", List.of());
        disableSku.put("status", 0);
        MvcResult skuDisabled = putJson(
                "/api/v1/admin/product/spus/" + spuId + "/skus/" + skuId,
                disableSku, admin.accessToken());
        assertThat(skuDisabled.getResponse().getStatus()).isEqualTo(200);

        MvcResult noEnabledSku = publish(admin.accessToken(), spuId);
        assertThat(noEnabledSku.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(noEnabledSku).path("code").asInt()).isEqualTo(12023);

        MvcResult deleted = deleteJson("/api/v1/admin/product/spus/" + spuId, admin.accessToken());
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);

        MvcResult adminMissing = getJson("/api/v1/admin/product/spus/" + spuId, admin.accessToken());
        assertThat(adminMissing.getResponse().getStatus()).isEqualTo(404);
        MvcResult userMissing = getJson("/api/v1/products/" + spuId, customer.accessToken());
        assertThat(userMissing.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    void disabledCategoryOrBrandHidesProductAndEvictsCache() throws Exception {
        TokenPair admin = adminLogin();
        TokenPair customer = register(uniqueUsername());
        String suffix = uniqueSuffix();
        String categoryName = "cat_hide_" + suffix;
        String brandName = "brand_hide_" + suffix;
        Long categoryId = createCategory(admin.accessToken(), categoryName, null, 1);
        Long brandId = createBrand(admin.accessToken(), brandName);
        Long spuId = createSpu(admin.accessToken(), "product_hide_" + suffix, categoryId, brandId,
                "https://example.com/main.jpg");
        createSku(admin.accessToken(), spuId, "SKU_HIDE_" + suffix, "20.00", List.of());
        assertThat(publish(admin.accessToken(), spuId).getResponse().getStatus()).isEqualTo(200);

        String cacheKey = spuDetailCacheKey(spuId);
        assertThat(getJson("/api/v1/products/" + spuId, customer.accessToken())
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isTrue();

        MvcResult categoryDisabled = putJson("/api/v1/admin/product/categories/" + categoryId,
                Map.of("name", categoryName, "sort", 0, "status", 0), admin.accessToken());
        assertThat(categoryDisabled.getResponse().getStatus()).isEqualTo(200);
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isFalse();
        assertThat(getJson("/api/v1/products/" + spuId, customer.accessToken())
                .getResponse().getStatus()).isEqualTo(404);

        assertThat(putJson("/api/v1/admin/product/categories/" + categoryId,
                Map.of("name", categoryName, "sort", 0, "status", 1),
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/v1/products/" + spuId, customer.accessToken())
                .getResponse().getStatus()).isEqualTo(200);

        MvcResult brandDisabled = putJson("/api/v1/admin/product/brands/" + brandId,
                Map.of("name", brandName, "description", "禁用品牌", "status", 0, "sort", 0),
                admin.accessToken());
        assertThat(brandDisabled.getResponse().getStatus()).isEqualTo(200);
        assertThat(stringRedisTemplate.hasKey(cacheKey)).isFalse();
        assertThat(getJson("/api/v1/products/" + spuId, customer.accessToken())
                .getResponse().getStatus()).isEqualTo(404);

        assertThat(putJson("/api/v1/admin/product/brands/" + brandId,
                Map.of("name", brandName, "description", "启用品牌", "status", 1, "sort", 0),
                admin.accessToken()).getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/v1/products/" + spuId, customer.accessToken())
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void publishRequiresCompleteSkuSpecs() throws Exception {
        TokenPair admin = adminLogin();
        String suffix = uniqueSuffix();
        Long categoryId = createCategory(admin.accessToken(), "cat_spec_complete_" + suffix, null, 1);
        Long spuId = createSpu(admin.accessToken(), "product_spec_complete_" + suffix,
                categoryId, null, "https://example.com/main.jpg");

        Long colorSpec = createSpec(admin.accessToken(), spuId, "颜色");
        Long black = createSpecValue(admin.accessToken(), spuId, colorSpec, "黑");
        Long skuId = createSku(admin.accessToken(), spuId, "SKU_SPEC_COMPLETE_" + suffix,
                "10.00", List.of(black));

        // SKU 创建后追加新规格，旧 SKU 未覆盖该规格。
        Long sizeSpec = createSpec(admin.accessToken(), spuId, "尺码");
        Long large = createSpecValue(admin.accessToken(), spuId, sizeSpec, "L");

        MvcResult incomplete = publish(admin.accessToken(), spuId);
        assertThat(incomplete.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(incomplete).path("code").asInt()).isEqualTo(12032);

        MvcResult updated = putJson("/api/v1/admin/product/spus/" + spuId + "/skus/" + skuId,
                skuUpdateRequest("10.00", List.of(black, large)), admin.accessToken());
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);

        assertThat(publish(admin.accessToken(), spuId).getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void publishRejectsSpecValueFromWrongSpec() throws Exception {
        TokenPair admin = adminLogin();
        String suffix = uniqueSuffix();
        Long categoryId = createCategory(admin.accessToken(), "cat_spec_wrong_" + suffix, null, 1);
        Long spuId = createSpu(admin.accessToken(), "product_spec_wrong_" + suffix,
                categoryId, null, "https://example.com/main.jpg");

        Long colorSpec = createSpec(admin.accessToken(), spuId, "颜色");
        Long black = createSpecValue(admin.accessToken(), spuId, colorSpec, "黑");
        Long sizeSpec = createSpec(admin.accessToken(), spuId, "尺码");
        Long large = createSpecValue(admin.accessToken(), spuId, sizeSpec, "L");
        Long skuId = createSku(admin.accessToken(), spuId, "SKU_SPEC_WRONG_" + suffix,
                "10.00", List.of(black, large));

        // 破坏数据：颜色规格行指向尺码规格值。
        skuSpecMapper.update(null, new LambdaUpdateWrapper<ProductSkuSpec>()
                .eq(ProductSkuSpec::getSkuId, skuId)
                .eq(ProductSkuSpec::getSpecId, colorSpec)
                .set(ProductSkuSpec::getSpecValueId, large));

        MvcResult published = publish(admin.accessToken(), spuId);
        assertThat(published.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(published).path("code").asInt()).isEqualTo(12032);
    }

    @Test
    void pageSizeIsCappedAtOneHundred() throws Exception {
        TokenPair admin = adminLogin();
        TokenPair customer = register(uniqueUsername());

        JsonNode adminPage = body(getJson(
                "/api/v1/admin/product/spus?pageNum=1&pageSize=1000", admin.accessToken())).path("data");
        assertThat(adminPage.path("pageSize").asLong()).isEqualTo(100);

        JsonNode userPage = body(getJson(
                "/api/v1/products?pageNum=1&pageSize=1000", customer.accessToken())).path("data");
        assertThat(userPage.path("pageSize").asLong()).isEqualTo(100);
    }

    private Map<String, Object> skuRequest(String skuCode, String price, List<Long> specValueIds) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("skuCode", skuCode);
        request.put("price", new BigDecimal(price));
        request.put("marketPrice", new BigDecimal("199.00"));
        request.put("imageUrl", "https://example.com/sku.png");
        request.put("specValueIds", specValueIds);
        request.put("status", 1);
        return request;
    }

    private Map<String, Object> skuUpdateRequest(String price, List<Long> specValueIds) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("price", new BigDecimal(price));
        request.put("marketPrice", new BigDecimal("199.00"));
        request.put("imageUrl", "https://example.com/sku.png");
        request.put("specValueIds", specValueIds);
        request.put("status", 1);
        return request;
    }

    private JsonNode findById(JsonNode list, Long id) {
        for (JsonNode item : list) {
            if (item.path("id").asLong() == id) {
                return item;
            }
        }
        return null;
    }
}
