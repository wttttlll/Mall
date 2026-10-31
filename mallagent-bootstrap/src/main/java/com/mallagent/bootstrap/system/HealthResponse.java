package com.mallagent.bootstrap.system;

import java.util.Map;

/**
 * 健康检查结果。
 *
 * @param status      整体状态：UP / DOWN
 * @param application 应用名称
 * @param components  各依赖组件状态
 */
public record HealthResponse(
        String status,
        String application,
        Map<String, String> components
) {
}
