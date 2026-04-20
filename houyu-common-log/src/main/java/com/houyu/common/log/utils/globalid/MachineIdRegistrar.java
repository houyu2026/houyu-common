package com.houyu.common.log.utils.globalid;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

/**
 * 机器码注册器
 * 负责在服务启动时向Redis注册唯一的机器码，服务销毁时更新销毁时间
 * 机器码生成规则：
 * 1. 主段：服务端口号 % 9000 * 10 ~ (服务端口号 % 9000 * 10 + 49)
 * 2. 备用段：当主段全部被占用时，使用9050~9999范围
 * <p>
 * 分布式锁：
 * - 使用Redisson分布式锁保证集群环境下机器码注册的原子性
 * - 锁Key：machine_id_{服务名称}_lock
 * - 初始加锁时间30秒，续期60秒
 */
@Slf4j
@Component
public class MachineIdRegistrar {

    /**
     * 端口模数（用于计算机器码主段范围）
     */
    private static final int PORT_MODULO = 9000;

    /**
     * 端口倍数（用于计算机器码主段起始值）
     */
    private static final int PORT_MULTIPLIER = 10;

    /**
     * 未注册机器码标记
     */
    private static final int UNREGISTERED_MACHINE_ID = -1;

    /**
     * 环境变量名：主机名
     */
    private static final String ENV_HOSTNAME = "HOSTNAME";

    /**
     * 系统属性名：主机名
     */
    private static final String PROP_HOSTNAME = "host.name";

    /**
     * 默认主机名
     */
    private static final String DEFAULT_HOSTNAME = "unknown";

    /**
     * 当前已注册的机器码
     */
    @Getter
    private volatile int machineId = UNREGISTERED_MACHINE_ID;

    /**
     * 当前机器码是否为备用段
     */
    @Getter
    private volatile boolean isBackup = false;

    /**
     * 服务名称，从Spring配置获取
     */
    @Value("${spring.application.name:default-service}")
    private String serviceName;

    /**
     * 服务端口号，从Spring配置获取
     */
    @Value("${server.port:8080}")
    private int serverPort;

    private final RedissonClient redissonClient;
    private final GlobalIdProperties properties;
    private final ObjectMapper objectMapper;

    /**
     * 机器码是否已注册标记
     */
    private volatile boolean registered = false;

    public MachineIdRegistrar(RedissonClient redissonClient, 
                               GlobalIdProperties properties,
                               ObjectMapper objectMapper) {
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 初始化机器码注册
     * 服务启动完成后自动调用，获取并注册唯一机器码
     */
    public void initialize() {
        if (registered) {
            log.info("机器码已注册，machineId: {}", machineId);
            return;
        }

        String lockKey = properties.getLockKey(serviceName);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean acquired = lock.tryLock(
                    properties.getLockLeaseTime(),
                    properties.getLockRenewalTime(),
                    TimeUnit.SECONDS
            );

            if (!acquired) {
                throw new IllegalStateException("无法获取机器码注册分布式锁: " + lockKey);
            }

            try {
                registerMachineId();
                registered = true;
            } finally {
                lock.unlock();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("机器码注册被中断", e);
        } catch (Exception e) {
            log.error("机器码注册失败", e);
            throw new IllegalStateException("机器码注册失败", e);
        }
    }

    /**
     * 执行机器码注册逻辑
     * 先尝试主段，主段用尽后尝试备用段
     */
    private void registerMachineId() throws UnknownHostException, JsonProcessingException {
        int basePort = serverPort % PORT_MODULO;
        int segmentStart = basePort * PORT_MULTIPLIER;
        int segmentEnd = segmentStart + properties.getMachineIdSegmentSize() - 1;

        log.info("尝试注册主段机器码，范围: {}~{}", segmentStart, segmentEnd);

        for (int id = segmentStart; id <= segmentEnd; id++) {
            if (tryRegister(id, false)) {
                machineId = id;
                isBackup = false;
                log.info("主段机器码注册成功: {}", machineId);
                return;
            }
        }

        log.warn("主段机器码已用尽，尝试备用段，范围: {}~{}",
                properties.getBackupMachineIdStart(), properties.getBackupMachineIdEnd());

        for (int id = properties.getBackupMachineIdStart();
             id <= properties.getBackupMachineIdEnd(); id++) {
            if (tryRegister(id, true)) {
                machineId = id;
                isBackup = true;
                log.info("备用段机器码注册成功: {}", machineId);
                return;
            }
        }

        throw new IllegalStateException("所有机器码段均已用尽，无法分配机器码");
    }

    /**
     * 尝试注册指定的机器码
     *
     * @param id       机器码
     * @param isBackup 是否为备用段
     * @return 是否注册成功
     */
    private boolean tryRegister(int id, boolean isBackup)
            throws UnknownHostException, JsonProcessingException {
        String key = properties.getMachineIdKey(serviceName, id, isBackup);

        if (Boolean.TRUE.equals(redissonClient.getBucket(key).isExists())) {
            log.debug("机器码 {} 已被占用", id);
            return false;
        }

        MachineIdInfo info = MachineIdInfo.builder()
                .machineName(getMachineName())
                .machineIp(getMachineIp())
                .registerTime(LocalDateTime.now())
                .destroyTime(null)
                .build();

        String json = objectMapper.writeValueAsString(info);
        redissonClient.getBucket(key).set(json);

        log.info("机器码 {} 注册成功，Key: {}", id, key);
        return true;
    }

    /**
     * 获取机器名称
     *
     * @return 机器名称
     */
    private String getMachineName() {
        return System.getenv(ENV_HOSTNAME) != null
                ? System.getenv(ENV_HOSTNAME)
                : System.getProperty(PROP_HOSTNAME, DEFAULT_HOSTNAME);
    }

    /**
     * 获取机器IP地址
     *
     * @return IP地址
     * @throws UnknownHostException 无法获取IP时抛出
     */
    private String getMachineIp() throws UnknownHostException {
        return InetAddress.getLocalHost().getHostAddress();
    }

    /**
     * 服务销毁时更新销毁时间
     * 用于标记该机器码已不再使用
     */
    @PreDestroy
    public void destroy() {
        if (!registered || machineId == UNREGISTERED_MACHINE_ID) {
            return;
        }

        try {
            String key = properties.getMachineIdKey(serviceName, machineId, isBackup);
            Object existing = redissonClient.getBucket(key).get();

            if (existing != null) {
                MachineIdInfo info = objectMapper.readValue(
                        existing.toString(), MachineIdInfo.class
                );
                info.setDestroyTime(LocalDateTime.now());
                redissonClient.getBucket(key).set(objectMapper.writeValueAsString(info));
                log.info("机器码 {} 已标记销毁", machineId);
            }
        } catch (Exception e) {
            log.error("更新机器码销毁时间失败", e);
        }
    }
}
