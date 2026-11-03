package com.mallagent.bootstrap.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 检查应用与 MySQL、Redis、RabbitMQ 的连通性。
 */
@Slf4j
@Service
public class SystemHealthService {

    private final DataSource dataSource;
    private final StringRedisTemplate redisTemplate;
    private final ConnectionFactory rabbitConnectionFactory;

    public SystemHealthService(DataSource dataSource,
                               StringRedisTemplate redisTemplate,
                               ConnectionFactory rabbitConnectionFactory) {
        this.dataSource = dataSource;
        this.redisTemplate = redisTemplate;
        this.rabbitConnectionFactory = rabbitConnectionFactory;
    }

    public HealthResponse check() {
        Map<String, String> components = new LinkedHashMap<>();
        components.put("database", checkDatabase());
        components.put("redis", checkRedis());
        components.put("rabbitmq", checkRabbitMq());

        boolean up = components.values().stream().allMatch("UP"::equals);
        return new HealthResponse(up ? "UP" : "DOWN", "mallagent", components);
    }

    private String checkDatabase() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.isValid(2) ? "UP" : "DOWN";
        } catch (Exception ex) {
            log.warn("Database health check failed: {}", ex.getMessage());
            return "DOWN";
        }
    }

    private String checkRedis() {
        RedisConnection connection = null;
        try {
            if (redisTemplate.getConnectionFactory() == null) {
                return "DOWN";
            }
            connection = redisTemplate.getConnectionFactory().getConnection();
            return "PONG".equalsIgnoreCase(connection.ping()) ? "UP" : "DOWN";
        } catch (Exception ex) {
            log.warn("Redis health check failed: {}", ex.getMessage());
            return "DOWN";
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }

    private String checkRabbitMq() {
        org.springframework.amqp.rabbit.connection.Connection connection = null;
        try {
            connection = rabbitConnectionFactory.createConnection();
            return connection.isOpen() ? "UP" : "DOWN";
        } catch (Exception ex) {
            log.warn("RabbitMQ health check failed: {}", ex.getMessage());
            return "DOWN";
        } finally {
            if (connection != null) {
                connection.close();
            }
        }
    }
}
