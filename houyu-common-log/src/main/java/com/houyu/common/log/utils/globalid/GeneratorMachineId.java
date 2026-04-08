package com.houyu.common.log.utils.globalid;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GeneratorMachineId {
    private static final String LOCK_PREFIX = "machine_id_%s_lock";
    private static final String MACHINE_ID_PREFIX = "machine_id_%s_%s";
    private static final String MACHINE_ID_BACKUP_PREFIX = "machine_id_bak_%s_%s";
    private static final int LOCK_INITIAL_TIME = 30;
    private static final int MAX_MACHINE_ID = 9999;
    private static final int MAX_POD_COUNT = 50;
    private static final int BACKUP_START = 9050;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port:8080}")
    private int serverPort;

    @Autowired
    private RedissonClient redissonClient;

    private MachineInfoDto.RedisMachineInfoDto redisMachineInfoDto;
    private boolean isBackup = false;

    @PostConstruct
    public void init() {
        try {
            redisMachineInfoDto = allocateMachineId();
            log.info("Machine ID allocated successfully: machineId={}, machineName={}, machineIp={}", 
                getMachineId(), redisMachineInfoDto.getRedisValue().getMachineName(), redisMachineInfoDto.getRedisValue().getMachineIp());
        } catch (Exception e) {
            log.error("Failed to initialize machine ID manager", e);
            throw new RuntimeException("Failed to initialize machine ID manager", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (redisMachineInfoDto != null) {
            releaseMachineId();
        }
    }

    private String getMachineName() {
        String hostname = System.getenv("HOSTNAME");
        if (StringUtils.isEmpty(hostname)) {
            hostname = System.getenv("COMPUTERNAME");
        }
        if (StringUtils.isEmpty(hostname)) {
            hostname = "unknown-" + System.currentTimeMillis();
        }
        return hostname;
    }

    private String getMachineIp() {
        try {
            InetAddress localHost = InetAddress.getLocalHost();
            return localHost.getHostAddress();
        } catch (UnknownHostException e) {
            log.warn("Failed to get machine IP, using default", e);
            return "127.0.0.1";
        }
    }

    private MachineInfoDto.RedisMachineInfoDto allocateMachineId() {
        String lockKey = String.format(LOCK_PREFIX, applicationName);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(0, LOCK_INITIAL_TIME, TimeUnit.SECONDS);
            if (!locked) {
                throw new RuntimeException("Failed to acquire lock for machine ID allocation");
            }

            // 尝试在主段分配机器码
            String machineId = allocateMachineIdInRange(getMainRangeStart(), getMainRangeEnd());
            if (machineId != null) {
                return createRedisMachineInfoDto(machineId, false);
            }

            // 主段已满，使用备用段
            isBackup = true;
            machineId = allocateMachineIdInRange(BACKUP_START, MAX_MACHINE_ID);
            if (machineId != null) {
                return createRedisMachineInfoDto(machineId, true);
            }

            throw new RuntimeException("Failed to allocate machine ID, all ranges are full");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Machine ID allocation interrupted", e);
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private MachineInfoDto.RedisMachineInfoDto createRedisMachineInfoDto(String machineId, boolean isBackup) {
        String key = isBackup ? 
            String.format(MACHINE_ID_BACKUP_PREFIX, applicationName, machineId) : 
            String.format(MACHINE_ID_PREFIX, applicationName, machineId);

        MachineInfoDto machineInfoDto = MachineInfoDto.builder()
            .machineName(getMachineName())
            .machineIp(getMachineIp())
            .registerTime(LocalDateTime.now())
            .destroyTime(null)
            .build();

        MachineInfoDto.RedisMachineInfoDto redisMachineInfoDto = MachineInfoDto.RedisMachineInfoDto.builder()
            .redisKey(key)
            .redisValue(machineInfoDto)
            .build();

        // 注册到Redis，永久有效
        RBucket<String> bucket = redissonClient.getBucket(key);
        bucket.set(JSON.toJSONString(machineInfoDto));

        return redisMachineInfoDto;
    }

    private int getMainRangeStart() {
        int base = (serverPort % 9000) * 10;
        return base;
    }

    private int getMainRangeEnd() {
        int start = getMainRangeStart();
        return start + MAX_POD_COUNT - 1;
    }

    private String allocateMachineIdInRange(int start, int end) {
        // 生成key前缀
        String keyPrefix = isBackup ? 
            String.format("machine_id_bak_%s_", applicationName) : 
            String.format("machine_id_%s_", applicationName);

        // 这里应该使用Redis的keys命令或scan命令查询，但为了简化，我们直接遍历
        for (int i = start; i <= end; i++) {
            String candidateId = String.format("%04d", i);
            String key = isBackup ? 
                String.format(MACHINE_ID_BACKUP_PREFIX, applicationName, candidateId) : 
                String.format(MACHINE_ID_PREFIX, applicationName, candidateId);

            RBucket<String> bucket = redissonClient.getBucket(key);
            if (!bucket.isExists()) {
                return candidateId;
            }
        }
        return null;
    }

    private void releaseMachineId() {
        String key = redisMachineInfoDto.getRedisKey();
        RBucket<String> bucket = redissonClient.getBucket(key);
        
        try {
            String json = bucket.get();
            if (json != null) {
                MachineInfoDto machineInfoDto = JSON.parseObject(json, MachineInfoDto.class);
                machineInfoDto.setDestroyTime(LocalDateTime.now());
                bucket.set(JSON.toJSONString(machineInfoDto), 24, TimeUnit.HOURS);
            }
        } catch (Exception e) {
            log.error("Failed to update machine info", e);
        }

        log.info("Machine ID released: machineId={}, machineName={}, machineIp={}", 
            getMachineId(), redisMachineInfoDto.getRedisValue().getMachineName(), redisMachineInfoDto.getRedisValue().getMachineIp());
    }

    public String getMachineId() {
        if (redisMachineInfoDto == null) {
            return null;
        }
        // 从redisKey中提取machineId
        String key = redisMachineInfoDto.getRedisKey();
        return key.substring(key.lastIndexOf("_") + 1);
    }

    public MachineInfoDto.RedisMachineInfoDto getRedisMachineInfoDto() {
        return redisMachineInfoDto;
    }
}
