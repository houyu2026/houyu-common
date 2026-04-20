package com.houyu.common.log.utils.globalid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("时间戳生成器测试")
class GeneratorTimestampTest {

    private GeneratorTimestamp generatorTimestamp;
    private GeneratorSequence generatorSequence;

    @BeforeEach
    void setUp() {
        generatorTimestamp = new GeneratorTimestamp();
        generatorSequence = new GeneratorSequence();
    }

    @Test
    @DisplayName("测试常量定义")
    void testConstants() {
        assertEquals(10, GeneratorTimestamp.TIME_PART_LENGTH);
        assertEquals(60, GeneratorTimestamp.BASE_MINUTE);
        assertEquals(100L, GeneratorTimestamp.TIME_DIVISOR);
    }

    @Test
    @DisplayName("测试时间偏移量计算")
    void testCalculateTimePartWithOffset() {
        long baseTime = 2604201430L;

        long timeWithOffset1 = generatorTimestamp.calculateTimePartWithOffset(baseTime, 1);
        long timeWithOffset2 = generatorTimestamp.calculateTimePartWithOffset(baseTime, 2);
        long timeWithOffset3 = generatorTimestamp.calculateTimePartWithOffset(baseTime, 3);

        assertEquals(2604201460L, timeWithOffset1, "偏移1应将分钟改为60");
        assertEquals(2604201461L, timeWithOffset2, "偏移2应将分钟改为61");
        assertEquals(2604201462L, timeWithOffset3, "偏移3应将分钟改为62");
    }

    @Test
    @DisplayName("测试时间偏移量计算 - 不同基础时间")
    void testCalculateTimePartWithOffset_DifferentBaseTime() {
        long baseTime1 = 2512312359L;
        long result1 = generatorTimestamp.calculateTimePartWithOffset(baseTime1, 5);
        assertEquals(2512312364L, result1);

        long baseTime2 = 2601010000L;
        long result2 = generatorTimestamp.calculateTimePartWithOffset(baseTime2, 10);
        assertEquals(2601010069L, result2);
    }

    @Test
    @DisplayName("测试获取时间锁")
    void testGetTimeLock() {
        ReentrantLock lock = generatorTimestamp.getTimeLock();
        assertNotNull(lock);
    }

    @Test
    @DisplayName("测试获取时间偏移量")
    void testGetTimeOffset() {
        AtomicInteger offset = generatorTimestamp.getTimeOffset();
        assertNotNull(offset);
        assertEquals(0, offset.get(), "初始偏移量应为0");
    }

    @Test
    @DisplayName("测试获取当前时间部分")
    void testGetCurrentTimePart() {
        long current = generatorTimestamp.getCurrentTimePart();
        assertEquals(0L, current, "初始时间部分应为0");
    }

    @Test
    @DisplayName("测试设置当前时间部分")
    void testSetCurrentTimePart() {
        long testTime = 2604201430L;
        generatorTimestamp.setCurrentTimePart(testTime);
        assertEquals(testTime, generatorTimestamp.getCurrentTimePart());
    }

    @Test
    @DisplayName("测试重置时间偏移量")
    void testResetTimeOffset() {
        AtomicInteger offset = generatorTimestamp.getTimeOffset();
        offset.set(5);

        generatorTimestamp.resetTimeOffset();

        assertEquals(0, offset.get(), "重置后偏移量应为0");
    }

    @Test
    @DisplayName("测试增加时间偏移量")
    void testIncrementTimeOffset() {
        long baseTime = 2604201430L;

        int offset1 = generatorTimestamp.incrementTimeOffset(baseTime);
        assertEquals(1, offset1);
        assertEquals(2604201460L, generatorTimestamp.getCurrentTimePart());

        int offset2 = generatorTimestamp.incrementTimeOffset(baseTime);
        assertEquals(2, offset2);
        assertEquals(2604201461L, generatorTimestamp.getCurrentTimePart());
    }

    @Test
    @DisplayName("测试时间格式的组成")
    void testTimeFormatComposition() {
        long time = 2604201430L;

        long timeWithoutMinute = time / GeneratorTimestamp.TIME_DIVISOR;
        int minute = (int) (time % GeneratorTimestamp.TIME_DIVISOR);

        assertEquals(26042014L, timeWithoutMinute, "去除分钟后应为26042014");
        assertEquals(30, minute, "分钟部分应为30");
    }

    @Test
    @DisplayName("测试时间部分的长度")
    void testTimePartLength() {
        String timeStr = "2604201430";
        assertEquals(GeneratorTimestamp.TIME_PART_LENGTH, timeStr.length());
    }
}
