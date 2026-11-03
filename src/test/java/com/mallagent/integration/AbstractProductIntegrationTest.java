package com.mallagent.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.mallagent.module.product.service.ProductCacheService;
import com.mallagent.module.user.entity.SysUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AbstractProductIntegrationTest extends AbstractIntegrationTest {

    protected static final String CATEGORY_TREE_CACHE_KEY = "mallagent:product:category:tree:v1";

    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

    @Autowired
    protected ProductCacheService productCacheService;

    protected String uniqueSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    protected Long createCategory(String token, String name, Long parentId, Integer status) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("parentId", parentId);
        request.put("sort", 0);
        request.put("status", status == null ? 1 : status);

        MvcResult result = postJson("/api/v1/admin/product/categories", request, token);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("data").path("id").asLong();
    }

    protected Long createBrand(String token, String name) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("logoUrl", "https://example.com/logo.png");
        request.put("description", "测试品牌");
        request.put("status", 1);
        request.put("sort", 0);

        MvcResult result = postJson("/api/v1/admin/product/brands", request, token);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("data").path("id").asLong();
    }

    protected Long createSpu(String token,
                             String name,
                             Long categoryId,
                             Long brandId,
                             String mainImage) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("subtitle", "测试副标题");
        request.put("categoryId", categoryId);
        request.put("brandId", brandId);
        request.put("mainImage", mainImage);
        request.put("images", mainImage == null
                ? List.of()
                : List.of(mainImage, "https://example.com/gallery.jpg"));
        request.put("description", "测试商品描述");
        request.put("sort", 0);

        MvcResult result = postJson("/api/v1/admin/product/spus", request, token);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("data").path("id").asLong();
    }

    protected Long createSpec(String token, Long spuId, String name) throws Exception {
        MvcResult result = postJson("/api/v1/admin/product/spus/" + spuId + "/specs",
                Map.of("name", name, "sort", 0), token);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("data").path("id").asLong();
    }

    protected Long createSpecValue(String token, Long spuId, Long specId, String value) throws Exception {
        MvcResult result = postJson("/api/v1/admin/product/spus/" + spuId + "/specs/" + specId + "/values",
                Map.of("value", value, "sort", 0), token);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("data").path("id").asLong();
    }

    protected Long createSku(String token,
                             Long spuId,
                             String skuCode,
                             String price,
                             List<Long> specValueIds) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("skuCode", skuCode);
        request.put("price", new BigDecimal(price));
        request.put("marketPrice", new BigDecimal("199.00"));
        request.put("imageUrl", "https://example.com/sku.png");
        request.put("specValueIds", specValueIds);
        request.put("status", 1);

        MvcResult result = postJson("/api/v1/admin/product/spus/" + spuId + "/skus", request, token);
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        return body(result).path("data").path("id").asLong();
    }

    protected MvcResult publish(String token, Long spuId) throws Exception {
        return putEmpty("/api/v1/admin/product/spus/" + spuId + "/on-shelf", token);
    }

    protected MvcResult offShelf(String token, Long spuId) throws Exception {
        return putEmpty("/api/v1/admin/product/spus/" + spuId + "/off-shelf", token);
    }

    protected JsonNode userDetail(String token, Long spuId) throws Exception {
        MvcResult result = getJson("/api/v1/products/" + spuId, token);
        return body(result);
    }

    protected void assignRole(String adminToken, String username, String roleCode) throws Exception {
        SysUser target = user(username);
        MvcResult result = putJson("/api/v1/admin/users/" + target.getId() + "/roles",
                Map.of("roleIds", List.of(roleId(roleCode))), adminToken);
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
    }

    protected String spuDetailCacheKey(Long spuId) {
        return "mallagent:product:spu:detail:v1:" + spuId;
    }
}
