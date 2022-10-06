package com.genersoft.iot.wvmp.service;

import com.genersoft.iot.wvmp.gb28181.protocol.platform.bean.ChannelReduce;

import java.util.List;

/**
 * 平台关联通道管理
 * @author lin
 */
public interface IPlatformChannelService {

    /**
     * 更新目录下的通道
     * @param platformId 平台编号
     * @param channelReduces 通道信息
     * @param catalogId 目录编号
     * @return
     */
    int updateChannelForGB(String platformId, List<ChannelReduce> channelReduces, String catalogId);

}
