package com.houyu.common.log.utils.globalid;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
public class GeneratorSequence {
    private static final int MAX_SEQUENCE = 9999;
    private final AtomicInteger sequence;
    private volatile String lastTimestamp;

    public GeneratorSequence() {
        this.sequence = new AtomicInteger(0);
        this.lastTimestamp = "";
    }

    public Integer nextSequence(String currentTimestamp) {
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
            return null;
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
