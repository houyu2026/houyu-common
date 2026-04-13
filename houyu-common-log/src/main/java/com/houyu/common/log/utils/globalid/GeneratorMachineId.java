package com.houyu.common.log.utils.globalid;

import com.alibaba.fastjson2.JSON;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
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
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
public class GeneratorMachineId {

    private static final String REDIS_KEY_SPLIT_CHAR = "_";

    /**
     * machineId存储key，machine_id_{取当前服务名称spring.application.name}_{machineId}，备用段为machine_id_bak_{取当前服务名称spring.application.name}_{machineId}
     */
    private static final String MACHINE_ID_PREFIX = "machine_id_%s_%s";
    private static final String MACHINE_ID_BACKUP_PREFIX = "machine_id_bak_%s_%s";

    /**
     * 获取machineId加锁，machine_id_{取当前服务名称spring.application.name}_lock
     */
    private static final String LOCK_PREFIX = "machine_id_%s_lock";

    /**
     * 获取machineId加锁时间，单位：秒
     */
    private static final int LOCK_WAIT_TIME = 30;
    private static final int LOCK_LEASE_TIME = 60;

    /**
     * 每个微服务最多pod数量
     */
    private static final int MAX_POD_COUNT = 50;

    /**
     * 生成machineId备用段取值
     */
    private static final int BACKUP_START = 9050;
    private static final int BACKUP_END = 9999;

    @Value("${spring.application.name}")
    private String applicationName;

    @Value("${server.port:8080}")
    private int serverPort;

    @Autowired
    private RedissonClient redissonClient;

    private RedisMachineInfoDto redisMachineInfoDto;

    @PostConstruct
    public void init() {
        try {
            allocateMachineId();
            redisMachineInfoDto.builder()
                    .redisValue(redisMachineInfoDto.getRedisValue().builder()
                            .machineName(getMachineName())
                            .machineIp(getMachineIp())
                            .registerTime(LocalDateTime.now()).build())
                    .build();
            redissonClient.getBucket(redisMachineInfoDto.getRedisKey()).set(JSON.toJSONString(redisMachineInfoDto.getRedisValue()));
            log.info("Machine ID allocated successfully: redisMachineInfoDto={}", redisMachineInfoDto);
        } catch (Exception e) {
            log.error("Machine ID allocated error", e);
            throw new RuntimeException("Machine ID allocated error", e);
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            String key = redisMachineInfoDto.getRedisKey();
            RBucket<String> bucket = redissonClient.getBucket(key);
            RedisMachineInfoDto.MachineInfoDto redisValue = JSON.parseObject(redissonClient.getBucket(redisMachineInfoDto.getRedisKey()).get().toString(), RedisMachineInfoDto.MachineInfoDto.class);
            redisValue.setDestroyTime(LocalDateTime.now());
            bucket.set(JSON.toJSONString(redisValue), 24, TimeUnit.HOURS);
            log.info("Machine ID released successfully: redisMachineInfoDto={}", redisMachineInfoDto);
        } catch (Exception e) {
            log.error("Machine ID released error", e);
        }
    }


    public String getMachineId() {
        try {
            return redisMachineInfoDto.getRedisValue().getMachineId();
        } catch (Exception e) {
            log.error("Get Machine ID error", e);
            throw new RuntimeException("Get Machine ID error", e);
        }
    }


    private void allocateMachineId() {
        String lockKey = String.format(LOCK_PREFIX, applicationName);
        RLock lock = redissonClient.getLock(lockKey);

        try {
            boolean locked = lock.tryLock(LOCK_WAIT_TIME, LOCK_LEASE_TIME, TimeUnit.SECONDS);
            if (!locked) {
                throw new RuntimeException("Failed to acquire lock for machine ID allocation");
            }

            // 尝试在主段分配机器码
            if (allocateMachineIdInRange(getMainRangeStart(), getMainRangeEnd())) {
                return;
            }

            // 主段已满，使用备用段
            if (allocateMachineIdInRange(BACKUP_START, BACKUP_END)) {
                return;
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

    private int getMainRangeStart() {
        int base = (serverPort % 9000) * 10;
        return base;
    }

    private int getMainRangeEnd() {
        int start = getMainRangeStart();
        return start + MAX_POD_COUNT - 1;
    }

    private boolean allocateMachineIdInRange(int start, int end) {
        // 根据key前缀从redis获取已经分配的machineId列表
        String keyPrefix = (start==BACKUP_START) ?
                StringUtils.remove(MACHINE_ID_BACKUP_PREFIX, "%s_%s") :
                MACHINE_ID_PREFIX.replace("%s_%s", applicationName+REDIS_KEY_SPLIT_CHAR);
        RKeys keys = redissonClient.getKeys();
        Iterator<String> it = keys.getKeysByPattern(keyPrefix, MAX_POD_COUNT).iterator();

        Set<String> redisKeySet = new HashSet<>();
        while (it.hasNext()) {
            String redisKey = it.next();
            redisKeySet.add(redisKey.substring(redisKey.lastIndexOf(REDIS_KEY_SPLIT_CHAR+1)));
        }

        for (int i = start; i <= end; i++) {
            String candidateId = String.format("%04d", i);
            if (!redisKeySet.contains(candidateId)) {
                redisMachineInfoDto = RedisMachineInfoDto.builder()
                        .redisKey(keyPrefix + applicationName + REDIS_KEY_SPLIT_CHAR + candidateId)
                        .redisValue(RedisMachineInfoDto.MachineInfoDto.builder()
                                .machineId(candidateId).build())
                        .build();
                return true;
            }
        }
        return false;
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

}
