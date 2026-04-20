package com.houyu.common.log.utils.globalid;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("机器码信息测试")
class MachineIdInfoTest {

    @Test
    @DisplayName("测试构建器创建对象")
    void testBuilder() {
        MachineIdInfo info = MachineIdInfo.builder()
                .machineName("test-host")
                .machineIp("192.168.1.100")
                .build();

        assertEquals("test-host", info.getMachineName());
        assertEquals("192.168.1.100", info.getMachineIp());
    }

    @Test
    @DisplayName("测试无参构造函数")
    void testNoArgsConstructor() {
        MachineIdInfo info = new MachineIdInfo();
        assertNull(info.getMachineName());
        assertNull(info.getMachineIp());
    }

    @Test
    @DisplayName("测试全参构造函数")
    void testAllArgsConstructor() {
        MachineIdInfo info = new MachineIdInfo(
                "host-1",
                "10.0.0.1",
                null,
                null
        );

        assertEquals("host-1", info.getMachineName());
        assertEquals("10.0.0.1", info.getMachineIp());
    }

    @Test
    @DisplayName("测试属性设置")
    void testSetters() {
        MachineIdInfo info = new MachineIdInfo();
        info.setMachineName("new-host");
        info.setMachineIp("172.16.0.1");

        assertEquals("new-host", info.getMachineName());
        assertEquals("172.16.0.1", info.getMachineIp());
    }

    @Test
    @DisplayName("测试 toString 方法")
    void testToString() {
        MachineIdInfo info = MachineIdInfo.builder()
                .machineName("test-host")
                .machineIp("192.168.1.100")
                .build();

        String toString = info.toString();
        assertTrue(toString.contains("test-host"));
        assertTrue(toString.contains("192.168.1.100"));
    }
}
