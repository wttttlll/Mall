package com.mallagent.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AddressIntegrationTest extends AbstractIntegrationTest {

    @Test
    void addressCrudAndDefaultSwitching() throws Exception {
        TokenPair tokens = register(uniqueUsername());

        MvcResult firstCreated = postJson("/api/v1/users/me/addresses",
                address("张三", "科技园 1 号", true), tokens.accessToken());
        assertThat(firstCreated.getResponse().getStatus()).isEqualTo(201);
        JsonNode first = body(firstCreated).path("data");
        assertThat(first.path("isDefault").asBoolean()).isTrue();
        Long firstId = first.path("id").asLong();

        MvcResult secondCreated = postJson("/api/v1/users/me/addresses",
                address("李四", "科技园 2 号", true), tokens.accessToken());
        assertThat(secondCreated.getResponse().getStatus()).isEqualTo(201);
        Long secondId = body(secondCreated).path("data").path("id").asLong();

        JsonNode list = body(getJson("/api/v1/users/me/addresses", tokens.accessToken())).path("data");
        assertThat(list.size()).isEqualTo(2);
        assertThat(countDefaults(list)).isEqualTo(1);
        assertThat(isDefault(list, firstId)).isFalse();

        MvcResult setDefault = putEmpty("/api/v1/users/me/addresses/" + firstId + "/default",
                tokens.accessToken());
        assertThat(setDefault.getResponse().getStatus()).isEqualTo(200);

        JsonNode afterSetDefault = body(getJson("/api/v1/users/me/addresses", tokens.accessToken())).path("data");
        assertThat(isDefault(afterSetDefault, firstId)).isTrue();
        assertThat(countDefaults(afterSetDefault)).isEqualTo(1);

        Map<String, Object> updated = address("张三丰", "科技园 3 号", false);
        MvcResult update = putJson("/api/v1/users/me/addresses/" + firstId,
                updated, tokens.accessToken());
        assertThat(update.getResponse().getStatus()).isEqualTo(200);
        assertThat(body(update).path("data").path("receiverName").asText()).isEqualTo("张三丰");

        MvcResult deleted = deleteJson("/api/v1/users/me/addresses/" + secondId, tokens.accessToken());
        assertThat(deleted.getResponse().getStatus()).isEqualTo(200);

        MvcResult missing = getJson("/api/v1/users/me/addresses/" + secondId, tokens.accessToken());
        assertThat(missing.getResponse().getStatus()).isEqualTo(404);
        assertThat(body(missing).path("code").asInt()).isEqualTo(11014);
    }

    @Test
    void firstAddressBecomesDefaultAutomatically() throws Exception {
        TokenPair tokens = register(uniqueUsername());

        MvcResult created = postJson("/api/v1/users/me/addresses",
                address("王五", "科技园 4 号", null), tokens.accessToken());
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        assertThat(body(created).path("data").path("isDefault").asBoolean()).isTrue();
    }

    @Test
    void addressIsOnlyVisibleToOwner() throws Exception {
        TokenPair owner = register(uniqueUsername());
        TokenPair other = register(uniqueUsername());

        MvcResult created = postJson("/api/v1/users/me/addresses",
                address("赵六", "科技园 5 号", true), owner.accessToken());
        Long addressId = body(created).path("data").path("id").asLong();

        MvcResult getByOther = getJson("/api/v1/users/me/addresses/" + addressId,
                other.accessToken());
        assertThat(getByOther.getResponse().getStatus()).isEqualTo(404);

        MvcResult deleteByOther = deleteJson("/api/v1/users/me/addresses/" + addressId,
                other.accessToken());
        assertThat(deleteByOther.getResponse().getStatus()).isEqualTo(404);
    }

    private Map<String, Object> address(String receiverName, String detailAddress, Boolean isDefault) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("receiverName", receiverName);
        body.put("receiverPhone", "13800138000");
        body.put("province", "广东省");
        body.put("city", "深圳市");
        body.put("district", "南山区");
        body.put("detailAddress", detailAddress);
        body.put("postalCode", "518000");
        if (isDefault != null) {
            body.put("isDefault", isDefault);
        }
        return body;
    }

    private long countDefaults(JsonNode addresses) {
        long count = 0;
        for (JsonNode address : addresses) {
            if (address.path("isDefault").asBoolean()) {
                count++;
            }
        }
        return count;
    }

    private boolean isDefault(JsonNode addresses, Long id) {
        for (JsonNode address : addresses) {
            if (address.path("id").asLong() == id) {
                return address.path("isDefault").asBoolean();
            }
        }
        return false;
    }
}
