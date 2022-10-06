package com.genersoft.iot.wvmp.conf;

import com.genersoft.iot.wvmp.storager.IRedisCatchStorage;
import com.genersoft.iot.wvmp.utils.SystemInfoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * 获取系统信息写入redis
 */
@Component
public class SystemInfoTimerTask {

    private Logger logger = LoggerFactory.getLogger(SystemInfoTimerTask.class);

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Scheduled(fixedRate = 1000)   //每1秒执行一次
    public void execute(){
        try {
            double cpuInfo = SystemInfoUtils.getCpuInfo();
            redisCatchStorage.addCpuInfo(cpuInfo);
            double memInfo = SystemInfoUtils.getMemInfo();
            redisCatchStorage.addMemInfo(memInfo);
            Map<String, String> networkInterfaces = SystemInfoUtils.getNetworkInterfaces();
            redisCatchStorage.addNetInfo(networkInterfaces);
        } catch (InterruptedException e) {
            logger.error("[获取系统信息失败] {}", e.getMessage());
        }

    }
}
