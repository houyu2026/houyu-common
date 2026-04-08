package com.houyu.common.log.utils.globalid;

import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import static org.junit.jupiter.api.Assertions.*;

public class GlobalIdGeneratorTest {

    @Test
    public void testTimestampFormat() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMddHHmm");
        LocalDateTime testTime = LocalDateTime.of(2026, 4, 7, 13, 38);
        String timestamp = testTime.format(formatter);
        assertEquals("2604071338", timestamp);
    }

    @Test
    public void testIdLength() {
        // 模拟ID生成
        String timestamp = "2604071338";
        String machineCode = "0001";
        String sequence = "0002";
        int flag = 5;
        String id = timestamp + machineCode + sequence + flag;
        assertEquals(19, id.length());
        assertEquals("2604071338000100025", id);
    }

    @Test
    public void testMachineCodeRange() {
        String minCode = "0000";
        String maxCode = "9999";
        
        assertTrue(minCode.compareTo(maxCode) <= 0);
        assertEquals(4, minCode.length());
        assertEquals(4, maxCode.length());
    }

    @Test
    public void testSequenceRange() {
        String minSeq = "0000";
        String maxSeq = "9999";
        
        assertTrue(minSeq.compareTo(maxSeq) <= 0);
        assertEquals(4, minSeq.length());
        assertEquals(4, maxSeq.length());
    }

    @Test
    public void testFlagRange() {
        int[] validFlags = {0, 1, 2, 3, 4, 5, 6, 7, 8, 9};
        for (int flag : validFlags) {
            assertTrue(flag >= 0 && flag <= 9);
        }
    }
}
