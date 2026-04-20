package com.houyu.common.log.utils.globalid;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 机器码注册信息类
 * 用于存储和序列化机器码的注册信息到Redis
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MachineIdInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 机器名称
     * 从系统环境获取，通常为hostname
     */
    private String machineName;

    /**
     * 机器IP地址
     * 从系统网络接口获取
     */
    private String machineIp;

    /**
     * 注册时间
     * 格式：yyyy-MM-dd HH:mm:ss SSS
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss SSS")
    private LocalDateTime registerTime;

    /**
     * 服务销毁时间
     * 服务正常销毁时更新，用于清理过期的机器码
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss SSS")
    private LocalDateTime destroyTime;
}
