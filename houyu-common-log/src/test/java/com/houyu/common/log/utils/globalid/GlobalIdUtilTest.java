package com.houyu.common.log.utils.globalid;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestPropertySource(locations = "classpath:application-test.yml")
public class GlobalIdUtilTest {

    @Autowired
    private GlobalIdUtil globalIdUtil;

    @Autowired
    private GeneratorMachineId generatorMachineId;

    @Autowired
    private GeneratorTimestamp generatorTimestamp;

    @Test
    public void testTimestampFormat() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyMMddHHmm");
        LocalDateTime testTime = LocalDateTime.of(2026, 4, 7, 13, 38);
        String timestamp = testTime.format(formatter);
        assertEquals("2604071338", timestamp);
    }

    @Test
    public void testIdLength() {
        // 测试生成的ID长度
        String id = globalIdUtil.nextId();
        assertEquals(19, id.length());
        
        // 测试带标志位的ID长度
        String idWithFlag = globalIdUtil.nextIdWithFlag(5);
        assertEquals(19, idWithFlag.length());
    }

    @Test
    public void testIdStructure() {
        String id = globalIdUtil.nextId();
        
        // 验证ID结构：10位时间戳 + 4位机器码 + 4位序列 + 1位标志位
        assertTrue(id.length() == 19);
        
        // 验证时间戳部分
        String timestamp = id.substring(0, 10);
        assertEquals(10, timestamp.length());
        
        // 验证机器码部分
        String machineCode = id.substring(10, 14);
        assertEquals(4, machineCode.length());
        
        // 验证序列部分
        String sequence = id.substring(14, 18);
        assertEquals(4, sequence.length());
        
        // 验证标志位部分
        String flag = id.substring(18, 19);
        assertEquals(1, flag.length());
    }

    @Test
    public void testMachineCodeRange() {
        String machineCode = generatorMachineId.getMachineId();
        assertNotNull(machineCode);
        assertEquals(4, machineCode.length());
        
        int code = Integer.parseInt(machineCode);
        assertTrue(code >= 0 && code <= 9999);
    }

    @Test
    public void testFlagRange() {
        // 测试有效标志位
        for (int i = 1; i <= 9; i++) {
            String id = globalIdUtil.nextIdWithFlag(i);
            assertEquals(String.valueOf(i), id.substring(18, 19));
        }
        
        // 测试非法标志位，应该默认为0
        String idWithInvalidFlag = globalIdUtil.nextIdWithFlag(10);
        assertEquals("0", idWithInvalidFlag.substring(18, 19));
        
        String idWithNegativeFlag = globalIdUtil.nextIdWithFlag(-1);
        assertEquals("0", idWithNegativeFlag.substring(18, 19));
    }

    @Test
    public void testNextIdList() {
        int count = 5;
        List<String> ids = globalIdUtil.nextIdList(count);
        assertEquals(count, ids.size());
        
        // 验证所有ID长度都为19
        for (String id : ids) {
            assertEquals(19, id.length());
        }
    }

    @Test
    public void testNextIdListWithFlag() {
        int count = 3;
        int flag = 7;
        List<String> ids = globalIdUtil.nextIdListWithFlag(count, flag);
        assertEquals(count, ids.size());
        
        // 验证所有ID长度都为19且标志位正确
        for (String id : ids) {
            assertEquals(19, id.length());
            assertEquals(String.valueOf(flag), id.substring(18, 19));
        }
    }

    @Test
    public void testAdjustTimestamp() {
        String originalTimestamp = "2604081359";
        String adjustedTimestamp = generatorTimestamp.adjustTimestamp(originalTimestamp, 0);
        assertEquals("2604081360", adjustedTimestamp);
        
        // 测试多次调整
        String adjustedTimestamp2 = generatorTimestamp.adjustTimestamp(originalTimestamp, 1);
        assertEquals("2604081361", adjustedTimestamp2);
    }
}
