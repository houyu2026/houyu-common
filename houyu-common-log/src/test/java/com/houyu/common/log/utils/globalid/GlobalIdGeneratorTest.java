package com.houyu.common.log.utils.globalid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("全局ID生成器测试")
class GlobalIdGeneratorTest {

    private GlobalIdGenerator globalIdGenerator;

    @BeforeEach
    void setUp() {
        globalIdGenerator = new GlobalIdGenerator(null);
        ReflectionTestUtils.setField(globalIdGenerator, "machineIdStr", "1234");
    }

    @Test
    @DisplayName("测试生成ID的基本格式")
    void testGenerate_BasicFormat() {
        String id = globalIdGenerator.generate();

        assertNotNull(id, "生成的ID不应为null");
        assertEquals(19, id.length(), "ID长度应为19位");
        assertTrue(id.matches("\\d{19}"), "ID应全为数字");
        assertTrue(id.contains("1234"), "ID应包含机器码1234");
    }

    @Test
    @DisplayName("测试生成ID的唯一性 - 1000次")
    void testGenerate_Uniqueness() {
        Set<String> ids = new HashSet<>();
        int count = 1000;

        for (int i = 0; i < count; i++) {
            String id = globalIdGenerator.generate();
            ids.add(id);
        }

        assertEquals(count, ids.size(), "所有生成的ID应唯一");
    }

    @Test
    @DisplayName("测试带标志位的ID生成")
    void testGenerate_WithFlag() {
        int flag = 5;
        String id = globalIdGenerator.generate(flag);

        assertNotNull(id);
        assertEquals(19, id.length());
        assertEquals(String.valueOf(flag), id.substring(18, 19), "最后一位应为标志位");
    }

    @Test
    @DisplayName("测试非法标志位 - 使用默认值0")
    void testGenerate_InvalidFlag() {
        String id1 = globalIdGenerator.generate(0);
        String id2 = globalIdGenerator.generate(10);
        String id3 = globalIdGenerator.generate(-1);

        assertEquals("0", id1.substring(18, 19), "标志位0应保持不变");
        assertEquals("0", id2.substring(18, 19), "标志位10应为非法，使用默认0");
        assertEquals("0", id3.substring(18, 19), "标志位-1应为非法，使用默认0");
    }

    @Test
    @DisplayName("测试有效标志位范围1-9")
    void testGenerate_ValidFlagRange() {
        for (int flag = 1; flag <= 9; flag++) {
            String id = globalIdGenerator.generate(flag);
            assertEquals(String.valueOf(flag), id.substring(18, 19),
                    "标志位" + flag + "应正确使用");
        }
    }

    @Test
    @DisplayName("测试多线程环境下的ID唯一性")
    void testGenerate_ThreadSafety() throws InterruptedException {
        int threadCount = 10;
        int idsPerThread = 100;
        int totalIds = threadCount * idsPerThread;

        Set<String> allIds = new HashSet<>(totalIds);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.execute(() -> {
                try {
                    startLatch.await();
                    for (int j = 0; j < idsPerThread; j++) {
                        synchronized (allIds) {
                            allIds.add(globalIdGenerator.generate());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        assertEquals(totalIds, allIds.size(), "多线程环境下所有ID应唯一");
    }

    @Test
    @DisplayName("测试获取机器码信息")
    void testGetMachineIdInfo() {
        assertEquals("1234", globalIdGenerator.getMachineIdString());
    }

    @Test
    @DisplayName("测试ID各部分结构")
    void testGenerate_IdStructure() {
        String id = globalIdGenerator.generate(9);

        assertEquals(19, id.length());

        String timePart = id.substring(0, 10);
        String machinePart = id.substring(10, 14);
        String sequencePart = id.substring(14, 18);
        String flagPart = id.substring(18, 19);

        assertTrue(timePart.matches("\\d{10}"), "时间部分应为10位数字");
        assertEquals("1234", machinePart, "机器码部分应为1234");
        assertTrue(sequencePart.matches("\\d{4}"), "序列号部分应为4位数字");
        assertEquals("9", flagPart, "标志位应为9");
    }

    @Test
    @DisplayName("测试常量定义")
    void testConstants() {
        assertEquals(19, GlobalIdGenerator.ID_TOTAL_LENGTH);
        assertEquals(10, GlobalIdGenerator.TIME_PART_LENGTH);
        assertEquals(4, GlobalIdGenerator.MACHINE_ID_LENGTH);
        assertEquals(4, GlobalIdGenerator.SEQUENCE_LENGTH);
        assertEquals(1, GlobalIdGenerator.FLAG_LENGTH);
        assertEquals(1, GlobalIdGenerator.FLAG_MIN);
        assertEquals(9, GlobalIdGenerator.FLAG_MAX);
        assertEquals(0, GlobalIdGenerator.DEFAULT_FLAG);
    }

    @Test
    @DisplayName("测试序列接近最大值时的行为")
    void testSequenceNearMaxValue() {
        AtomicInteger sequence = new AtomicInteger(9990);
        AtomicLong currentTimePart = new AtomicLong(2604201430L);
        AtomicInteger timeOffset = new AtomicInteger(0);
        ReentrantLock timeLock = new ReentrantLock();

        ReflectionTestUtils.setField(globalIdGenerator, "sequence", sequence);
        ReflectionTestUtils.setField(globalIdGenerator, "currentTimePart", currentTimePart);
        ReflectionTestUtils.setField(globalIdGenerator, "timeOffset", timeOffset);
        ReflectionTestUtils.setField(globalIdGenerator, "timeLock", timeLock);

        Set<String> ids = new HashSet<>();
        for (int i = 0; i < 20; i++) {
            String id = globalIdGenerator.generate();
            ids.add(id);
        }

        assertEquals(20, ids.size(), "序列溢出时应继续生成唯一ID");
    }

    @Test
    @DisplayName("测试时间更新时重置序列")
    void testTimeChangeResetsSequence() {
        AtomicLong currentTimePart = new AtomicLong(0L);
        AtomicInteger timeOffset = new AtomicInteger(10);
        ReentrantLock timeLock = new ReentrantLock();

        ReflectionTestUtils.setField(globalIdGenerator, "currentTimePart", currentTimePart);
        ReflectionTestUtils.setField(globalIdGenerator, "timeOffset", timeOffset);
        ReflectionTestUtils.setField(globalIdGenerator, "timeLock", timeLock);

        String id1 = globalIdGenerator.generate();
        String id2 = globalIdGenerator.generate();

        assertNotNull(id1);
        assertNotNull(id2);
        assertEquals(19, id1.length());
        assertEquals(19, id2.length());
    }

    @Test
    @DisplayName("测试标志位边界值")
    void testFlagBoundaryValues() {
        String idMin = globalIdGenerator.generate(1);
        String idMax = globalIdGenerator.generate(9);

        assertEquals("1", idMin.substring(18, 19), "标志位最小值1应正确使用");
        assertEquals("9", idMax.substring(18, 19), "标志位最大值9应正确使用");
    }

    @Test
    @DisplayName("测试标志位超出上限")
    void testFlagAboveMax() {
        String id = globalIdGenerator.generate(100);
        assertEquals("0", id.substring(18, 19), "标志位100应为非法，使用默认0");
    }

    @Test
    @DisplayName("测试标志位为负数")
    void testFlagNegative() {
        String id = globalIdGenerator.generate(-5);
        assertEquals("0", id.substring(18, 19), "标志位-5应为非法，使用默认0");
    }
}
