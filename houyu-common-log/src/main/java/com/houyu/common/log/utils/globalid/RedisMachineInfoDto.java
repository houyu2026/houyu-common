package com.houyu.common.log.utils.globalid;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Redis机器码信息包装DTO
 * 用于封装存储在Redis中的机器码相关信息
 * 包含Redis键和对应的值对象
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RedisMachineInfoDto implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Redis中存储的键
     * 主段格式：machine_id_{服务名称}_{机器码}
     * 备用段格式：machine_id_bak_{服务名称}_{机器码}
     */
    private String redisKey;

    /**
     * Redis中存储的值对象
     * 包含机器码的详细信息
     */
    private MachineInfoDto redisValue;

    /**
     * 机器码详细信息DTO
     * 用于存储和序列化机器码的注册信息到Redis
     * 支持Builder模式构建对象
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MachineInfoDto implements Serializable {

        private static final long serialVersionUID = 1L;

        /**
         * 机器码
         * 取值范围：0000~9999，集群唯一
         */
        private String machineId;

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
         * 格式：yyyy-MM-dd HH:mm:ss SSS
         */
        @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss SSS")
        private LocalDateTime destroyTime;
    }
}
