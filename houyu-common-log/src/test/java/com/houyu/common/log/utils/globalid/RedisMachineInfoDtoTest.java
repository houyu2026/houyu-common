package com.houyu.common.log.utils.globalid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Redis机器码信息DTO测试")
class RedisMachineInfoDtoTest {

    @Test
    @DisplayName("测试RedisMachineInfoDto构建器创建对象")
    void testRedisMachineInfoDtoBuilder() {
        RedisMachineInfoDto.MachineInfoDto value = RedisMachineInfoDto.MachineInfoDto.builder()
                .machineId("0001")
                .machineName("test-host")
                .machineIp("192.168.1.100")
                .build();

        RedisMachineInfoDto dto = RedisMachineInfoDto.builder()
                .redisKey("machine_id_test-service_0001")
                .redisValue(value)
                .build();

        assertEquals("machine_id_test-service_0001", dto.getRedisKey());
        assertEquals("0001", dto.getRedisValue().getMachineId());
    }

    @Test
    @DisplayName("测试RedisMachineInfoDto无参构造函数")
    void testRedisMachineInfoDtoNoArgsConstructor() {
        RedisMachineInfoDto dto = new RedisMachineInfoDto();
        assertNull(dto.getRedisKey());
        assertNull(dto.getRedisValue());
    }

    @Test
    @DisplayName("测试RedisMachineInfoDto全参构造函数")
    void testRedisMachineInfoDtoAllArgsConstructor() {
        RedisMachineInfoDto.MachineInfoDto value = new RedisMachineInfoDto.MachineInfoDto();
        RedisMachineInfoDto dto = new RedisMachineInfoDto("test-key", value);

        assertEquals("test-key", dto.getRedisKey());
        assertEquals(value, dto.getRedisValue());
    }

    @Test
    @DisplayName("测试RedisMachineInfoDto Setter方法")
    void testRedisMachineInfoDtoSetters() {
        RedisMachineInfoDto dto = new RedisMachineInfoDto();
        dto.setRedisKey("new-key");

        RedisMachineInfoDto.MachineInfoDto value = new RedisMachineInfoDto.MachineInfoDto();
        dto.setRedisValue(value);

        assertEquals("new-key", dto.getRedisKey());
        assertEquals(value, dto.getRedisValue());
    }

    @Test
    @DisplayName("测试MachineInfoDto构建器创建对象")
    void testMachineInfoDtoBuilder() {
        LocalDateTime now = LocalDateTime.now();

        RedisMachineInfoDto.MachineInfoDto info = RedisMachineInfoDto.MachineInfoDto.builder()
                .machineId("0001")
                .machineName("test-host")
                .machineIp("192.168.1.100")
                .registerTime(now)
                .destroyTime(null)
                .build();

        assertEquals("0001", info.getMachineId());
        assertEquals("test-host", info.getMachineName());
        assertEquals("192.168.1.100", info.getMachineIp());
        assertEquals(now, info.getRegisterTime());
        assertNull(info.getDestroyTime());
    }

    @Test
    @DisplayName("测试MachineInfoDto无参构造函数")
    void testMachineInfoDtoNoArgsConstructor() {
        RedisMachineInfoDto.MachineInfoDto info = new RedisMachineInfoDto.MachineInfoDto();
        assertNull(info.getMachineId());
        assertNull(info.getMachineName());
        assertNull(info.getMachineIp());
        assertNull(info.getRegisterTime());
        assertNull(info.getDestroyTime());
    }

    @Test
    @DisplayName("测试MachineInfoDto全参构造函数")
    void testMachineInfoDtoAllArgsConstructor() {
        LocalDateTime registerTime = LocalDateTime.now();
        LocalDateTime destroyTime = LocalDateTime.now().plusHours(1);

        RedisMachineInfoDto.MachineInfoDto info = new RedisMachineInfoDto.MachineInfoDto(
                "0005",
                "host-1",
                "10.0.0.1",
                registerTime,
                destroyTime
        );

        assertEquals("0005", info.getMachineId());
        assertEquals("host-1", info.getMachineName());
        assertEquals("10.0.0.1", info.getMachineIp());
        assertEquals(registerTime, info.getRegisterTime());
        assertEquals(destroyTime, info.getDestroyTime());
    }

    @Test
    @DisplayName("测试MachineInfoDto Setter方法")
    void testMachineInfoDtoSetters() {
        RedisMachineInfoDto.MachineInfoDto info = new RedisMachineInfoDto.MachineInfoDto();
        info.setMachineId("0099");
        info.setMachineName("new-host");
        info.setMachineIp("172.16.0.1");

        LocalDateTime now = LocalDateTime.now();
        info.setRegisterTime(now);
        info.setDestroyTime(now.plusHours(2));

        assertEquals("0099", info.getMachineId());
        assertEquals("new-host", info.getMachineName());
        assertEquals("172.16.0.1", info.getMachineIp());
        assertEquals(now, info.getRegisterTime());
        assertEquals(now.plusHours(2), info.getDestroyTime());
    }

    @Test
    @DisplayName("测试MachineInfoDto toString方法")
    void testMachineInfoDtoToString() {
        RedisMachineInfoDto.MachineInfoDto info = RedisMachineInfoDto.MachineInfoDto.builder()
                .machineId("0001")
                .machineName("test-host")
                .machineIp("192.168.1.100")
                .build();

        String toString = info.toString();
        assertTrue(toString.contains("0001"));
        assertTrue(toString.contains("test-host"));
        assertTrue(toString.contains("192.168.1.100"));
    }

    @Test
    @DisplayName("测试RedisMachineInfoDto toString方法")
    void testRedisMachineInfoDtoToString() {
        RedisMachineInfoDto.MachineInfoDto value = RedisMachineInfoDto.MachineInfoDto.builder()
                .machineId("0001")
                .build();

        RedisMachineInfoDto dto = RedisMachineInfoDto.builder()
                .redisKey("test-key")
                .redisValue(value)
                .build();

        String toString = dto.toString();
        assertTrue(toString.contains("test-key"));
    }
}
