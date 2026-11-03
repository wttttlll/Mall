package com.mallagent.integration;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mallagent.module.user.entity.SysPermission;
import com.mallagent.module.user.entity.SysRole;
import com.mallagent.module.user.entity.SysUser;
import com.mallagent.module.user.entity.SysUserRole;
import com.mallagent.module.user.mapper.SysPermissionMapper;
import com.mallagent.module.user.mapper.SysRoleMapper;
import com.mallagent.module.user.mapper.SysUserMapper;
import com.mallagent.module.user.mapper.SysUserRoleMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String ADMIN_USERNAME = "admin";
    protected static final String ADMIN_PASSWORD = "Admin@123456";
    protected static final String TEST_PASSWORD = "Test@123456";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected SysUserMapper userMapper;

    @Autowired
    protected SysRoleMapper roleMapper;

    @Autowired
    protected SysPermissionMapper permissionMapper;

    @Autowired
    protected SysUserRoleMapper userRoleMapper;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @BeforeEach
    void prepareAdminAccount() {
        SysUser admin = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, ADMIN_USERNAME));
        if (admin == null) {
            admin = new SysUser();
            admin.setUsername(ADMIN_USERNAME);
            admin.setPassword(passwordEncoder.encode(ADMIN_PASSWORD));
            admin.setNickname("系统管理员");
            admin.setStatus(1);
            admin.setTokenVersion(0);
            userMapper.insert(admin);
        } else {
            userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                    .eq(SysUser::getId, admin.getId())
                    .set(SysUser::getPassword, passwordEncoder.encode(ADMIN_PASSWORD))
                    .set(SysUser::getStatus, 1)
                    .set(SysUser::getTokenVersion, 0));
        }

        SysRole superAdminRole = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, "SUPER_ADMIN"));
        assertThat(superAdminRole).isNotNull();

        Long assigned = userRoleMapper.selectCount(new LambdaQueryWrapper<SysUserRole>()
                .eq(SysUserRole::getUserId, admin.getId())
                .eq(SysUserRole::getRoleId, superAdminRole.getId()));
        if (assigned == null || assigned == 0) {
            SysUserRole userRole = new SysUserRole();
            userRole.setUserId(admin.getId());
            userRole.setRoleId(superAdminRole.getId());
            userRoleMapper.insert(userRole);
        }
    }

    protected String uniqueUsername() {
        return "u" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    protected TokenPair register(String username) throws Exception {
        MvcResult result = postJson("/api/v1/auth/register",
                Map.of("username", username, "password", TEST_PASSWORD));
        assertThat(result.getResponse().getStatus()).isEqualTo(201);
        JsonNode body = body(result);
        assertThat(body.path("code").asInt()).isZero();
        return tokens(body.path("data"));
    }

    protected TokenPair login(String username, String password) throws Exception {
        MvcResult result = postJson("/api/v1/auth/login",
                Map.of("username", username, "password", password));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        JsonNode body = body(result);
        assertThat(body.path("code").asInt()).isZero();
        return tokens(body.path("data"));
    }

    protected TokenPair adminLogin() throws Exception {
        return login(ADMIN_USERNAME, ADMIN_PASSWORD);
    }

    protected TokenPair refreshToken(String refreshToken) throws Exception {
        MvcResult result = postJson("/api/v1/auth/refresh",
                Map.of("refreshToken", refreshToken));
        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        return tokens(body(result).path("data"));
    }

    protected MvcResult getJson(String url, String accessToken) throws Exception {
        return perform(get(url), accessToken);
    }

    protected MvcResult postJson(String url, Object body) throws Exception {
        return postJson(url, body, null);
    }

    protected MvcResult postJson(String url, Object body, String accessToken) throws Exception {
        return perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)), accessToken);
    }

    protected MvcResult putJson(String url, Object body, String accessToken) throws Exception {
        return perform(put(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)), accessToken);
    }

    protected MvcResult putEmpty(String url, String accessToken) throws Exception {
        return perform(put(url), accessToken);
    }

    protected MvcResult deleteJson(String url, String accessToken) throws Exception {
        return perform(delete(url), accessToken);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    protected SysUser user(String username) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        assertThat(user).isNotNull();
        return user;
    }

    protected Long roleId(String roleCode) {
        SysRole role = roleMapper.selectOne(new LambdaQueryWrapper<SysRole>()
                .eq(SysRole::getRoleCode, roleCode));
        assertThat(role).isNotNull();
        return role.getId();
    }

    protected Long permissionId(String permissionCode) {
        SysPermission permission = permissionMapper.selectOne(new LambdaQueryWrapper<SysPermission>()
                .eq(SysPermission::getPermissionCode, permissionCode));
        assertThat(permission).isNotNull();
        return permission.getId();
    }

    private MvcResult perform(MockHttpServletRequestBuilder builder, String accessToken) throws Exception {
        if (accessToken != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        }
        return mockMvc.perform(builder).andReturn();
    }

    private TokenPair tokens(JsonNode data) {
        return new TokenPair(
                data.path("accessToken").asText(),
                data.path("refreshToken").asText()
        );
    }

    protected record TokenPair(String accessToken, String refreshToken) {
    }
}
