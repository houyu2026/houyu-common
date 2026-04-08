package com.houyu.common.log.utils.globalid;

import com.alibaba.fastjson2.JSON;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.redisson.api.lock.RedissonLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class MachineIdManager {
    private static final String LOCK_PREFIX = "machine_id_%s_lock";
    private static final String MACHINE_ID_PREFIX = "machine_id_%s_%s";
    private static final String MACHINE_ID_BACKUP_PREFIX = "machine_id_bak_%s_%s";
    private static final int LOCK_INITIAL_TIME = 30;
    private static final int LOCK_RENEWAL_TIME = 60;
    private static final int MAX_MACHINE_ID = 9999;
    private static final int MAX_POD_COUNT = 50;
    private static final int BACKUP_START = 9050;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port:8080}")
    private int serverPort;

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private ObjectMapper objectMapper;

    private String machineId;
    private String machineName;
    private String machineIp;
    private LocalDateTime registerTime;
    private boolean isBackup = false;

    @PostConstruct
    public void init() {
        try {
            machineName = getMachineName();
            machineIp = getMachineIp();
            registerTime = LocalDateTime.now();
            machineId = allocateMachineId();
            log.info("Machine ID allocated successfully: machineId={}, machineName={}, machineIp={}", machineId, machineName, machineIp);
        } catch (Exception e) {
            log.error("Failed to initialize machine ID manager", e);
            throw new RuntimeException("Failed to initialize machine ID manager", e);
        }
    }

    @PreDestroy
    public void destroy() {
        if (machineId != null) {
            releaseMachineId();
        }
    }

    private String getMachineName() {
        String hostname = System.getenv("HOSTNAME");
        if (hostname == null || hostname.isEmpty()) {
            hostname = System.getenv("COMPUTERNAME");
        }
        if (hostname == null || hostname.isEmpty()) {
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

    private String allocateMachineId() {
        String lockKey = String.format(LOCK_PREFIX, applicationName);
        RedissonLock lock = (RedissonLock) redissonClient.getLock(lockKey);

        org.redisson.api.RLock lock1 = redissonClient.getLock(lockKey);
        lock1.tryLock(0, LOCK_INITIAL_TIME, TimeUnit.SECONDS);

        try {
            boolean locked = lock.tryLock(0, LOCK_INITIAL_TIME, TimeUnit.SECONDS);
            if (!locked) {
                throw new RuntimeException("Failed to acquire lock for machine ID allocation");
            }

            // 启动锁续期线程
            startLockRenewal(lock);

            // 尝试在主段分配机器码
            String machineId = allocateMachineIdInRange(getMainRangeStart(), getMainRangeEnd());
            if (machineId != null) {
                return machineId;
            }

            // 主段已满，使用备用段
            isBackup = true;
            machineId = allocateMachineIdInRange(BACKUP_START, MAX_MACHINE_ID);
            if (machineId != null) {
                return machineId;
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

    private void startLockRenewal(RedissonLock lock) {
        Thread renewalThread = new Thread(() -> {
            while (lock.isHeldByCurrentThread()) {
                try {
                    Thread.sleep(LOCK_RENEWAL_TIME / 2 * 1000);
                    lock.renewExpiration();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        renewalThread.setDaemon(true);
        renewalThread.setName("machine-id-lock-renewal");
        renewalThread.start();
    }

    private int getMainRangeStart() {
        int base = (serverPort % 9000) * 10;
        return base;
    }

    private int getMainRangeEnd() {
        int base = (serverPort % 9000) * 10;
        return base + 49;
    }

    private String allocateMachineIdInRange(int start, int end) {
        for (int i = start; i <= end; i++) {
            String candidateId = String.format("%04d", i);
            String key = isBackup ? 
                String.format(MACHINE_ID_BACKUP_PREFIX, applicationName, candidateId) : 
                String.format(MACHINE_ID_PREFIX, applicationName, candidateId);

            RBucket<String> bucket = redissonClient.getBucket(key);
            if (!bucket.isExists()) {
                // 注册机器码
                MachineInfo machineInfo = new MachineInfo(machineName, machineIp, registerTime, null);
                try {
                    String json = objectMapper.writeValueAsString(machineInfo);
                    bucket.set(json, 24, TimeUnit.HOURS);
                    return candidateId;
                } catch (JsonProcessingException e) {
                    log.error("Failed to serialize machine info", e);
                    throw new RuntimeException("Failed to serialize machine info", e);
                }
            }
        }
        return null;
    }

    private void releaseMachineId() {
        String key = isBackup ? 
            String.format(MACHINE_ID_BACKUP_PREFIX, applicationName, machineId) : 
            String.format(MACHINE_ID_PREFIX, applicationName, machineId);

        RBucket<String> bucket = redissonClient.getBucket(key);
        try {
            String json = bucket.get();
            if (json != null) {
                MachineInfo machineInfo = objectMapper.readValue(json, MachineInfo.class);
                machineInfo.setDestroyTime(LocalDateTime.now());
                String updatedJson = objectMapper.writeValueAsString(machineInfo);
                bucket.set(updatedJson, 24, TimeUnit.HOURS);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to update machine info", e);
        }

        log.info("Machine ID released: machineId={}, machineName={}, machineIp={}", machineId, machineName, machineIp);
    }

    public String getMachineId() {
        return machineId;
    }
}
