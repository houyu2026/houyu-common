package com.houyu.common.log.utils.globalid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("自增序列生成器测试")
class GeneratorSequenceTest {

    private GeneratorSequence generatorSequence;

    @BeforeEach
    void setUp() {
        generatorSequence = new GeneratorSequence();
    }

    @Test
    @DisplayName("测试序列生成的唯一性")
    void testGetNextSequence_Uniqueness() {
        Set<Integer> sequences = new HashSet<>();
        int count = 1000;

        for (int i = 0; i < count; i++) {
            int seq = generatorSequence.getNextSequence();
            sequences.add(seq);
        }

        assertEquals(count, sequences.size(), "所有序列值应唯一");
    }

    @Test
    @DisplayName("测试序列生成的连续性")
    void testGetNextSequence_Continuity() {
        int first = generatorSequence.getNextSequence();
        int second = generatorSequence.getNextSequence();
        int third = generatorSequence.getNextSequence();

        assertEquals(first + 1, second, "序列应连续递增");
        assertEquals(second + 1, third, "序列应连续递增");
    }

    @Test
    @DisplayName("测试序列是否溢出判断")
    void testIsOverflow() {
        assertFalse(generatorSequence.isOverflow(0), "0不应溢出");
        assertFalse(generatorSequence.isOverflow(9999), "9999不应溢出");
        assertTrue(generatorSequence.isOverflow(10000), "10000应溢出");
        assertTrue(generatorSequence.isOverflow(99999), "99999应溢出");
    }

    @Test
    @DisplayName("测试序列重置")
    void testReset() {
        for (int i = 0; i < 100; i++) {
            generatorSequence.getNextSequence();
        }

        generatorSequence.reset();

        assertEquals(0, generatorSequence.getCurrentSequence(), "重置后序列应为0");
    }

    @Test
    @DisplayName("测试获取当前序列值")
    void testGetCurrentSequence() {
        assertEquals(0, generatorSequence.getCurrentSequence(), "初始序列应为0");

        generatorSequence.getNextSequence();
        assertEquals(1, generatorSequence.getCurrentSequence(), "生成后序列应为1");
    }

    @Test
    @DisplayName("测试获取时间锁")
    void testGetTimeLock() {
        ReentrantLock lock = generatorSequence.getTimeLock();
        assertNotNull(lock, "时间锁不应为null");
    }

    @Test
    @DisplayName("测试常量定义")
    void testConstants() {
        assertEquals(4, GeneratorSequence.SEQUENCE_LENGTH);
        assertEquals(9999, GeneratorSequence.MAX_SEQUENCE);
    }

    @Test
    @DisplayName("测试大量序列生成")
    void testLargeSequenceGeneration() {
        int count = 5000;
        Set<Integer> sequences = new HashSet<>(count);

        for (int i = 0; i < count; i++) {
            int seq = generatorSequence.getNextSequence();
            sequences.add(seq);
        }

        assertEquals(count, sequences.size(), "大量生成的序列应全部唯一");
    }

    @Test
    @DisplayName("测试序列从任意位置开始")
    void testSequenceFromArbitraryPosition() {
        GeneratorSequence seq = new GeneratorSequence();

        for (int i = 0; i < 500; i++) {
            seq.getNextSequence();
        }

        int current = seq.getCurrentSequence();
        assertEquals(500, current, "500次生成后应为500");

        int next = seq.getNextSequence();
        assertEquals(500, next, "第501次生成应返回500");
        assertEquals(501, seq.getCurrentSequence(), "当前值应为501");
    }
}
