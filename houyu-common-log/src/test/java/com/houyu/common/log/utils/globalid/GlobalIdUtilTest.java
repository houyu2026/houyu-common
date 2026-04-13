package com.houyu.common.log.utils.globalid;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class GlobalIdUtilTest {

    @Autowired
    private GlobalIdUtil globalIdUtil;

    @Autowired
    private GeneratorMachineId generatorMachineId;

    @Autowired
    private GeneratorTimestamp generatorTimestamp;

    @Autowired
    private GeneratorSequence generatorSequence;

    @Test
    void testNextId() {
        String id = globalIdUtil.nextId();
        assertNotNull(id);
        assertEquals(19, id.length());
    }

    @Test
    void testNextIdStructure() {
        String id = globalIdUtil.nextId();

        String timestamp = id.substring(0, 10);
        String machineCode = id.substring(10, 14);
        String sequence = id.substring(14, 18);
        String flag = id.substring(18, 19);

        assertEquals(10, timestamp.length());
        assertEquals(4, machineCode.length());
        assertEquals(4, sequence.length());
        assertEquals(1, flag.length());

        assertTrue(timestamp.matches("\\d{10}"));
        assertTrue(machineCode.matches("\\d{4}"));
        assertTrue(sequence.matches("\\d{4}"));
        assertTrue(flag.matches("\\d{1}"));

        assertEquals("0", flag);
    }

    @Test
    void testNextIdWithFlagValid() {
        for (int flag = 1; flag <= 9; flag++) {
            String id = globalIdUtil.nextIdWithFlag(flag);
            assertNotNull(id);
            assertEquals(19, id.length());
            assertEquals(String.valueOf(flag), id.substring(18, 19));
        }
    }

    @Test
    void testNextIdWithFlagInvalid() {
        String idWithZero = globalIdUtil.nextIdWithFlag(0);
        assertEquals("0", idWithZero.substring(18, 19));

        String idWithNegative = globalIdUtil.nextIdWithFlag(-1);
        assertEquals("0", idWithNegative.substring(18, 19));

        String idWithOver9 = globalIdUtil.nextIdWithFlag(10);
        assertEquals("0", idWithOver9.substring(18, 19));

        String idWith100 = globalIdUtil.nextIdWithFlag(100);
        assertEquals("0", idWith100.substring(18, 19));
    }

    @Test
    void testNextIdList() {
        int count = 5;
        List<String> ids = globalIdUtil.nextIdList(count);
        assertNotNull(ids);
        assertEquals(count, ids.size());

        for (String id : ids) {
            assertEquals(19, id.length());
            assertEquals("0", id.substring(18, 19));
        }
    }

    @Test
    void testNextIdListWithFlag() {
        int count = 3;
        int flag = 7;
        List<String> ids = globalIdUtil.nextIdListWithFlag(count, flag);
        assertNotNull(ids);
        assertEquals(count, ids.size());

        for (String id : ids) {
            assertEquals(19, id.length());
            assertEquals(String.valueOf(flag), id.substring(18, 19));
        }
    }

    @Test
    void testNextIdUniqueness() {
        int count = 100;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String id = globalIdUtil.nextId();
            assertTrue(ids.add(id), "Duplicate ID detected: " + id);
        }
        assertEquals(count, ids.size());
    }

    @Test
    void testNextIdWithFlagUniqueness() {
        int count = 50;
        Set<String> ids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            String id = globalIdUtil.nextIdWithFlag(5);
            assertTrue(ids.add(id), "Duplicate ID detected: " + id);
        }
        assertEquals(count, ids.size());
    }

    @Test
    void testNextIdListUniqueness() {
        List<String> ids = globalIdUtil.nextIdList(100);
        Set<String> uniqueIds = new HashSet<>(ids);
        assertEquals(ids.size(), uniqueIds.size());
    }

    @Test
    void testMachineCodeInId() {
        String id = globalIdUtil.nextId();
        String machineCode = id.substring(10, 14);
        String expectedMachineId = generatorMachineId.getMachineId();
        assertEquals(expectedMachineId, machineCode);
    }

    @Test
    void testTimestampFormat() {
        String id = globalIdUtil.nextId();
        String timestamp = id.substring(0, 10);
        assertTrue(timestamp.matches("^[0-9]{10}$"));

        String currentTimestamp = generatorTimestamp.generateTimestamp();
        assertTrue(timestamp.compareTo(currentTimestamp) >= 0 || 
                  timestamp.startsWith(currentTimestamp.substring(0, 8)));
    }

    @Test
    void testSequenceInRange() {
        for (int i = 0; i < 10; i++) {
            String id = globalIdUtil.nextId();
            String sequence = id.substring(14, 18);
            int seq = Integer.parseInt(sequence);
            assertTrue(seq >= 0 && seq <= 9999, "Sequence out of range: " + seq);
        }
    }

    @Test
    void testNextIdWithDifferentFlags() {
        String id1 = globalIdUtil.nextIdWithFlag(1);
        String id2 = globalIdUtil.nextIdWithFlag(2);
        String id3 = globalIdUtil.nextIdWithFlag(3);

        assertEquals("1", id1.substring(18, 19));
        assertEquals("2", id2.substring(18, 19));
        assertEquals("3", id3.substring(18, 19));

        assertNotEquals(id1, id2);
        assertNotEquals(id2, id3);
        assertNotEquals(id1, id3);
    }

    @Test
    void testNextIdListWithInvalidFlag() {
        List<String> ids = globalIdUtil.nextIdListWithFlag(3, -1);
        for (String id : ids) {
            assertEquals("0", id.substring(18, 19));
        }
    }

    @Test
    void testNextIdListWithOver9Flag() {
        List<String> ids = globalIdUtil.nextIdListWithFlag(3, 99);
        for (String id : ids) {
            assertEquals("0", id.substring(18, 19));
        }
    }

    @Test
    void testGenerateSequenceWithTimestampAdjustment() throws Exception {
        GeneratorSequence testSequence = new GeneratorSequence();
        String timestamp = "2604081430";

        for (int i = 0; i < 9999; i++) {
            testSequence.nextSequence(timestamp);
        }
        assertNull(testSequence.nextSequence(timestamp));

        String adjusted = generatorTimestamp.adjustTimestamp(timestamp, 0);
        assertNotNull(adjusted);
        Integer seq = testSequence.nextSequence(adjusted);
        assertNotNull(seq);
        assertEquals(1, seq);
    }

    @Test
    void testGenerateSequenceSleepBranch() throws Exception {
        GeneratorSequence exhaustedSequence = new GeneratorSequence();
        String timestamp = "2604081430";
        for (int i = 0; i < 9999; i++) {
            exhaustedSequence.nextSequence(timestamp);
        }

        GeneratorSequence originalSequence = generatorSequence;
        try {
            Field sequenceField = GlobalIdUtil.class.getDeclaredField("generatorSequence");
            sequenceField.setAccessible(true);
            sequenceField.set(globalIdUtil, exhaustedSequence);

            ExecutorService executor = Executors.newSingleThreadExecutor();
            AtomicReference<String> result = new AtomicReference<>();
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch finishLatch = new CountDownLatch(1);

            executor.submit(() -> {
                startLatch.countDown();
                try {
                    String id = globalIdUtil.nextId();
                    result.set(id);
                } finally {
                    finishLatch.countDown();
                }
            });

            startLatch.await();
            Thread.sleep(500);

            Field sequenceField2 = GlobalIdUtil.class.getDeclaredField("generatorSequence");
            sequenceField2.setAccessible(true);
            sequenceField2.set(globalIdUtil, new GeneratorSequence());

            boolean finished = finishLatch.await(35, TimeUnit.SECONDS);
            assertTrue(finished, "The nextId call should complete within timeout");

            String id = result.get();
            assertNotNull(id);
            assertEquals(19, id.length());

            executor.shutdown();
        } finally {
            Field sequenceField = GlobalIdUtil.class.getDeclaredField("generatorSequence");
            sequenceField.setAccessible(true);
            sequenceField.set(globalIdUtil, originalSequence);
        }
    }
}
