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
@DisplayName("机器码生成器测试")
class GeneratorMachineIdTest {

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @Mock
    private RBucket<Object> bucket;

    private GlobalIdProperties properties;
    private GeneratorMachineId generatorMachineId;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new GlobalIdProperties();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        generatorMachineId = new GeneratorMachineId(redissonClient, properties, objectMapper);

        ReflectionTestUtils.setField(generatorMachineId, "serviceName", "test-service");
        ReflectionTestUtils.setField(generatorMachineId, "serverPort", 9000);
    }

    @Test
    @DisplayName("测试初始状态")
    void testInitialState() {
        assertEquals(-1, generatorMachineId.getMachineId());
        assertFalse(generatorMachineId.isBackup());
    }

    @Test
    @DisplayName("测试Redis键生成规则")
    void testRedisKeyGeneration() {
        String mainKey = GeneratorMachineId.getMachineIdKey("test-service", 1234, false);
        String backupKey = GeneratorMachineId.getMachineIdKey("test-service", 9500, true);
        String lockKey = GeneratorMachineId.getLockKey("test-service");

        assertEquals("machine_id_test-service_1234", mainKey);
        assertEquals("machine_id_bak_test-service_9500", backupKey);
        assertEquals("machine_id_test-service_lock", lockKey);
    }

    @Test
    @DisplayName("测试机器码信息序列化")
    void testMachineIdInfoSerialization() throws Exception {
        RedisMachineInfoDto.MachineInfoDto info = RedisMachineInfoDto.MachineInfoDto.builder()
                .machineId("0001")
                .machineName("test-host")
                .machineIp("192.168.1.100")
                .registerTime(LocalDateTime.of(2026, 4, 20, 14, 30, 0, 123000000))
                .destroyTime(null)
                .build();

        String json = objectMapper.writeValueAsString(info);
        RedisMachineInfoDto.MachineInfoDto parsed = objectMapper.readValue(json, RedisMachineInfoDto.MachineInfoDto.class);

        assertEquals(info.getMachineId(), parsed.getMachineId());
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
            generatorMachineId.initialize();
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
            generatorMachineId.initialize();
        });
    }

    @Test
    @DisplayName("测试重复初始化不执行重复注册")
    void testInitialize_AlreadyRegistered() throws Exception {
        ReflectionTestUtils.setField(generatorMachineId, "registered", true);
        ReflectionTestUtils.setField(generatorMachineId, "machineId", 1234);

        generatorMachineId.initialize();

        verify(redissonClient, never()).getLock(anyString());
        assertEquals(1234, generatorMachineId.getMachineId());
    }

    @Test
    @DisplayName("测试未注册时destroy不执行任何操作")
    void testDestroy_NotRegistered() {
        ReflectionTestUtils.setField(generatorMachineId, "registered", false);
        ReflectionTestUtils.setField(generatorMachineId, "machineId", -1);

        generatorMachineId.destroy();

        verify(redissonClient, never()).getBucket(anyString());
    }

    @Test
    @DisplayName("测试已注册但machineId为-1时destroy不执行")
    void testDestroy_RegisteredButNoMachineId() {
        ReflectionTestUtils.setField(generatorMachineId, "registered", true);
        ReflectionTestUtils.setField(generatorMachineId, "machineId", -1);

        generatorMachineId.destroy();

        verify(redissonClient, never()).getBucket(anyString());
    }

    @Test
    @DisplayName("测试已注册但redis无数据时destroy不执行更新")
    void testDestroy_NoDataInRedis() {
        ReflectionTestUtils.setField(generatorMachineId, "registered", true);
        ReflectionTestUtils.setField(generatorMachineId, "machineId", 1234);
        ReflectionTestUtils.setField(generatorMachineId, "isBackup", false);
        ReflectionTestUtils.setField(generatorMachineId, "serviceName", "test-service");

        when(redissonClient.getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(null);

        generatorMachineId.destroy();

        verify(bucket, never()).set(any());
    }

    @Test
    @DisplayName("测试使用备用段时destroy正常执行")
    void testDestroy_WithBackupMachineId() throws Exception {
        ReflectionTestUtils.setField(generatorMachineId, "registered", true);
        ReflectionTestUtils.setField(generatorMachineId, "machineId", 9500);
        ReflectionTestUtils.setField(generatorMachineId, "isBackup", true);
        ReflectionTestUtils.setField(generatorMachineId, "serviceName", "test-service");

        RedisMachineInfoDto.MachineInfoDto existingInfo = RedisMachineInfoDto.MachineInfoDto.builder()
                .machineId("9500")
                .machineName("test-host")
                .machineIp("192.168.1.100")
                .registerTime(LocalDateTime.now())
                .destroyTime(null)
                .build();

        when(redissonClient.getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn(objectMapper.writeValueAsString(existingInfo));

        generatorMachineId.destroy();

        verify(bucket, times(1)).set(anyString());
    }

    @Test
    @DisplayName("测试destroy时JSON解析失败的异常处理")
    void testDestroy_JsonParseException() {
        ReflectionTestUtils.setField(generatorMachineId, "registered", true);
        ReflectionTestUtils.setField(generatorMachineId, "machineId", 1234);
        ReflectionTestUtils.setField(generatorMachineId, "isBackup", false);
        ReflectionTestUtils.setField(generatorMachineId, "serviceName", "test-service");

        when(redissonClient.getBucket(anyString())).thenReturn(bucket);
        when(bucket.get()).thenReturn("invalid json");

        generatorMachineId.destroy();

        verify(bucket, never()).set(any());
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
                "0001",
                "test-host",
                "192.168.1.100",
                registerTime,
                destroyTime
        );

        assertEquals("0001", info.getMachineId());
        assertEquals("test-host", info.getMachineName());
        assertEquals("192.168.1.100", info.getMachineIp());
        assertEquals(registerTime, info.getRegisterTime());
        assertEquals(destroyTime, info.getDestroyTime());
    }

    @Test
    @DisplayName("测试MachineInfoDto Setter方法")
    void testMachineInfoDtoSetters() {
        RedisMachineInfoDto.MachineInfoDto info = new RedisMachineInfoDto.MachineInfoDto();
        info.setMachineName("new-host");
        info.setMachineIp("10.0.0.1");
        info.setMachineId("0099");

        assertEquals("0099", info.getMachineId());
        assertEquals("new-host", info.getMachineName());
        assertEquals("10.0.0.1", info.getMachineIp());
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
    @DisplayName("测试RedisMachineInfoDto构造函数")
    void testRedisMachineInfoDtoConstructor() {
        RedisMachineInfoDto.MachineInfoDto value = RedisMachineInfoDto.MachineInfoDto.builder()
                .machineId("0001")
                .build();

        RedisMachineInfoDto dto = new RedisMachineInfoDto("test-key", value);

        assertEquals("test-key", dto.getRedisKey());
        assertEquals("0001", dto.getRedisValue().getMachineId());
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
    @DisplayName("测试MachineInfoDto Builder模式")
    void testMachineInfoDtoBuilder() {
        LocalDateTime now = LocalDateTime.now();

        RedisMachineInfoDto.MachineInfoDto info = RedisMachineInfoDto.MachineInfoDto.builder()
                .machineId("0005")
                .machineName("builder-host")
                .machineIp("10.10.10.10")
                .registerTime(now)
                .destroyTime(null)
                .build();

        assertEquals("0005", info.getMachineId());
        assertEquals("builder-host", info.getMachineName());
        assertEquals("10.10.10.10", info.getMachineIp());
        assertEquals(now, info.getRegisterTime());
        assertNull(info.getDestroyTime());
    }

    @Test
    @DisplayName("测试不同端口号的机器码范围计算")
    void testPortModuloCalculation() {
        int portModulo = GeneratorMachineId.PORT_MODULO;
        int portMultiplier = GeneratorMachineId.PORT_MULTIPLIER;

        assertEquals(0, (9000 % portModulo) * portMultiplier);
        assertEquals(50, (9005 % portModulo) * portMultiplier);
        assertEquals(80800, (8080 % portModulo) * portMultiplier);
    }

    @Test
    @DisplayName("测试机器码字符串格式化")
    void testMachineIdStringFormat() {
        assertEquals("0000", String.format(GeneratorMachineId.MACHINE_ID_FORMAT, 0));
        assertEquals("0001", String.format(GeneratorMachineId.MACHINE_ID_FORMAT, 1));
        assertEquals("0010", String.format(GeneratorMachineId.MACHINE_ID_FORMAT, 10));
        assertEquals("0100", String.format(GeneratorMachineId.MACHINE_ID_FORMAT, 100));
        assertEquals("1000", String.format(GeneratorMachineId.MACHINE_ID_FORMAT, 1000));
        assertEquals("9999", String.format(GeneratorMachineId.MACHINE_ID_FORMAT, 9999));
    }

    @Test
    @DisplayName("测试getMachineIdString方法")
    void testGetMachineIdString() {
        ReflectionTestUtils.setField(generatorMachineId, "machineId", 123);
        assertEquals("0123", generatorMachineId.getMachineIdString());

        ReflectionTestUtils.setField(generatorMachineId, "machineId", 9999);
        assertEquals("9999", generatorMachineId.getMachineIdString());
    }
}
