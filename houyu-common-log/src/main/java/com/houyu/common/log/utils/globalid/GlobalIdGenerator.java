package com.houyu.common.log.utils.globalid;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 全局唯一ID生成器
 * <p>
 * ID格式（共19位）：
 * - 10位时间：yyMMddHHmm（2位年 + 2位月 + 2位日 + 2位时 + 2位分）
 * - 4位机器码：0000~9999，集群唯一
 * - 4位自增序列：0000~9999，进程内唯一
 * - 1位标志位：由调用方传入，取值1~9，非法值或未传则为0
 * <p>
 * 自增序列兜底机制：
 * - 同一分钟内序列超过10000时，将时间部分的分钟后两位依次修正为60、61...
 * - 例如：2604201430 → 2604201460 → 2604201461 → ...
 */
@Slf4j
@Component
public class GlobalIdGenerator {

    /**
     * ID总长度
     */
    public static final int ID_TOTAL_LENGTH = 19;

    /**
     * 时间部分长度（10位）
     */
    public static final int TIME_PART_LENGTH = 10;

    /**
     * 机器码部分长度（4位）
     */
    public static final int MACHINE_ID_LENGTH = 4;

    /**
     * 序列号部分长度（4位）
     */
    public static final int SEQUENCE_LENGTH = 4;

    /**
     * 标志位部分长度（1位）
     */
    public static final int FLAG_LENGTH = 1;

    /**
     * 标志位最小值
     */
    public static final int FLAG_MIN = 1;

    /**
     * 标志位最大值
     */
    public static final int FLAG_MAX = 9;

    /**
     * 标志位默认值
     */
    public static final int DEFAULT_FLAG = 0;

    /**
     * 时间部分格式：yyMMddHHmm
     */
    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyMMddHHmm");

    /**
     * 最大序列值（4位：0000~9999）
     */
    private static final int MAX_SEQUENCE = 9999;

    /**
     * 基础时间分钟值（用于序列溢出时的时间偏移）
     */
    private static final int BASE_MINUTE = 60;

    /**
     * 时间部分除数（用于分离分钟部分）
     */
    private static final long TIME_DIVISOR = 100L;

    /**
     * 机器码格式（4位，带前导零）
     */
    private static final String MACHINE_ID_FORMAT = "%04d";

    /**
     * ID格式字符串：时间(10位) + 机器码(4位) + 序列号(4位) + 标志位(1位)
     */
    private static final String ID_FORMAT = "%010d%s%04d%d";

    /**
     * 默认机器码字符串
     */
    private static final String DEFAULT_MACHINE_ID_STR = "0000";

    /**
     * 机器码注册器
     */
    private final MachineIdRegistrar machineIdRegistrar;

    /**
     * 当前序列值
     */
    private final AtomicInteger sequence = new AtomicInteger(0);

    /**
     * 当前时间戳（yyMMddHHmm格式的整数）
     */
    private final AtomicLong currentTimePart = new AtomicLong(0);

    /**
     * 当前时间偏移量（用于序列兜底）
     */
    private final AtomicInteger timeOffset = new AtomicInteger(0);

    /**
     * 用于保证时间更新和序列重置的原子性
     */
    private final ReentrantLock timeLock = new ReentrantLock();

    /**
     * 机器码字符串（4位，带前导零）
     */
    private volatile String machineIdStr = DEFAULT_MACHINE_ID_STR;

    public GlobalIdGenerator(MachineIdRegistrar machineIdRegistrar) {
        this.machineIdRegistrar = machineIdRegistrar;
    }

    /**
     * 初始化
     * 初始化机器码和时间部分
     */
    @PostConstruct
    public void init() {
        try {
            machineIdRegistrar.initialize();
            int machineId = machineIdRegistrar.getMachineId();
            this.machineIdStr = String.format(MACHINE_ID_FORMAT, machineId);
            log.info("全局ID生成器初始化完成，机器码: {}", machineIdStr);
        } catch (Exception e) {
            log.error("全局ID生成器初始化失败", e);
            throw new IllegalStateException("全局ID生成器初始化失败", e);
        }
    }

    /**
     * 生成全局唯一ID（使用默认标志位0）
     *
     * @return 19位全局唯一ID字符串
     */
    public String generate() {
        return generate(DEFAULT_FLAG);
    }

    /**
     * 生成全局唯一ID
     *
     * @param flag 标志位，取值1~9，非法值则使用默认值0
     * @return 19位全局唯一ID字符串
     */
    public String generate(int flag) {
        int validFlag = validateFlag(flag);
        long timePart = getNextTimePart();
        int seq = getNextSequence(timePart);

        return String.format(ID_FORMAT,
                timePart,
                machineIdStr,
                seq,
                validFlag);
    }

    /**
     * 生成下一个时间部分
     * 处理同一分钟内序列溢出的情况
     *
     * @return 时间部分（yyMMddHHmm格式，可能带偏移）
     */
    private long getNextTimePart() {
        long baseTime = getBaseTime();
        long currentTime = currentTimePart.get();

        if (baseTime > currentTime) {
            timeLock.lock();
            try {
                if (baseTime > currentTimePart.get()) {
                    currentTimePart.set(baseTime);
                    timeOffset.set(0);
                    sequence.set(0);
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
     * 获取下一个序列号
     * 当同一时间周期内序列溢出时，增加时间偏移量
     *
     * @param timePart 当前时间部分
     * @return 序列号（0~9999）
     */
    private int getNextSequence(long timePart) {
        int currentSeq = sequence.getAndIncrement();

        if (currentSeq > MAX_SEQUENCE) {
            timeLock.lock();
            try {
                if (sequence.get() > MAX_SEQUENCE) {
                    int offset = timeOffset.incrementAndGet();
                    long newTimePart = calculateTimePartWithOffset(timePart, offset);
                    currentTimePart.set(newTimePart);
                    sequence.set(0);
                    log.warn("序列溢出，时间偏移量增加为: {}, 新时间部分: {}",
                            offset, newTimePart);
                    return 0;
                }
                return sequence.getAndIncrement();
            } finally {
                timeLock.unlock();
            }
        }

        return currentSeq;
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
    private long calculateTimePartWithOffset(long baseTimePart, int offset) {
        long timeWithoutMinute = baseTimePart / TIME_DIVISOR;
        int newMinute = BASE_MINUTE + offset - 1;
        return timeWithoutMinute * TIME_DIVISOR + newMinute;
    }

    /**
     * 验证标志位
     * 合法范围：1~9
     * 非法值或未传则返回默认值0
     *
     * @param flag 输入标志位
     * @return 有效标志位
     */
    private int validateFlag(int flag) {
        if (flag >= FLAG_MIN && flag <= FLAG_MAX) {
            return flag;
        }
        if (flag != DEFAULT_FLAG) {
            log.debug("标志位 {} 非法，使用默认值 {}", flag, DEFAULT_FLAG);
        }
        return DEFAULT_FLAG;
    }

    /**
     * 获取当前机器码
     *
     * @return 机器码（0~9999）
     */
    public int getMachineId() {
        return machineIdRegistrar.getMachineId();
    }

    /**
     * 获取当前机器码字符串（4位格式）
     *
     * @return 机器码字符串
     */
    public String getMachineIdString() {
        return machineIdStr;
    }

    /**
     * 检查是否使用备用段机器码
     *
     * @return true表示使用备用段
     */
    public boolean isUsingBackupMachineId() {
        return machineIdRegistrar.isBackup();
    }
}
