package com.genersoft.iot.wvmp.conf;

import com.alibaba.fastjson.JSONObject;
import com.genersoft.iot.wvmp.service.IMediaServerService;
import com.genersoft.iot.wvmp.storager.IRedisCatchStorage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WvmpTimerTask {

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Autowired
    private IMediaServerService mediaServerService;

    @Value("${server.port}")
    private int serverPort;

    @Autowired
    private SipConfig sipConfig;

    @Scheduled(fixedRate = 2 * 1000)   //每3秒执行一次
    public void execute(){
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("ip", sipConfig.getIp());
        jsonObject.put("port", serverPort);
        redisCatchStorage.updateWVPInfo(jsonObject, 3);
    }
}
