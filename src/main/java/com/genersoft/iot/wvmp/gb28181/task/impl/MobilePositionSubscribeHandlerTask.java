package com.genersoft.iot.wvmp.gb28181.task.impl;

import com.genersoft.iot.wvmp.gb28181.task.ISubscribeTask;
import com.genersoft.iot.wvmp.service.IPlatformService;
import com.genersoft.iot.wvmp.utils.SpringBeanFactory;

/**
 * 向已经订阅(移动位置)的上级发送MobilePosition消息
 * @author lin
 */
public class MobilePositionSubscribeHandlerTask implements ISubscribeTask {


    private IPlatformService platformService;
    private String platformId;


    public MobilePositionSubscribeHandlerTask(String platformId) {
        this.platformService = SpringBeanFactory.getBean("platformServiceImpl");
        this.platformId = platformId;
    }

    @Override
    public void run() {
        platformService.sendNotifyMobilePosition(this.platformId);
    }

    @Override
    public void stop() {

    }
}
