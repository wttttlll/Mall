package com.mallagent.bootstrap.system;

import com.mallagent.common.api.Result;
import com.mallagent.common.error.CommonErrorCode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 系统健康检查接口。
 */
@Tag(name = "系统")
@RestController
@RequestMapping("/api/v1/system")
public class SystemHealthController {

    private final SystemHealthService healthService;

    public SystemHealthController(SystemHealthService healthService) {
        this.healthService = healthService;
    }

    @Operation(summary = "健康检查", description = "检查应用及 MySQL、Redis、RabbitMQ 的连通性")
    @GetMapping("/health")
    public ResponseEntity<Result<?>> health() {
        HealthResponse health = healthService.check();
        if ("UP".equals(health.status())) {
            return ResponseEntity.ok(Result.success(health));
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Result.error(CommonErrorCode.SERVICE_UNAVAILABLE, "依赖服务不可用"));
    }
}
