package com.genersoft.iot.wvmp.service;

import com.alibaba.fastjson.JSONObject;
import com.genersoft.iot.wvmp.common.StreamInfo;
import com.genersoft.iot.wvmp.conf.exception.ServiceException;
import com.genersoft.iot.wvmp.gb28181.bean.Device;
import com.genersoft.iot.wvmp.gb28181.bean.InviteStreamCallback;
import com.genersoft.iot.wvmp.gb28181.bean.InviteStreamInfo;
import com.genersoft.iot.wvmp.gb28181.event.SipSubscribe;
import com.genersoft.iot.wvmp.zlm.ZlmHttpHookSubscribe;
import com.genersoft.iot.wvmp.zlm.dto.MediaServerItem;
import com.genersoft.iot.wvmp.service.bean.InviteTimeOutCallback;
import com.genersoft.iot.wvmp.service.bean.PlayBackCallback;
import com.genersoft.iot.wvmp.service.bean.SsrcInfo;
import com.genersoft.iot.wvmp.gb28181.protocol.play.bean.PlayResult;
import org.springframework.web.context.request.async.DeferredResult;

import javax.sip.InvalidArgumentException;
import javax.sip.SipException;
import java.text.ParseException;

/**
 * 点播处理
 */
public interface IPlayService {

    void onPublishHandlerForPlay(MediaServerItem mediaServerItem, JSONObject resonse, String deviceId, String channelId, String uuid);

    void play(MediaServerItem mediaServerItem, SsrcInfo ssrcInfo, Device device, String channelId,
              ZlmHttpHookSubscribe.Event hookEvent, SipSubscribe.Event errorEvent,
              InviteTimeOutCallback timeoutCallback, String uuid);
    PlayResult play(MediaServerItem mediaServerItem, String deviceId, String channelId, ZlmHttpHookSubscribe.Event event, SipSubscribe.Event errorEvent, Runnable timeoutCallback);

    MediaServerItem getNewMediaServerItem(Device device);

    void onPublishHandlerForDownload(InviteStreamInfo inviteStreamInfo, String deviceId, String channelId, String toString);

    DeferredResult<com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<StreamInfo>> playBack(String deviceId, String channelId, String startTime, String endTime, InviteStreamCallback infoCallBack, PlayBackCallback hookCallBack);
    DeferredResult<com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<StreamInfo>> playBack(MediaServerItem mediaServerItem, SsrcInfo ssrcInfo, String deviceId, String channelId, String startTime, String endTime, InviteStreamCallback infoCallBack, PlayBackCallback hookCallBack);

    void zlmServerOffline(String mediaServerId);

    DeferredResult<com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<StreamInfo>> download(String deviceId, String channelId, String startTime, String endTime, int downloadSpeed, InviteStreamCallback infoCallBack, PlayBackCallback hookCallBack);
    DeferredResult<com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<StreamInfo>> download(MediaServerItem mediaServerItem, SsrcInfo ssrcInfo, String deviceId, String channelId, String startTime, String endTime, int downloadSpeed, InviteStreamCallback infoCallBack, PlayBackCallback hookCallBack);

    StreamInfo getDownLoadInfo(String deviceId, String channelId, String stream);

    void zlmServerOnline(String mediaServerId);

    void pauseRtp(String streamId) throws ServiceException, InvalidArgumentException, ParseException, SipException;

    void resumeRtp(String streamId) throws ServiceException, InvalidArgumentException, ParseException, SipException;
}
