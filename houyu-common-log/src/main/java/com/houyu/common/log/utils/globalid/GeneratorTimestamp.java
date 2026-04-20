package com.houyu.common.log.utils.globalid;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 时间戳生成器
 * 负责生成全局唯一ID中的时间部分
 * 时间格式：yyMMddHHmm（10位，2位年+2位月+2位日+2位时+2位分）
 * <p>
 * 特性：
 * 1. 支持时间更新时自动重置序列
 * 2. 支持序列兜底机制：同一分钟内序列超过10000时，分钟部分修正为60、61...
 * 3. 使用锁保证时间更新的原子性
 */
@Slf4j
@Component
public class GeneratorTimestamp {

    /**
     * 时间部分长度（10位）
     */
    public static final int TIME_PART_LENGTH = 10;

    /**
     * 时间部分格式：yyMMddHHmm
     * 示例：2604201430 表示 2026-04-20 14:30
     */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyMMddHHmm");

    /**
     * 基础时间分钟值（用于序列溢出时的时间偏移）
     * 正常分钟范围：00~59，溢出后使用：60、61...
     */
    public static final int BASE_MINUTE = 60;

    /**
     * 时间部分除数（用于分离分钟部分）
     * 时间格式：yyMMddHHmm，最后两位是分钟
     */
    public static final long TIME_DIVISOR = 100L;

    /**
     * 当前时间戳（yyMMddHHmm格式的整数）
     * 使用AtomicLong保证多线程可见性
     */
    private final AtomicLong currentTimePart = new AtomicLong(0);

    /**
     * 当前时间偏移量（用于序列兜底）
     * 当同一分钟内序列超过10000时，偏移量递增
     */
    @Getter
    private final AtomicInteger timeOffset = new AtomicInteger(0);

    /**
     * 用于保证时间更新和序列重置的原子性
     */
    private final ReentrantLock timeLock = new ReentrantLock();

    /**
     * 生成下一个时间部分
     * 处理时间变化和序列溢出的情况
     *
     * @param generatorSequence 序列生成器（用于时间变化时重置序列）
     * @return 时间部分（yyMMddHHmm格式，可能带偏移）
     */
    public long getNextTimePart(GeneratorSequence generatorSequence) {
        long baseTime = getBaseTime();
        long currentTime = currentTimePart.get();

        if (baseTime > currentTime) {
            timeLock.lock();
            try {
                if (baseTime > currentTimePart.get()) {
                    currentTimePart.set(baseTime);
                    timeOffset.set(0);
                    generatorSequence.reset();
                    log.debug("时间部分更新为: {}, 偏移量重置为0", baseTime);
                }
            } finally {
                timeLock.unlock();
            }
        }

        return currentTimePart.get();
    }

    /**
     * 获取当前基础时间（不带偏移）
     *
     * @return yyMMddHHmm格式的时间值
     */
    private long getBaseTime() {
        LocalDateTime now = LocalDateTime.now();
        String timeStr = now.format(TIME_FORMATTER);
        return Long.parseLong(timeStr);
    }

    /**
     * 根据偏移量计算时间部分
     * 原始时间格式：yyMMddHHmm（最后两位是分钟）
     * 溢出处理：将分钟部分依次改为60、61、62...
     * <p>
     * 例如：
     * - 原始时间：2604201430 → 表示2026-04-20 14:30
     * - 偏移1：2604201460 → 分钟部分改为60
     * - 偏移2：2604201461 → 分钟部分改为61
     *
     * @param baseTimePart 基础时间部分
     * @param offset       偏移量
     * @return 带偏移的时间部分
     */
    public long calculateTimePartWithOffset(long baseTimePart, int offset) {
        long timeWithoutMinute = baseTimePart / TIME_DIVISOR;
        int newMinute = BASE_MINUTE + offset - 1;
        return timeWithoutMinute * TIME_DIVISOR + newMinute;
    }

    /**
     * 增加时间偏移量并更新时间部分
     * 当同一时间周期内序列溢出时调用
     *
     * @param baseTimePart 基础时间部分
     * @return 新的时间偏移量
     */
    public int incrementTimeOffset(long baseTimePart) {
        int offset = timeOffset.incrementAndGet();
        long newTimePart = calculateTimePartWithOffset(baseTimePart, offset);
        currentTimePart.set(newTimePart);
        log.warn("序列溢出，时间偏移量增加为: {}, 新时间部分: {}", offset, newTimePart);
        return offset;
    }

    /**
     * 获取时间锁
     * 用于在序列溢出时保证时间更新的原子性
     *
     * @return 可重入锁
     */
    public ReentrantLock getTimeLock() {
        return timeLock;
    }

    /**
     * 获取当前时间部分（仅用于测试和监控）
     *
     * @return 当前时间部分
     */
    public long getCurrentTimePart() {
        return currentTimePart.get();
    }

    /**
     * 设置当前时间部分（仅用于测试）
     *
     * @param timePart 时间部分
     */
    public void setCurrentTimePart(long timePart) {
        currentTimePart.set(timePart);
    }

    /**
     * 重置时间偏移量（仅用于测试）
     */
    public void resetTimeOffset() {
        timeOffset.set(0);
    }
}
