package com.houyu.common.log.utils.globalid;

import com.alibaba.fastjson2.JSON;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class RedisMachineInfoDtoTest {

    @Test
    void testBuilderAndGetSet() {
        LocalDateTime now = LocalDateTime.now();

        RedisMachineInfoDto.MachineInfoDto machineInfoDto = RedisMachineInfoDto.MachineInfoDto.builder()
                .machineId("0001")
                .machineName("test-host")
                .machineIp("192.168.1.1")
                .registerTime(now)
                .destroyTime(null)
                .build();

        assertEquals("0001", machineInfoDto.getMachineId());
        assertEquals("test-host", machineInfoDto.getMachineName());
        assertEquals("192.168.1.1", machineInfoDto.getMachineIp());
        assertEquals(now, machineInfoDto.getRegisterTime());
        assertNull(machineInfoDto.getDestroyTime());

        RedisMachineInfoDto dto = RedisMachineInfoDto.builder()
                .redisKey("machine_id_test_0001")
                .redisValue(machineInfoDto)
                .build();

        assertEquals("machine_id_test_0001", dto.getRedisKey());
        assertEquals(machineInfoDto, dto.getRedisValue());
    }

    @Test
    void testNoArgsConstructor() {
        RedisMachineInfoDto dto = new RedisMachineInfoDto();
        assertNull(dto.getRedisKey());
        assertNull(dto.getRedisValue());

        RedisMachineInfoDto.MachineInfoDto machineInfoDto = new RedisMachineInfoDto.MachineInfoDto();
        assertNull(machineInfoDto.getMachineId());
        assertNull(machineInfoDto.getMachineName());
        assertNull(machineInfoDto.getMachineIp());
        assertNull(machineInfoDto.getRegisterTime());
        assertNull(machineInfoDto.getDestroyTime());
    }

    @Test
    void testAllArgsConstructor() {
        LocalDateTime now = LocalDateTime.now();

        RedisMachineInfoDto.MachineInfoDto machineInfoDto = new RedisMachineInfoDto.MachineInfoDto(
                "0002", "host2", "10.0.0.1", now, null);

        assertEquals("0002", machineInfoDto.getMachineId());
        assertEquals("host2", machineInfoDto.getMachineName());
        assertEquals("10.0.0.1", machineInfoDto.getMachineIp());
        assertEquals(now, machineInfoDto.getRegisterTime());
        assertNull(machineInfoDto.getDestroyTime());

        RedisMachineInfoDto dto = new RedisMachineInfoDto("machine_id_test_0002", machineInfoDto);
        assertEquals("machine_id_test_0002", dto.getRedisKey());
        assertEquals(machineInfoDto, dto.getRedisValue());
    }

    @Test
    void testSetters() {
        LocalDateTime now = LocalDateTime.now();

        RedisMachineInfoDto.MachineInfoDto machineInfoDto = new RedisMachineInfoDto.MachineInfoDto();
        machineInfoDto.setMachineId("0003");
        machineInfoDto.setMachineName("host3");
        machineInfoDto.setMachineIp("172.16.0.1");
        machineInfoDto.setRegisterTime(now);
        machineInfoDto.setDestroyTime(now);

        assertEquals("0003", machineInfoDto.getMachineId());
        assertEquals("host3", machineInfoDto.getMachineName());
        assertEquals("172.16.0.1", machineInfoDto.getMachineIp());
        assertEquals(now, machineInfoDto.getRegisterTime());
        assertEquals(now, machineInfoDto.getDestroyTime());

        RedisMachineInfoDto dto = new RedisMachineInfoDto();
        dto.setRedisKey("machine_id_test_0003");
        dto.setRedisValue(machineInfoDto);

        assertEquals("machine_id_test_0003", dto.getRedisKey());
        assertEquals(machineInfoDto, dto.getRedisValue());
    }

    @Test
    void testJsonSerialization() {
        LocalDateTime now = LocalDateTime.of(2026, 4, 8, 14, 30, 0);

        RedisMachineInfoDto.MachineInfoDto machineInfoDto = RedisMachineInfoDto.MachineInfoDto.builder()
                .machineId("0001")
                .machineName("test-host")
                .machineIp("192.168.1.1")
                .registerTime(now)
                .destroyTime(null)
                .build();

        String json = JSON.toJSONString(machineInfoDto);
        assertNotNull(json);
        assertTrue(json.contains("0001"));
        assertTrue(json.contains("test-host"));
        assertTrue(json.contains("192.168.1.1"));
    }

    @Test
    void testJsonDeserialization() {
        String json = "{\"machineId\":\"0001\",\"machineName\":\"test-host\",\"machineIp\":\"192.168.1.1\",\"registerTime\":\"2026-04-08 14:30:00 000\",\"destroyTime\":null}";

        RedisMachineInfoDto.MachineInfoDto machineInfoDto = JSON.parseObject(json, RedisMachineInfoDto.MachineInfoDto.class);
        assertNotNull(machineInfoDto);
        assertEquals("0001", machineInfoDto.getMachineId());
        assertEquals("test-host", machineInfoDto.getMachineName());
        assertEquals("192.168.1.1", machineInfoDto.getMachineIp());
    }

    @Test
    void testEqualsAndHashCode() {
        LocalDateTime now = LocalDateTime.now();

        RedisMachineInfoDto.MachineInfoDto dto1 = RedisMachineInfoDto.MachineInfoDto.builder()
                .machineId("0001")
                .machineName("host")
                .machineIp("192.168.1.1")
                .registerTime(now)
                .build();

        RedisMachineInfoDto.MachineInfoDto dto2 = RedisMachineInfoDto.MachineInfoDto.builder()
                .machineId("0001")
                .machineName("host")
                .machineIp("192.168.1.1")
                .registerTime(now)
                .build();

        assertEquals(dto1, dto2);
        assertEquals(dto1.hashCode(), dto2.hashCode());
    }
}
