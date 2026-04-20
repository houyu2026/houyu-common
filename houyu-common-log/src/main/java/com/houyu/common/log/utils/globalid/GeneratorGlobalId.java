package com.houyu.common.log.utils.globalid;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
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
 * <p>
 * 依赖组件：
 * - GeneratorMachineId：机器码生成器
 * - GeneratorSequence：自增序列生成器
 * - GeneratorTimestamp：时间戳生成器
 */
@Slf4j
@Component
public class GeneratorGlobalId {

    /**
     * ID总长度（19位）
     */
    public static final int ID_TOTAL_LENGTH = 19;

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
     * ID格式字符串：时间(10位) + 机器码(4位) + 序列号(4位) + 标志位(1位)
     */
    private static final String ID_FORMAT = "%010d%s%04d%d";

    /**
     * 默认机器码字符串
     */
    private static final String DEFAULT_MACHINE_ID_STR = "0000";

    /**
     * 机器码生成器
     * 负责生成和管理集群唯一的机器码
     */
    private final GeneratorMachineId generatorMachineId;

    /**
     * 自增序列生成器
     * 负责生成进程内唯一的自增序列
     */
    private final GeneratorSequence generatorSequence;

    /**
     * 时间戳生成器
     * 负责生成时间部分并处理序列兜底
     */
    private final GeneratorTimestamp generatorTimestamp;

    /**
     * 机器码字符串（4位，带前导零）
     * 使用volatile保证多线程可见性
     */
    private volatile String machineIdStr = DEFAULT_MACHINE_ID_STR;

    /**
     * 构造函数
     *
     * @param generatorMachineId  机器码生成器
     * @param generatorSequence   自增序列生成器
     * @param generatorTimestamp  时间戳生成器
     */
    public GeneratorGlobalId(GeneratorMachineId generatorMachineId,
                              GeneratorSequence generatorSequence,
                              GeneratorTimestamp generatorTimestamp) {
        this.generatorMachineId = generatorMachineId;
        this.generatorSequence = generatorSequence;
        this.generatorTimestamp = generatorTimestamp;
    }

    /**
     * 初始化
     * 服务启动后自动调用，初始化机器码
     */
    @PostConstruct
    public void init() {
        try {
            generatorMachineId.initialize();
            int machineId = generatorMachineId.getMachineId();
            this.machineIdStr = generatorMachineId.getMachineIdString();
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
        long timePart = generatorTimestamp.getNextTimePart(generatorSequence);
        int seq = getNextSequence(timePart);

        return String.format(ID_FORMAT,
                timePart,
                machineIdStr,
                seq,
                validFlag);
    }

    /**
     * 生成多个全局唯一ID（使用默认标志位0）
     *
     * @param count 需要生成的ID数量
     * @return ID列表
     * @throws IllegalArgumentException 如果count小于等于0
     */
    public List<String> generateBatch(int count) {
        return generateBatch(count, DEFAULT_FLAG);
    }

    /**
     * 生成多个全局唯一ID
     *
     * @param count 需要生成的ID数量
     * @param flag  标志位，取值1~9，非法值则使用默认值0
     * @return ID列表
     * @throws IllegalArgumentException 如果count小于等于0
     */
    public List<String> generateBatch(int count, int flag) {
        if (count <= 0) {
            throw new IllegalArgumentException("生成数量必须大于0");
        }

        List<String> ids = new ArrayList<>(count);
        int validFlag = validateFlag(flag);

        for (int i = 0; i < count; i++) {
            long timePart = generatorTimestamp.getNextTimePart(generatorSequence);
            int seq = getNextSequence(timePart);
            String id = String.format(ID_FORMAT, timePart, machineIdStr, seq, validFlag);
            ids.add(id);
        }

        return ids;
    }

    /**
     * 获取下一个序列号
     * 当同一时间周期内序列溢出时，增加时间偏移量
     *
     * @param timePart 当前时间部分
     * @return 序列号（0~9999）
     */
    private int getNextSequence(long timePart) {
        int currentSeq = generatorSequence.getNextSequence();

        if (generatorSequence.isOverflow(currentSeq)) {
            ReentrantLock timeLock = generatorTimestamp.getTimeLock();
            timeLock.lock();
            try {
                if (generatorSequence.isOverflow(generatorSequence.getCurrentSequence())) {
                    generatorTimestamp.incrementTimeOffset(timePart);
                    generatorSequence.reset();
                    return 0;
                }
                return generatorSequence.getNextSequence();
            } finally {
                timeLock.unlock();
            }
        }

        return currentSeq;
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
        return generatorMachineId.getMachineId();
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
        return generatorMachineId.isBackup();
    }
}
