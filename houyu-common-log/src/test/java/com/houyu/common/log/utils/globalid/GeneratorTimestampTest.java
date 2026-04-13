package com.houyu.common.log.utils.globalid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorTimestampTest {

    private GeneratorTimestamp generatorTimestamp;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyMMddHHmm");

    @BeforeEach
    void setUp() {
        generatorTimestamp = new GeneratorTimestamp();
    }

    @Test
    void testGenerateTimestamp() {
        String timestamp = generatorTimestamp.generateTimestamp();
        assertNotNull(timestamp);
        assertEquals(10, timestamp.length());

        LocalDateTime now = LocalDateTime.now();
        String expected = now.format(TIME_FORMATTER);
        assertEquals(expected, timestamp);
    }

    @Test
    void testGenerateTimestampFormat() {
        LocalDateTime testTime = LocalDateTime.of(2026, 4, 7, 13, 38);
        String expected = testTime.format(TIME_FORMATTER);
        assertEquals("2604071338", expected);
    }

    @Test
    void testAdjustTimestampCount0() {
        String originalTimestamp = "2604081359";
        String adjusted = generatorTimestamp.adjustTimestamp(originalTimestamp, 0);
        assertEquals("2604081360", adjusted);
    }

    @Test
    void testAdjustTimestampCount1() {
        String originalTimestamp = "2604081359";
        String adjusted = generatorTimestamp.adjustTimestamp(originalTimestamp, 1);
        assertEquals("2604081361", adjusted);
    }

    @Test
    void testAdjustTimestampCount5() {
        String originalTimestamp = "2604081359";
        String adjusted = generatorTimestamp.adjustTimestamp(originalTimestamp, 5);
        assertEquals("2604081365", adjusted);
    }

    @Test
    void testAdjustTimestampMaxAdjustment() {
        String originalTimestamp = "2604081359";
        String adjusted = generatorTimestamp.adjustTimestamp(originalTimestamp, 39);
        assertEquals("2604081399", adjusted);
    }

    @Test
    void testAdjustTimestampExceedsMax() {
        String originalTimestamp = "2604081359";
        String adjusted = generatorTimestamp.adjustTimestamp(originalTimestamp, 40);
        assertNull(adjusted);
    }

    @Test
    void testAdjustTimestampExceedsMaxBy1() {
        String originalTimestamp = "2604081359";
        String adjusted = generatorTimestamp.adjustTimestamp(originalTimestamp, 41);
        assertNull(adjusted);
    }

    @Test
    void testAdjustTimestampPreservesBase() {
        String originalTimestamp = "2604071338";
        String adjusted = generatorTimestamp.adjustTimestamp(originalTimestamp, 0);
        assertTrue(adjusted.startsWith("26040713"));
        assertEquals("2604071360", adjusted);
    }

    @Test
    void testAdjustTimestampBoundary() {
        String originalTimestamp = "2612312359";
        String adjusted = generatorTimestamp.adjustTimestamp(originalTimestamp, 0);
        assertEquals("2612312360", adjusted);
    }
}
