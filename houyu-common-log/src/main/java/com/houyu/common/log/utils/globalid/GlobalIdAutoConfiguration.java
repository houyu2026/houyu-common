package com.houyu.common.log.utils.globalid;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局ID生成器自动配置类
 * 提供GlobalIdGenerator和相关组件的自动装配
 * <p>
 * 使用方式：
 * 1. 引入houyu-common-log依赖
 * 2. 配置spring.application.name（推荐）
 * 3. 配置server.port（推荐，用于计算机器码初始范围）
 * 4. 配置Redisson（Redis连接）
 * 5. 直接注入GlobalIdGenerator使用
 */
@Configuration
@EnableConfigurationProperties(GlobalIdProperties.class)
public class GlobalIdAutoConfiguration {

    /**
     * 创建机器码注册器
     *
     * @param redissonClient Redisson客户端
     * @param properties     配置属性
     * @param objectMapper   JSON序列化器
     * @return 机器码注册器
     */
    @Bean
    @ConditionalOnMissingBean
    public MachineIdRegistrar machineIdRegistrar(
            RedissonClient redissonClient,
            GlobalIdProperties properties,
            ObjectMapper objectMapper) {
        return new MachineIdRegistrar(redissonClient, properties, objectMapper);
    }

    /**
     * 创建全局ID生成器
     *
     * @param machineIdRegistrar 机器码注册器
     * @return 全局ID生成器
     */
    @Bean
    @ConditionalOnMissingBean
    public GlobalIdGenerator globalIdGenerator(MachineIdRegistrar machineIdRegistrar) {
        return new GlobalIdGenerator(machineIdRegistrar);
    }
}
