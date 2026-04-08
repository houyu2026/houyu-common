package com.houyu.common.log.utils.globalid;

import java.util.concurrent.atomic.AtomicInteger;

public class SequenceGenerator {
    private static final int MAX_SEQUENCE = 9999;
    private final AtomicInteger sequence;
    private volatile String lastTimestamp;

    public SequenceGenerator() {
        this.sequence = new AtomicInteger(0);
        this.lastTimestamp = "";
    }

    public int nextSequence(String currentTimestamp) {
        if (!currentTimestamp.equals(lastTimestamp)) {
            synchronized (this) {
                if (!currentTimestamp.equals(lastTimestamp)) {
                    sequence.set(0);
                    lastTimestamp = currentTimestamp;
                }
            }
        }
        
        int currentSequence = sequence.incrementAndGet();
        if (currentSequence > MAX_SEQUENCE) {
            synchronized (this) {
                while (sequence.get() > MAX_SEQUENCE) {
                    try {
                        Thread.sleep(1);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException("Sequence generation interrupted", e);
                    }
                }
                currentSequence = sequence.incrementAndGet();
            }
        }
        return currentSequence;
    }

    public boolean isSequenceExhausted(String currentTimestamp) {
        if (!currentTimestamp.equals(lastTimestamp)) {
            return false;
        }
        return sequence.get() >= MAX_SEQUENCE;
    }
}
