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
 * 机器码生成器
 * 负责在服务启动时向Redis注册唯一的机器码，服务销毁时更新销毁时间
 * 所有与机器码生成相关的常量都定义在此类中
 * <p>
 * 机器码生成规则：
 * 1. 主段：服务端口号 % 9000 * 10 ~ (服务端口号 % 9000 * 10 + 50)
 * 2. 备用段：当主段全部被占用时，使用9050~9999范围
 * <p>
 * 分布式锁：
 * - 使用Redisson分布式锁保证集群环境下机器码注册的原子性
 * - 锁Key：machine_id_{服务名称}_lock
 * - 初始加锁时间30秒，续期60秒
 */
@Slf4j
@Component
public class GeneratorMachineId {

    /**
     * 端口模数（用于计算机器码主段范围）
     * 主段起始值计算公式：服务端口号 % PORT_MODULO
     */
    public static final int PORT_MODULO = 9000;

    /**
     * 端口倍数（用于计算机器码主段起始值）
     * 主段起始值计算公式：(服务端口号 % PORT_MODULO) * PORT_MULTIPLIER
     */
    public static final int PORT_MULTIPLIER = 10;

    /**
     * 未注册机器码标记
     * 表示机器码尚未成功注册
     */
    public static final int UNREGISTERED_MACHINE_ID = -1;

    /**
     * 机器码格式（4位，带前导零）
     * 用于格式化机器码为字符串
     */
    public static final String MACHINE_ID_FORMAT = "%04d";

    /**
     * 环境变量名：主机名
     * 用于获取机器名称
     */
    public static final String ENV_HOSTNAME = "HOSTNAME";

    /**
     * 系统属性名：主机名
     * 备用方式获取机器名称
     */
    public static final String PROP_HOSTNAME = "host.name";

    /**
     * 默认主机名
     * 当无法获取机器名称时使用
     */
    public static final String DEFAULT_HOSTNAME = "unknown";

    /**
     * 当前已注册的机器码
     * 使用volatile保证多线程可见性
     */
    @Getter
    private volatile int machineId = UNREGISTERED_MACHINE_ID;

    /**
     * 当前机器码是否为备用段
     * 使用volatile保证多线程可见性
     */
    @Getter
    private volatile boolean isBackup = false;

    /**
     * 服务名称，从Spring配置获取
     * 默认值：default-service
     */
    @Value("${spring.application.name:default-service}")
    private String serviceName;

    /**
     * 服务端口号，从Spring配置获取
     * 默认值：8080
     * 用于计算机器码主段范围
     */
    @Value("${server.port:8080}")
    private int serverPort;

    /**
     * Redisson客户端
     * 用于Redis操作和分布式锁
     */
    private final RedissonClient redissonClient;

    /**
     * 全局ID配置属性
     * 包含机器码段大小、备用段范围等配置
     */
    private final GlobalIdProperties properties;

    /**
     * JSON序列化器
     * 用于机器码信息的序列化和反序列化
     */
    private final ObjectMapper objectMapper;

    /**
     * 机器码是否已注册标记
     * 使用volatile保证多线程可见性
     * 用于避免重复注册
     */
    private volatile boolean registered = false;

    /**
     * 构造函数
     *
     * @param redissonClient Redisson客户端
     * @param properties     配置属性
     * @param objectMapper   JSON序列化器
     */
    public GeneratorMachineId(RedissonClient redissonClient,
                               GlobalIdProperties properties,
                               ObjectMapper objectMapper) {
        this.redissonClient = redissonClient;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    /**
     * 初始化机器码注册
     * 服务启动完成后调用，获取并注册唯一机器码
     * 使用分布式锁保证集群环境下的原子性
     *
     * @throws IllegalStateException 注册失败时抛出
     */
    public void initialize() {
        if (registered) {
            log.info("机器码已注册，machineId: {}", machineId);
            return;
        }

        String lockKey = getLockKey(serviceName);
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
     *
     * @throws UnknownHostException      无法获取机器IP时抛出
     * @throws JsonProcessingException   JSON序列化失败时抛出
     */
    private void registerMachineId() throws UnknownHostException, JsonProcessingException {
        int basePort = serverPort % PORT_MODULO;
        int segmentStart = basePort * PORT_MULTIPLIER;
        int segmentEnd = segmentStart + properties.getMachineIdSegmentSize();

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
     * 检查该机器码是否已被占用，未占用则进行注册
     *
     * @param id       机器码
     * @param isBackup 是否为备用段
     * @return 是否注册成功
     * @throws UnknownHostException    无法获取机器IP时抛出
     * @throws JsonProcessingException JSON序列化失败时抛出
     */
    private boolean tryRegister(int id, boolean isBackup)
            throws UnknownHostException, JsonProcessingException {
        String key = getMachineIdKey(serviceName, id, isBackup);

        if (Boolean.TRUE.equals(redissonClient.getBucket(key).isExists())) {
            log.debug("机器码 {} 已被占用", id);
            return false;
        }

        RedisMachineInfoDto.MachineInfoDto info = RedisMachineInfoDto.MachineInfoDto.builder()
                .machineId(String.format(MACHINE_ID_FORMAT, id))
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
     * 获取机器码Redis键
     * 根据是否为备用段生成不同的键格式
     *
     * @param serviceName 服务名称
     * @param machineId   机器码
     * @param isBackup    是否为备用段
     * @return Redis键
     */
    public static String getMachineIdKey(String serviceName, int machineId, boolean isBackup) {
        if (isBackup) {
            return String.format("machine_id_bak_%s_%04d", serviceName, machineId);
        }
        return String.format("machine_id_%s_%04d", serviceName, machineId);
    }

    /**
     * 获取分布式锁键
     * 用于机器码注册时的分布式锁
     *
     * @param serviceName 服务名称
     * @return 分布式锁键
     */
    public static String getLockKey(String serviceName) {
        return String.format("machine_id_%s_lock", serviceName);
    }

    /**
     * 获取机器名称
     * 优先从环境变量获取，其次从系统属性获取，都失败则使用默认值
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
     * 获取当前机器码字符串（4位格式，带前导零）
     *
     * @return 机器码字符串，如 "0001"、"9500"
     */
    public String getMachineIdString() {
        return String.format(MACHINE_ID_FORMAT, machineId);
    }

    /**
     * 服务销毁时更新销毁时间
     * 用于标记该机器码已不再使用，便于后续清理
     */
    @PreDestroy
    public void destroy() {
        if (!registered || machineId == UNREGISTERED_MACHINE_ID) {
            return;
        }

        try {
            String key = getMachineIdKey(serviceName, machineId, isBackup);
            Object existing = redissonClient.getBucket(key).get();

            if (existing != null) {
                RedisMachineInfoDto.MachineInfoDto info = objectMapper.readValue(
                        existing.toString(), RedisMachineInfoDto.MachineInfoDto.class
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
