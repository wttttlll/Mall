package com.mallagent.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.mallagent.module.user.entity.SysUser;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RbacAdminIntegrationTest extends AbstractIntegrationTest {

    @Test
    void adminCanAssignRolesResetPasswordAndDisableUser() throws Exception {
        TokenPair admin = adminLogin();

        MvcResult page = getJson("/api/v1/admin/users?pageNum=1&pageSize=10", admin.accessToken());
        assertThat(page.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(page).path("code").asInt()).isZero();

        MvcResult tree = getJson("/api/v1/admin/permissions/tree", admin.accessToken());
        assertThat(tree.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(tree).path("data").isArray()).isTrue();
        assertThat(body(tree).path("data").size()).isGreaterThan(0);

        String username = uniqueUsername();
        register(username);
        SysUser target = user(username);
        Long operatorRoleId = roleId("OPERATOR");

        MvcResult assign = putJson("/api/v1/admin/users/" + target.getId() + "/roles",
                Map.of("roleIds", List.of(operatorRoleId)), admin.accessToken());
        assertThat(assign.getResponse().getStatus()).isEqualTo(200);

        TokenPair operator = login(username, TEST_PASSWORD);
        MvcResult operatorTree = getJson("/api/v1/admin/permissions/tree", operator.accessToken());
        assertThat(operatorTree.getResponse().getStatus()).isEqualTo(200);

        String deniedRoleCode = "DENY_" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();
        MvcResult forbiddenCreate = postJson("/api/v1/admin/roles",
                Map.of("roleCode", deniedRoleCode, "roleName", "无权限角色"),
                operator.accessToken());
        assertThat(forbiddenCreate.getResponse().getStatus()).isEqualTo(403);

        MvcResult reset = putJson("/api/v1/admin/users/" + target.getId() + "/password",
                Map.of("newPassword", "New@123456"), admin.accessToken());
        assertThat(reset.getResponse().getStatus()).isEqualTo(200);

        TokenPair afterReset = login(username, "New@123456");
        assertThat(afterReset.accessToken()).isNotBlank();

        MvcResult disable = putJson("/api/v1/admin/users/" + target.getId() + "/status",
                Map.of("status", 0), admin.accessToken());
        assertThat(disable.getResponse().getStatus()).isEqualTo(200);

        MvcResult disabledLogin = postJson("/api/v1/auth/login",
                Map.of("username", username, "password", "New@123456"));
        assertThat(disabledLogin.getResponse().getStatus()).isEqualTo(403);
        assertThat(body(disabledLogin).path("code").asInt()).isEqualTo(11005);

        MvcResult oldToken = getJson("/api/v1/users/me", afterReset.accessToken());
        assertThat(oldToken.getResponse().getStatus()).isEqualTo(401);

        SysUser adminUser = user(ADMIN_USERNAME);
        MvcResult selfDisable = putJson("/api/v1/admin/users/" + adminUser.getId() + "/status",
                Map.of("status", 0), admin.accessToken());
        assertThat(selfDisable.getResponse().getStatus()).isEqualTo(400);
        assertThat(body(selfDisable).path("code").asInt()).isEqualTo(11016);
    }

    @Test
    void roleCrudAndPermissionAssignment() throws Exception {
        TokenPair admin = adminLogin();
        String roleCode = "TEST_" + UUID.randomUUID().toString()
                .replace("-", "")
                .substring(0, 8)
                .toUpperCase();

        MvcResult created = postJson("/api/v1/admin/roles",
                Map.of("roleCode", roleCode,
                        "roleName", "测试角色",
                        "description", "集成测试角色",
                        "status", 1,
                        "sort", 99),
                admin.accessToken());
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        Long createdRoleId = body(created).path("data").path("id").asLong();

        Long permissionId = permissionId("system:permission:read");
        MvcResult assign = putJson("/api/v1/admin/roles/" + createdRoleId + "/permissions",
                Map.of("permissionIds", List.of(permissionId)), admin.accessToken());
        assertThat(assign.getResponse().getStatus()).isEqualTo(200);

        MvcResult list = getJson("/api/v1/admin/roles", admin.accessToken());
        assertThat(body(list).path("data").toString()).contains(roleCode);

        MvcResult deleted = deleteJson("/api/v1/admin/roles/" + createdRoleId, admin.accessToken());
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);

        MvcResult builtInDelete = deleteJson("/api/v1/admin/roles/" + roleId("CUSTOMER"),
                admin.accessToken());
        assertThat(builtInDelete.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(builtInDelete).path("code").asInt()).isEqualTo(11012);
    }

    @Test
    void lastSuperAdminCannotLoseSuperAdminRole() throws Exception {
        TokenPair admin = adminLogin();
        SysUser adminUser = user(ADMIN_USERNAME);

        MvcResult result = putJson("/api/v1/admin/users/" + adminUser.getId() + "/roles",
                Map.of("roleIds", List.of()), admin.accessToken());
        assertThat(result.getResponse().getStatus()).isEqualTo(409);
        assertThat(body(result).path("code").asInt()).isEqualTo(11017);
    }

    @Test
    void permissionTreeContainsSeededHierarchy() throws Exception {
        TokenPair admin = adminLogin();
        JsonNode tree = body(getJson("/api/v1/admin/permissions/tree", admin.accessToken())).path("data");
        assertThat(tree.isArray()).isTrue();
        assertThat(tree.toString()).contains("system:user");
        assertThat(tree.toString()).contains("system:role");
        assertThat(tree.toString()).contains("system:permission");
    }
}
