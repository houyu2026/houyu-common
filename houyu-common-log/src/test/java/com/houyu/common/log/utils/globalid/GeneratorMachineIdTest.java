package com.houyu.common.log.utils.globalid;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class GeneratorMachineIdTest {

    @Autowired
    private GeneratorMachineId generatorMachineId;

    @Autowired
    private RedissonClient redissonClient;

    @BeforeEach
    void cleanUp() {
        RKeys keys = redissonClient.getKeys();
        Iterable<String> keyIterable = keys.getKeysByPattern("machine_id_*");
        for (String key : keyIterable) {
            keys.delete(key);
        }
    }

    @AfterEach
    void tearDown() {
        RKeys keys = redissonClient.getKeys();
        Iterable<String> keyIterable = keys.getKeysByPattern("machine_id_*");
        for (String key : keyIterable) {
            keys.delete(key);
        }
    }

    @Test
    void testInitAllocatesMachineId() {
        String machineId = generatorMachineId.getMachineId();
        assertNotNull(machineId);
        assertEquals(4, machineId.length());

        int code = Integer.parseInt(machineId);
        assertTrue(code >= 0 && code <= 9999);
    }

    @Test
    void testMachineIdRegisteredInRedis() {
        String machineId = generatorMachineId.getMachineId();
        assertNotNull(machineId);

        String expectedKeyPrefix = "machine_id_houyu-common-log-test_";
        String key = expectedKeyPrefix + machineId;
        RBucket<String> bucket = redissonClient.getBucket(key);
        String value = bucket.get();
        assertNotNull(value);

        RedisMachineInfoDto.MachineInfoDto machineInfoDto = JSON.parseObject(value, RedisMachineInfoDto.MachineInfoDto.class);
        assertNotNull(machineInfoDto);
        assertEquals(machineId, machineInfoDto.getMachineId());
        assertNotNull(machineInfoDto.getMachineName());
        assertNotNull(machineInfoDto.getMachineIp());
        assertNotNull(machineInfoDto.getRegisterTime());
    }

    @Test
    void testMachineIdRangeForPort9000() {
        String machineId = generatorMachineId.getMachineId();
        assertNotNull(machineId);

        int code = Integer.parseInt(machineId);
        assertTrue(code >= 0 && code <= 49, "Machine ID should be in range 0000~0049 for port 9000, but got " + machineId);
    }

    @Test
    void testGetMachineIdReturnsValidFormat() {
        String machineId = generatorMachineId.getMachineId();
        assertNotNull(machineId);
        assertTrue(machineId.matches("\\d{4}"), "Machine ID should be 4 digits");
    }

    @Test
    void testDestroyUpdatesRedisKey() {
        String machineId = generatorMachineId.getMachineId();
        assertNotNull(machineId);

        String expectedKeyPrefix = "machine_id_houyu-common-log-test_";
        String key = expectedKeyPrefix + machineId;

        generatorMachineId.destroy();

        RBucket<String> bucket = redissonClient.getBucket(key);
        String value = bucket.get();
        assertNotNull(value);

        RedisMachineInfoDto.MachineInfoDto machineInfoDto = JSON.parseObject(value, RedisMachineInfoDto.MachineInfoDto.class);
        assertNotNull(machineInfoDto.getDestroyTime());
    }

    @Test
    void testBackupRangeAllocation() {
        String keyPrefix = "machine_id_houyu-common-log-test_";
        for (int i = 0; i <= 49; i++) {
            String candidateId = String.format("%04d", i);
            String key = keyPrefix + candidateId;
            RedisMachineInfoDto.MachineInfoDto info = RedisMachineInfoDto.MachineInfoDto.builder()
                    .machineId(candidateId)
                    .machineName("fake-host")
                    .machineIp("127.0.0.1")
                    .registerTime(LocalDateTime.now())
                    .build();
            redissonClient.getBucket(key).set(JSON.toJSONString(info));
        }

        GeneratorMachineId newGenerator = new GeneratorMachineId();
        org.springframework.beans.factory.annotation.Value annotation = null;

        try {
            java.lang.reflect.Field applicationNameField = GeneratorMachineId.class.getDeclaredField("applicationName");
            applicationNameField.setAccessible(true);
            applicationNameField.set(newGenerator, "houyu-common-log-test");

            java.lang.reflect.Field serverPortField = GeneratorMachineId.class.getDeclaredField("serverPort");
            serverPortField.setAccessible(true);
            serverPortField.set(newGenerator, 9000);

            java.lang.reflect.Field redissonClientField = GeneratorMachineId.class.getDeclaredField("redissonClient");
            redissonClientField.setAccessible(true);
            redissonClientField.set(newGenerator, redissonClient);

            newGenerator.init();

            String backupMachineId = newGenerator.getMachineId();
            assertNotNull(backupMachineId);
            int code = Integer.parseInt(backupMachineId);
            assertTrue(code >= 9050 && code <= 9999, "Backup machine ID should be in range 9050~9999, but got " + backupMachineId);

            String backupKey = "machine_id_bak_houyu-common-log-test_" + backupMachineId;
            RBucket<String> bucket = redissonClient.getBucket(backupKey);
            String value = bucket.get();
            assertNotNull(value);

            newGenerator.destroy();
        } catch (Exception e) {
            fail("Failed to test backup range allocation: " + e.getMessage());
        }
    }

    @Test
    void testAllRangesFull() {
        String keyPrefix = "machine_id_houyu-common-log-test_";
        for (int i = 0; i <= 49; i++) {
            String candidateId = String.format("%04d", i);
            String key = keyPrefix + candidateId;
            RedisMachineInfoDto.MachineInfoDto info = RedisMachineInfoDto.MachineInfoDto.builder()
                    .machineId(candidateId)
                    .machineName("fake-host")
                    .machineIp("127.0.0.1")
                    .registerTime(LocalDateTime.now())
                    .build();
            redissonClient.getBucket(key).set(JSON.toJSONString(info));
        }

        String backupKeyPrefix = "machine_id_bak_houyu-common-log-test_";
        for (int i = 9050; i <= 9999; i++) {
            String candidateId = String.format("%04d", i);
            String key = backupKeyPrefix + candidateId;
            RedisMachineInfoDto.MachineInfoDto info = RedisMachineInfoDto.MachineInfoDto.builder()
                    .machineId(candidateId)
                    .machineName("fake-host")
                    .machineIp("127.0.0.1")
                    .registerTime(LocalDateTime.now())
                    .build();
            redissonClient.getBucket(key).set(JSON.toJSONString(info));
        }

        GeneratorMachineId newGenerator = new GeneratorMachineId();
        try {
            java.lang.reflect.Field applicationNameField = GeneratorMachineId.class.getDeclaredField("applicationName");
            applicationNameField.setAccessible(true);
            applicationNameField.set(newGenerator, "houyu-common-log-test");

            java.lang.reflect.Field serverPortField = GeneratorMachineId.class.getDeclaredField("serverPort");
            serverPortField.setAccessible(true);
            serverPortField.set(newGenerator, 9000);

            java.lang.reflect.Field redissonClientField = GeneratorMachineId.class.getDeclaredField("redissonClient");
            redissonClientField.setAccessible(true);
            redissonClientField.set(newGenerator, redissonClient);

            assertThrows(RuntimeException.class, newGenerator::init);
        } catch (Exception e) {
            fail("Failed to test all ranges full: " + e.getMessage());
        }
    }

    @Test
    void testGetMachineIdWithNullRedisMachineInfoDto() {
        GeneratorMachineId newGenerator = new GeneratorMachineId();
        try {
            java.lang.reflect.Field redissonClientField = GeneratorMachineId.class.getDeclaredField("redissonClient");
            redissonClientField.setAccessible(true);
            redissonClientField.set(newGenerator, redissonClient);

            java.lang.reflect.Field applicationNameField = GeneratorMachineId.class.getDeclaredField("applicationName");
            applicationNameField.setAccessible(true);
            applicationNameField.set(newGenerator, "houyu-common-log-test");

            java.lang.reflect.Field serverPortField = GeneratorMachineId.class.getDeclaredField("serverPort");
            serverPortField.setAccessible(true);
            serverPortField.set(newGenerator, 9000);

            assertThrows(RuntimeException.class, newGenerator::getMachineId);
        } catch (Exception e) {
            fail("Failed to test getMachineId with null redisMachineInfoDto: " + e.getMessage());
        }
    }
}
