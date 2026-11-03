package com.mallagent.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mallagent.module.user.entity.SysRolePermission;
import com.mallagent.module.user.mapper.SysRolePermissionMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ProductPermissionIntegrationTest extends AbstractProductIntegrationTest {

    @Autowired
    private SysRolePermissionMapper rolePermissionMapper;

    @Test
    void superAdminHasProductRootPermission() {
        Long count = rolePermissionMapper.selectCount(new LambdaQueryWrapper<SysRolePermission>()
                .eq(SysRolePermission::getRoleId, roleId("SUPER_ADMIN"))
                .eq(SysRolePermission::getPermissionId, permissionId("product")));

        assertThat(count).isEqualTo(1);
    }

    @Test
    void customerCannotUseAdminProductApis() throws Exception {
        TokenPair customer = register(uniqueUsername());

        MvcResult list = getJson("/api/v1/admin/product/spus", customer.accessToken());
        assertThat(list.getResponse().getStatus()).isEqualTo(403);

        MvcResult categories = getJson("/api/v1/admin/product/categories", customer.accessToken());
        assertThat(categories.getResponse().getStatus()).isEqualTo(403);

        MvcResult create = postJson("/api/v1/admin/product/categories",
                categoryRequest("deny_customer_" + uniqueSuffix()), customer.accessToken());
        assertThat(create.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void operatorHasReadOnlyProductPermissions() throws Exception {
        TokenPair admin = adminLogin();
        String username = uniqueUsername();
        register(username);
        assignRole(admin.accessToken(), username, "OPERATOR");
        TokenPair operator = login(username, TEST_PASSWORD);

        assertThat(getJson("/api/v1/admin/product/spus", operator.accessToken())
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/v1/admin/product/categories", operator.accessToken())
                .getResponse().getStatus()).isEqualTo(200);
        assertThat(getJson("/api/v1/admin/product/brands", operator.accessToken())
                .getResponse().getStatus()).isEqualTo(200);

        MvcResult create = postJson("/api/v1/admin/product/categories",
                categoryRequest("deny_operator_" + uniqueSuffix()), operator.accessToken());
        assertThat(create.getResponse().getStatus()).isEqualTo(403);

        assertThat(getJson("/api/v1/products", operator.accessToken())
                .getResponse().getStatus()).isEqualTo(200);
    }

    @Test
    void unauthenticatedProductBrowseRequiresLogin() throws Exception {
        MvcResult result = getJson("/api/v1/products", null);
        assertThat(result.getResponse().getStatus()).isEqualTo(401);
        assertThat(body(result).path("code").asInt()).isEqualTo(10001);
    }

    private Map<String, Object> categoryRequest(String name) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("name", name);
        request.put("parentId", null);
        request.put("sort", 0);
        request.put("status", 1);
        return request;
    }
}
