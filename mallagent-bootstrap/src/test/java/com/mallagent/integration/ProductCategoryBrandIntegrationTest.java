package com.mallagent.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCategoryBrandIntegrationTest extends AbstractProductIntegrationTest {

    @Test
    void categoryTreeLevelsAndDeleteConstraints() throws Exception {
        TokenPair admin = adminLogin();
        String suffix = uniqueSuffix();
        String rootName = "cat_root_" + suffix;
        String childName = "cat_child_" + suffix;
        String grandName = "cat_grand_" + suffix;

        Long root = createCategory(admin.accessToken(), rootName, null, 1);
        Long child = createCategory(admin.accessToken(), childName, root, 1);
        Long grand = createCategory(admin.accessToken(), grandName, child, 1);

        MvcResult fourth = postJson("/api/v1/admin/product/categories",
                categoryRequest("cat_fourth_" + suffix, grand, 1), admin.accessToken());
        assertThat(fourth.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(fourth).path("code").asInt()).isEqualTo(12003);

        MvcResult duplicate = postJson("/api/v1/admin/product/categories",
                categoryRequest(childName, root, 1), admin.accessToken());
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(duplicate).path("code").asInt()).isEqualTo(12002);

        MvcResult deleteRoot = deleteJson("/api/v1/admin/product/categories/" + root, admin.accessToken());
        assertThat(deleteRoot.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(deleteRoot).path("code").asInt()).isEqualTo(12004);

        TokenPair customer = register(uniqueUsername());
        JsonNode userTree = body(getJson("/api/v1/products/categories/tree", customer.accessToken())).path("data");
        assertThat(userTree.toString()).contains(rootName, childName, grandName);

        MvcResult disabled = putJson("/api/v1/admin/product/categories/" + child,
                Map.of("name", childName, "sort", 0, "status", 0), admin.accessToken());
        assertThat(disabled.getResponse().getStatus()).isEqualTo(200);

        JsonNode afterDisable = body(getJson("/api/v1/products/categories/tree", customer.accessToken())).path("data");
        assertThat(afterDisable.toString()).doesNotContain(childName, grandName);
        assertThat(afterDisable.toString()).contains(rootName);
    }

    @Test
    void brandCrudAndDuplicate() throws Exception {
        TokenPair admin = adminLogin();
        String suffix = uniqueSuffix();
        String name = "brand_" + suffix;
        Long brandId = createBrand(admin.accessToken(), name);

        MvcResult duplicate = postJson("/api/v1/admin/product/brands",
                Map.of("name", name, "status", 1, "sort", 0), admin.accessToken());
        assertThat(duplicate.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(duplicate).path("code").asInt()).isEqualTo(12007);

        String newName = name + "_new";
        MvcResult updated = putJson("/api/v1/admin/product/brands/" + brandId,
                Map.of("name", newName, "description", "更新后的品牌", "status", 1, "sort", 1),
                admin.accessToken());
        assertThat(updated.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(updated).path("data").path("name").asText()).isEqualTo(newName);

        MvcResult deleted = deleteJson("/api/v1/admin/product/brands/" + brandId, admin.accessToken());
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);

        MvcResult missing = getJson("/api/v1/admin/product/brands/" + brandId, admin.accessToken());
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(missing).path("code").asInt()).isEqualTo(12006);
    }

    @Test
    void categoryAndBrandInUseCannotBeDeleted() throws Exception {
        TokenPair admin = adminLogin();
        String suffix = uniqueSuffix();
        Long categoryId = createCategory(admin.accessToken(), "cat_used_" + suffix, null, 1);
        Long brandId = createBrand(admin.accessToken(), "brand_used_" + suffix);
        createSpu(admin.accessToken(), "product_used_" + suffix, categoryId, brandId,
                "https://example.com/main.jpg");

        MvcResult categoryDelete = deleteJson("/api/v1/admin/product/categories/" + categoryId,
                admin.accessToken());
        assertThat(categoryDelete.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(categoryDelete).path("code").asInt()).isEqualTo(12005);

        MvcResult brandDelete = deleteJson("/api/v1/admin/product/brands/" + brandId,
                admin.accessToken());
        assertThat(brandDelete.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(brandDelete).path("code").asInt()).isEqualTo(12008);
    }

    @Test
    void categoryTreeCacheHasTtlAndInvalidatesAfterCommit() throws Exception {
        TokenPair customer = register(uniqueUsername());
        MvcResult tree = getJson("/api/v1/products/categories/tree", customer.accessToken());
        assertThat(tree.getResponse().getStatus()).isEqualTo(200);

        assertThat(stringRedisTemplate.hasKey(CATEGORY_TREE_CACHE_KEY)).isTrue();
        Long ttl = stringRedisTemplate.getExpire(CATEGORY_TREE_CACHE_KEY);
        assertThat(ttl).isNotNull();
        assertThat(ttl).isGreaterThan(0);
        assertThat(productCacheService.getCategoryTree()).isPresent();

        TokenPair admin = adminLogin();
        createCategory(admin.accessToken(), "cat_cache_" + uniqueSuffix(), null, 1);

        assertThat(stringRedisTemplate.hasKey(CATEGORY_TREE_CACHE_KEY)).isFalse();
    }

    private Map<String, Object> categoryRequest(String name, Long parentId, Integer status) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("parentId", parentId);
        request.put("sort", 0);
        request.put("status", status);
        return request;
    }
}
