package com.houyu.common.log.utils.globalid;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class GeneratorSequenceTest {

    private GeneratorSequence generatorSequence;

    @BeforeEach
    void setUp() {
        generatorSequence = new GeneratorSequence();
    }

    @Test
    void testNextSequenceBasic() {
        Integer seq1 = generatorSequence.nextSequence("2604081430");
        assertNotNull(seq1);
        assertEquals(1, seq1);
    }

    @Test
    void testNextSequenceIncrement() {
        String timestamp = "2604081430";
        Integer seq1 = generatorSequence.nextSequence(timestamp);
        Integer seq2 = generatorSequence.nextSequence(timestamp);
        Integer seq3 = generatorSequence.nextSequence(timestamp);

        assertNotNull(seq1);
        assertNotNull(seq2);
        assertNotNull(seq3);
        assertEquals(1, seq1);
        assertEquals(2, seq2);
        assertEquals(3, seq3);
    }

    @Test
    void testNextSequenceTimestampChange() {
        Integer seq1 = generatorSequence.nextSequence("2604081430");
        assertEquals(1, seq1);

        Integer seq2 = generatorSequence.nextSequence("2604081431");
        assertEquals(1, seq2);

        Integer seq3 = generatorSequence.nextSequence("2604081430");
        assertNotNull(seq3);
    }

    @Test
    void testNextSequenceExhausted() {
        String timestamp = "2604081430";
        for (int i = 1; i <= 9999; i++) {
            Integer seq = generatorSequence.nextSequence(timestamp);
            assertNotNull(seq);
            assertEquals(i, seq);
        }

        Integer exhausted = generatorSequence.nextSequence(timestamp);
        assertNull(exhausted);
    }

    @Test
    void testNextSequenceAfterExhaustNewTimestamp() {
        String timestamp1 = "2604081430";
        for (int i = 1; i <= 9999; i++) {
            generatorSequence.nextSequence(timestamp1);
        }
        assertNull(generatorSequence.nextSequence(timestamp1));

        String timestamp2 = "2604081431";
        Integer seq = generatorSequence.nextSequence(timestamp2);
        assertNotNull(seq);
        assertEquals(1, seq);
    }

    @Test
    void testConcurrentNextSequence() throws InterruptedException {
        int threadCount = 10;
        int iterationsPerThread = 100;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        Set<Integer> allSequences = Collections.newSetFromMap(new ConcurrentHashMap<>());

        AtomicInteger nullCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    for (int j = 0; j < iterationsPerThread; j++) {
                        Integer seq = generatorSequence.nextSequence("2604081430");
                        if (seq != null) {
                            allSequences.add(seq);
                        } else {
                            nullCount.incrementAndGet();
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executorService.shutdown();

        assertTrue(allSequences.size() >= 999 || nullCount.get() > 0);
    }

    @Test
    void testSequenceUniqueness() {
        String timestamp = "2604081430";
        Set<Integer> sequences = new HashSet<>();
        for (int i = 0; i < 9999; i++) {
            Integer seq = generatorSequence.nextSequence(timestamp);
            assertNotNull(seq);
            assertTrue(sequences.add(seq), "Duplicate sequence detected: " + seq);
        }
        assertEquals(9999, sequences.size());
    }
}
