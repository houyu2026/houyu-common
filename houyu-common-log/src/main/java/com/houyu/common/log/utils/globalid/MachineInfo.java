package com.houyu.common.log.utils.globalid;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MachineInfo {
    private String machineName;
    private String machineIp;
    private LocalDateTime registerTime;
    private LocalDateTime destroyTime;
}
