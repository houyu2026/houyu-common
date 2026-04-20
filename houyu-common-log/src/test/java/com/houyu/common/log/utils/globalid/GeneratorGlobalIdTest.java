package com.houyu.common.log.utils.globalid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("全局ID生成器测试")
class GeneratorGlobalIdTest {

    private GeneratorGlobalId generatorGlobalId;
    private GeneratorSequence generatorSequence;
    private GeneratorTimestamp generatorTimestamp;

    @BeforeEach
    void setUp() {
        generatorSequence = new GeneratorSequence();
        generatorTimestamp = new GeneratorTimestamp();
        generatorGlobalId = new GeneratorGlobalId(null, generatorSequence, generatorTimestamp);
        ReflectionTestUtils.setField(generatorGlobalId, "machineIdStr", "1234");
    }

    @Test
    @DisplayName("测试生成ID的基本格式")
    void testGenerate_BasicFormat() {
        String id = generatorGlobalId.generate();

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
            String id = generatorGlobalId.generate();
            ids.add(id);
        }

        assertEquals(count, ids.size(), "所有生成的ID应唯一");
    }

    @Test
    @DisplayName("测试带标志位的ID生成")
    void testGenerate_WithFlag() {
        int flag = 5;
        String id = generatorGlobalId.generate(flag);

        assertNotNull(id);
        assertEquals(19, id.length());
        assertEquals(String.valueOf(flag), id.substring(18, 19), "最后一位应为标志位");
    }

    @Test
    @DisplayName("测试非法标志位 - 使用默认值0")
    void testGenerate_InvalidFlag() {
        String id1 = generatorGlobalId.generate(0);
        String id2 = generatorGlobalId.generate(10);
        String id3 = generatorGlobalId.generate(-1);

        assertEquals("0", id1.substring(18, 19), "标志位0应保持不变");
        assertEquals("0", id2.substring(18, 19), "标志位10应为非法，使用默认0");
        assertEquals("0", id3.substring(18, 19), "标志位-1应为非法，使用默认0");
    }

    @Test
    @DisplayName("测试有效标志位范围1-9")
    void testGenerate_ValidFlagRange() {
        for (int flag = 1; flag <= 9; flag++) {
            String id = generatorGlobalId.generate(flag);
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
                            allIds.add(generatorGlobalId.generate());
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
        assertEquals("1234", generatorGlobalId.getMachineIdString());
    }

    @Test
    @DisplayName("测试ID各部分结构")
    void testGenerate_IdStructure() {
        String id = generatorGlobalId.generate(9);

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
        assertEquals(19, GeneratorGlobalId.ID_TOTAL_LENGTH);
        assertEquals(1, GeneratorGlobalId.FLAG_MIN);
        assertEquals(9, GeneratorGlobalId.FLAG_MAX);
        assertEquals(0, GeneratorGlobalId.DEFAULT_FLAG);
    }

    @Test
    @DisplayName("测试标志位边界值")
    void testFlagBoundaryValues() {
        String idMin = generatorGlobalId.generate(1);
        String idMax = generatorGlobalId.generate(9);

        assertEquals("1", idMin.substring(18, 19), "标志位最小值1应正确使用");
        assertEquals("9", idMax.substring(18, 19), "标志位最大值9应正确使用");
    }

    @Test
    @DisplayName("测试标志位超出上限")
    void testFlagAboveMax() {
        String id = generatorGlobalId.generate(100);
        assertEquals("0", id.substring(18, 19), "标志位100应为非法，使用默认0");
    }

    @Test
    @DisplayName("测试标志位为负数")
    void testFlagNegative() {
        String id = generatorGlobalId.generate(-5);
        assertEquals("0", id.substring(18, 19), "标志位-5应为非法，使用默认0");
    }

    @Test
    @DisplayName("测试批量生成ID - 默认标志位")
    void testGenerateBatch_DefaultFlag() {
        int count = 100;
        List<String> ids = generatorGlobalId.generateBatch(count);

        assertEquals(count, ids.size(), "应生成指定数量的ID");
        
        Set<String> uniqueIds = new HashSet<>(ids);
        assertEquals(count, uniqueIds.size(), "批量生成的ID应全部唯一");

        for (String id : ids) {
            assertEquals(19, id.length(), "每个ID长度应为19位");
            assertEquals("0", id.substring(18, 19), "默认标志位应为0");
        }
    }

    @Test
    @DisplayName("测试批量生成ID - 带标志位")
    void testGenerateBatch_WithFlag() {
        int count = 50;
        int flag = 5;
        List<String> ids = generatorGlobalId.generateBatch(count, flag);

        assertEquals(count, ids.size(), "应生成指定数量的ID");

        for (String id : ids) {
            assertEquals(19, id.length(), "每个ID长度应为19位");
            assertEquals(String.valueOf(flag), id.substring(18, 19), "标志位应为" + flag);
        }
    }

    @Test
    @DisplayName("测试批量生成ID - 数量为0抛出异常")
    void testGenerateBatch_ZeroCount() {
        assertThrows(IllegalArgumentException.class, () -> {
            generatorGlobalId.generateBatch(0);
        }, "生成数量为0应抛出异常");
    }

    @Test
    @DisplayName("测试批量生成ID - 数量为负数抛出异常")
    void testGenerateBatch_NegativeCount() {
        assertThrows(IllegalArgumentException.class, () -> {
            generatorGlobalId.generateBatch(-1);
        }, "生成数量为负数应抛出异常");
    }

    @Test
    @DisplayName("测试批量生成ID的唯一性")
    void testGenerateBatch_Uniqueness() {
        int count = 1000;
        List<String> ids = generatorGlobalId.generateBatch(count, 3);

        assertEquals(count, ids.size());

        Set<String> uniqueIds = new HashSet<>(ids);
        assertEquals(count, uniqueIds.size(), "批量生成的" + count + "个ID应全部唯一");
    }
}
