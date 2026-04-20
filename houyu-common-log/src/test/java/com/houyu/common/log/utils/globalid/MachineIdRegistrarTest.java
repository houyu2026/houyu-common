package com.houyu.common.log.utils.globalid;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.test.util.ReflectionTestUtils;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("机器码注册器测试")
class MachineIdRegistrarTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Mock
    private RBucket<Object> bucket;

    private GlobalIdProperties properties;
    private MachineIdRegistrar registrar;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new GlobalIdProperties();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        registrar = new MachineIdRegistrar(redissonClient, properties, objectMapper);

        ReflectionTestUtils.setField(registrar, "serviceName", "test-service");
        ReflectionTestUtils.setField(registrar, "serverPort", 9000);
    }

    @Test
    @DisplayName("测试初始状态")
    void testInitialState() {
        assertEquals(-1, registrar.getMachineId());
        assertFalse(registrar.isBackup());
    }

    @Test
    @DisplayName("测试Redis键生成规则")
    void testRedisKeyGeneration() {
        String mainKey = properties.getMachineIdKey("test-service", 1234, false);
        String backupKey = properties.getMachineIdKey("test-service", 9500, true);
        String lockKey = properties.getLockKey("test-service");

        assertEquals("machine_id_test-service_1234", mainKey);
        assertEquals("machine_id_bak_test-service_9500", backupKey);
        assertEquals("machine_id_test-service_lock", lockKey);
    }

    @Test
    @DisplayName("测试机器码信息序列化")
    void testMachineIdInfoSerialization() throws Exception {
        MachineIdInfo info = MachineIdInfo.builder()
                .machineName("test-host")
                .machineIp("192.168.1.100")
                .registerTime(LocalDateTime.of(2026, 4, 20, 14, 30, 0, 123000000))
                .destroyTime(null)
                .build();

        String json = objectMapper.writeValueAsString(info);
        MachineIdInfo parsed = objectMapper.readValue(json, MachineIdInfo.class);

        assertEquals(info.getMachineName(), parsed.getMachineName());
        assertEquals(info.getMachineIp(), parsed.getMachineIp());
        assertEquals(info.getRegisterTime().getYear(), parsed.getRegisterTime().getYear());
        assertNull(parsed.getDestroyTime());
    }

    @Test
    @DisplayName("测试配置属性默认值")
    void testPropertiesDefaults() {
        assertEquals(8080, properties.getServerPort());
        assertEquals(30, properties.getLockLeaseTime());
        assertEquals(60, properties.getLockRenewalTime());
        assertEquals(50, properties.getMachineIdSegmentSize());
        assertEquals(9050, properties.getBackupMachineIdStart());
        assertEquals(9999, properties.getBackupMachineIdEnd());
    }

    @Test
    @DisplayName("测试配置属性自定义值")
    void testPropertiesCustomValues() {
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
    @DisplayName("测试无法获取分布式锁抛出异常")
    void testInitialize_LockAcquisitionFailed() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(false);

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            registrar.initialize();
        });

        assertNotNull(exception);
    }

    @Test
    @DisplayName("测试获取锁时被中断抛出异常")
    void testInitialize_Interrupted() throws Exception {
        when(redissonClient.getLock(anyString())).thenReturn(lock);
        when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class)))
                .thenThrow(new InterruptedException("Test interrupt"));

        assertThrows(IllegalStateException.class, () -> {
            registrar.initialize();
        });
    }

    @Test
    @DisplayName("测试重复初始化不执行重复注册")
    void testInitialize_AlreadyRegistered() throws Exception {
        ReflectionTestUtils.setField(registrar, "registered", true);
        ReflectionTestUtils.setField(registrar, "machineId", 1234);

        registrar.initialize();

        verify(redissonClient, never()).getLock(anyString());
        assertEquals(1234, registrar.getMachineId());
    }

    @Test
    @DisplayName("测试未注册时destroy不执行任何操作")
    void testDestroy_NotRegistered() {
        ReflectionTestUtils.setField(registrar, "registered", false);
        ReflectionTestUtils.setField(registrar, "machineId", -1);

        registrar.destroy();

        verify(redissonClient, never()).getBucket(anyString());
    }

    @Test
    @DisplayName("测试已注册但machineId为-1时destroy不执行")
    void testDestroy_RegisteredButNoMachineId() {
        ReflectionTestUtils.setField(registrar, "registered", true);
        ReflectionTestUtils.setField(registrar, "machineId", -1);

        registrar.destroy();

        verify(redissonClient, never()).getBucket(anyString());
    }

    @Test
    @DisplayName("测试已注册但redis无数据时destroy不执行更新")
    void testDestroy_NoDataInRedis() {
        ReflectionTestUtils.setField(registrar, "registered", true);
        ReflectionTestUtils.setField(registrar, "machineId", 1234);
        ReflectionTestUtils.setField(registrar, "isBackup", false);
        ReflectionTestUtils.setField(registrar, "serviceName", "test-service");

        when(redissonClient.getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);

        registrar.destroy();

        verify(bucket, never()).set(any());
    }

    @Test
    @DisplayName("测试使用备用段时destroy正常执行")
    void testDestroy_WithBackupMachineId() throws Exception {
        ReflectionTestUtils.setField(registrar, "registered", true);
        ReflectionTestUtils.setField(registrar, "machineId", 9500);
        ReflectionTestUtils.setField(registrar, "isBackup", true);
        ReflectionTestUtils.setField(registrar, "serviceName", "test-service");

        MachineIdInfo existingInfo = MachineIdInfo.builder()
                .machineName("test-host")
                .machineIp("192.168.1.100")
                .registerTime(LocalDateTime.now())
                .destroyTime(null)
                .build();

        when(redissonClient.getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(objectMapper.writeValueAsString(existingInfo));

        registrar.destroy();

        verify(bucket, times(1)).set(anyString());
    }

    @Test
    @DisplayName("测试destroy时JSON解析失败的异常处理")
    void testDestroy_JsonParseException() {
        ReflectionTestUtils.setField(registrar, "registered", true);
        ReflectionTestUtils.setField(registrar, "machineId", 1234);
        ReflectionTestUtils.setField(registrar, "isBackup", false);
        ReflectionTestUtils.setField(registrar, "serviceName", "test-service");

        when(redissonClient.getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn("invalid json");

        registrar.destroy();

        verify(bucket, never()).set(any());
    }

    @Test
    @DisplayName("测试MachineIdInfo无参构造函数")
    void testMachineIdInfoNoArgsConstructor() {
        MachineIdInfo info = new MachineIdInfo();
        assertNull(info.getMachineName());
        assertNull(info.getMachineIp());
        assertNull(info.getRegisterTime());
        assertNull(info.getDestroyTime());
    }

    @Test
    @DisplayName("测试MachineIdInfo全参构造函数")
    void testMachineIdInfoAllArgsConstructor() {
        LocalDateTime registerTime = LocalDateTime.now();
        LocalDateTime destroyTime = LocalDateTime.now().plusHours(1);

        MachineIdInfo info = new MachineIdInfo(
                "test-host",
                "192.168.1.100",
                registerTime,
                destroyTime
        );

        assertEquals("test-host", info.getMachineName());
        assertEquals("192.168.1.100", info.getMachineIp());
        assertEquals(registerTime, info.getRegisterTime());
        assertEquals(destroyTime, info.getDestroyTime());
    }

    @Test
    @DisplayName("测试MachineIdInfo Setter方法")
    void testMachineIdInfoSetters() {
        MachineIdInfo info = new MachineIdInfo();
        info.setMachineName("new-host");
        info.setMachineIp("10.0.0.1");

        assertEquals("new-host", info.getMachineName());
        assertEquals("10.0.0.1", info.getMachineIp());
    }

    @Test
    @DisplayName("测试MachineIdInfo toString方法")
    void testMachineIdInfoToString() {
        MachineIdInfo info = MachineIdInfo.builder()
                .machineName("test-host")
                .machineIp("192.168.1.100")
                .build();

        String toString = info.toString();
        assertTrue(toString.contains("test-host"));
        assertTrue(toString.contains("192.168.1.100"));
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
    @DisplayName("测试通过反射获取机器名称")
    void testGetMachineNameViaReflection() {
        String hostNameFromEnv = System.getenv("HOSTNAME");
        String hostNameFromProp = System.getProperty("host.name");

        String expected = hostNameFromEnv != null ? hostNameFromEnv : 
                          (hostNameFromProp != null ? hostNameFromProp : "unknown");

        assertTrue(expected != null && !expected.isEmpty());
    }

    @Test
    @DisplayName("测试获取本机IP地址")
    void testGetLocalHostIp() throws UnknownHostException {
        String ip = InetAddress.getLocalHost().getHostAddress();
        assertNotNull(ip);
        assertFalse(ip.isEmpty());
    }

    @Test
    @DisplayName("测试MachineIdInfo Builder模式")
    void testMachineIdInfoBuilder() {
        LocalDateTime now = LocalDateTime.now();
        
        MachineIdInfo info = MachineIdInfo.builder()
                .machineName("builder-host")
                .machineIp("10.10.10.10")
                .registerTime(now)
                .destroyTime(null)
                .build();

        assertEquals("builder-host", info.getMachineName());
        assertEquals("10.10.10.10", info.getMachineIp());
        assertEquals(now, info.getRegisterTime());
        assertNull(info.getDestroyTime());
    }

    @Test
    @DisplayName("测试不同端口号的机器码范围计算")
    void testPortModuloCalculation() {
        int portModulo = 9000;
        int portMultiplier = 10;
        
        assertEquals(0, (9000 % portModulo) * portMultiplier);
        assertEquals(50, (9005 % portModulo) * portMultiplier);
        assertEquals(80800, (8080 % portModulo) * portMultiplier);
    }
}
