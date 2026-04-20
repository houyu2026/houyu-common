package com.houyu.common.log.utils.globalid;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 全局ID生成器自动配置类
 * 提供GeneratorGlobalId和相关组件的自动装配
 * <p>
 * 使用方式：
 * 1. 引入houyu-common-log依赖
 * 2. 配置spring.application.name（推荐）
 * 3. 配置server.port（推荐，用于计算机器码初始范围）
 * 4. 配置Redisson（Redis连接）
 * 5. 直接注入GeneratorGlobalId使用
 * <p>
 * 自动装配的Bean：
 * - GeneratorMachineId：机器码生成器
 * - GeneratorSequence：自增序列生成器
 * - GeneratorTimestamp：时间戳生成器
 * - GeneratorGlobalId：全局ID生成器（对外API）
 */
@Configuration
@EnableConfigurationProperties(GlobalIdProperties.class)
public class GlobalIdAutoConfiguration {

    /**
     * 创建机器码生成器
     * 负责在服务启动时向Redis注册唯一的机器码
     *
     * @param redissonClient Redisson客户端
     * @param properties     配置属性
     * @param objectMapper   JSON序列化器
     * @return 机器码生成器
     */
    @Bean
    @ConditionalOnMissingBean
    public GeneratorMachineId generatorMachineId(
            RedissonClient redissonClient,
            GlobalIdProperties properties,
            ObjectMapper objectMapper) {
        return new GeneratorMachineId(redissonClient, properties, objectMapper);
    }

    /**
     * 创建自增序列生成器
     * 负责生成进程内唯一的自增序列（0000~9999）
     *
     * @return 自增序列生成器
     */
    @Bean
    @ConditionalOnMissingBean
    public GeneratorSequence generatorSequence() {
        return new GeneratorSequence();
    }

    /**
     * 创建时间戳生成器
     * 负责生成时间部分并处理序列兜底机制
     *
     * @return 时间戳生成器
     */
    @Bean
    @ConditionalOnMissingBean
    public GeneratorTimestamp generatorTimestamp() {
        return new GeneratorTimestamp();
    }

    /**
     * 创建全局ID生成器
     * 对外提供生成全局唯一ID的API
     * <p>
     * 提供的方法：
     * - generate()：生成默认标志位的ID
     * - generate(int flag)：生成带标志位的ID
     * - generateBatch(int count)：批量生成默认标志位的ID
     * - generateBatch(int count, int flag)：批量生成带标志位的ID
     *
     * @param generatorMachineId  机器码生成器
     * @param generatorSequence   自增序列生成器
     * @param generatorTimestamp  时间戳生成器
     * @return 全局ID生成器
     */
    @Bean
    @ConditionalOnMissingBean
    public GeneratorGlobalId generatorGlobalId(
            GeneratorMachineId generatorMachineId,
            GeneratorSequence generatorSequence,
            GeneratorTimestamp generatorTimestamp) {
        return new GeneratorGlobalId(generatorMachineId, generatorSequence, generatorTimestamp);
    }
}
