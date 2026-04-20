package com.houyu.common.log.utils.globalid;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 自增序列生成器
 * 负责生成全局唯一ID中的自增序列部分
 * 序列范围：0000~9999（4位）
 * <p>
 * 特性：
 * 1. 使用AtomicInteger保证线程安全
 * 2. 支持序列兜底机制：同一时间周期内序列超过10000时，触发时间偏移
 * 3. 使用锁保证时间更新和序列重置的原子性
 */
@Slf4j
@Component
public class GeneratorSequence {

    /**
     * 序列号部分长度（4位）
     */
    public static final int SEQUENCE_LENGTH = 4;

    /**
     * 最大序列值（4位：0000~9999）
     * 当序列超过此值时，触发时间偏移兜底机制
     */
    public static final int MAX_SEQUENCE = 9999;

    /**
     * 当前序列值
     * 使用AtomicInteger保证多线程环境下的原子性
     */
    private final AtomicInteger sequence = new AtomicInteger(0);

    /**
     * 用于保证时间更新和序列重置的原子性
     * 在序列溢出需要更新时间时使用
     */
    private final ReentrantLock timeLock = new ReentrantLock();

    /**
     * 获取下一个序列号
     * 使用CAS操作保证线程安全
     *
     * @return 序列号（0~9999）
     */
    public int getNextSequence() {
        return sequence.getAndIncrement();
    }

    /**
     * 检查序列是否溢出
     * 当序列超过MAX_SEQUENCE时视为溢出
     *
     * @param currentSeq 当前序列号
     * @return true表示溢出
     */
    public boolean isOverflow(int currentSeq) {
        return currentSeq > MAX_SEQUENCE;
    }

    /**
     * 重置序列
     * 当时间周期变化时需要重置序列
     */
    public void reset() {
        sequence.set(0);
    }

    /**
     * 获取当前序列值（仅用于测试和监控）
     *
     * @return 当前序列值
     */
    public int getCurrentSequence() {
        return sequence.get();
    }

    /**
     * 获取时间锁
     * 用于在序列溢出时保证时间更新和序列重置的原子性
     *
     * @return 可重入锁
     */
    public ReentrantLock getTimeLock() {
        return timeLock;
    }
}
