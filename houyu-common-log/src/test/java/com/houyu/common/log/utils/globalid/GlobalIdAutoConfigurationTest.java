package com.houyu.common.log.utils.globalid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

@DisplayName("全局ID配置属性测试")
class GlobalIdAutoConfigurationTest {

    private GlobalIdAutoConfiguration autoConfiguration;
    private RedissonClient redissonClient;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        autoConfiguration = new GlobalIdAutoConfiguration();
        redissonClient = mock(RedissonClient.class);
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    @DisplayName("测试默认配置值")
    void testDefaultValues() {
        GlobalIdProperties properties = new GlobalIdProperties();

        assertEquals(8080, properties.getServerPort());
        assertEquals(30, properties.getLockLeaseTime());
        assertEquals(60, properties.getLockRenewalTime());
        assertEquals(50, properties.getMachineIdSegmentSize());
        assertEquals(9050, properties.getBackupMachineIdStart());
        assertEquals(9999, properties.getBackupMachineIdEnd());
    }

    @Test
    @DisplayName("测试自定义配置值")
    void testCustomValues() {
        GlobalIdProperties properties = new GlobalIdProperties();
        properties.setServerPort(9000);
        properties.setLockLeaseTime(60);
        properties.setLockRenewalTime(120);
        properties.setMachineIdSegmentSize(100);
        properties.setBackupMachineIdStart(8000);
        properties.setBackupMachineIdEnd(8999);

        assertEquals(9000, properties.getServerPort());
        assertEquals(60, properties.getLockLeaseTime());
        assertEquals(120, properties.getLockRenewalTime());
        assertEquals(100, properties.getMachineIdSegmentSize());
        assertEquals(8000, properties.getBackupMachineIdStart());
        assertEquals(8999, properties.getBackupMachineIdEnd());
    }

    @Test
    @DisplayName("测试Redis键生成 - 主段")
    void testGetMachineIdKey_MainSegment() {
        GlobalIdProperties properties = new GlobalIdProperties();

        String key = properties.getMachineIdKey("test-service", 1234, false);

        assertEquals("machine_id_test-service_1234", key);
    }

    @Test
    @DisplayName("测试Redis键生成 - 备用段")
    void testGetMachineIdKey_BackupSegment() {
        GlobalIdProperties properties = new GlobalIdProperties();

        String key = properties.getMachineIdKey("test-service", 9500, true);

        assertEquals("machine_id_bak_test-service_9500", key);
    }

    @Test
    @DisplayName("测试分布式锁键生成")
    void testGetLockKey() {
        GlobalIdProperties properties = new GlobalIdProperties();

        String lockKey = properties.getLockKey("my-service");

        assertEquals("machine_id_my-service_lock", lockKey);
    }

    @Test
    @DisplayName("测试机器码格式 - 带前导零")
    void testMachineIdFormat_WithLeadingZeros() {
        String format = "%04d";

        assertEquals("0000", String.format(format, 0));
        assertEquals("0001", String.format(format, 1));
        assertEquals("0010", String.format(format, 10));
        assertEquals("0100", String.format(format, 100));
        assertEquals("1000", String.format(format, 1000));
        assertEquals("9999", String.format(format, 9999));
    }

    @Test
    @DisplayName("测试机器码范围边界")
    void testMachineIdRangeBoundaries() {
        GlobalIdProperties properties = new GlobalIdProperties();

        assertTrue(properties.getBackupMachineIdStart() <= properties.getBackupMachineIdEnd());
        assertTrue(properties.getMachineIdSegmentSize() > 0);
    }

    @Test
    @DisplayName("测试GlobalIdProperties属性设置和获取")
    void testGlobalIdPropertiesAccessors() {
        GlobalIdProperties props = new GlobalIdProperties();
        props.setServiceName("custom-service");
        props.setServerPort(9090);
        props.setLockLeaseTime(45);
        props.setLockRenewalTime(90);
        props.setMachineIdSegmentSize(60);
        props.setBackupMachineIdStart(8500);
        props.setBackupMachineIdEnd(9499);

        assertEquals("custom-service", props.getServiceName());
        assertEquals(9090, props.getServerPort());
        assertEquals(45, props.getLockLeaseTime());
        assertEquals(90, props.getLockRenewalTime());
        assertEquals(60, props.getMachineIdSegmentSize());
        assertEquals(8500, props.getBackupMachineIdStart());
        assertEquals(9499, props.getBackupMachineIdEnd());
    }

    @Test
    @DisplayName("测试自动配置创建MachineIdRegistrar")
    void testMachineIdRegistrarBean() {
        GlobalIdProperties properties = new GlobalIdProperties();
        
        MachineIdRegistrar registrar = autoConfiguration.machineIdRegistrar(
                redissonClient, properties, objectMapper
        );

        assertNotNull(registrar);
        assertEquals(-1, registrar.getMachineId());
        assertFalse(registrar.isBackup());
    }

    @Test
    @DisplayName("测试自动配置创建GlobalIdGenerator")
    void testGlobalIdGeneratorBean() {
        GlobalIdProperties properties = new GlobalIdProperties();
        
        MachineIdRegistrar registrar = autoConfiguration.machineIdRegistrar(
                redissonClient, properties, objectMapper
        );
        
        GlobalIdGenerator generator = autoConfiguration.globalIdGenerator(registrar);

        assertNotNull(generator);
    }
}
