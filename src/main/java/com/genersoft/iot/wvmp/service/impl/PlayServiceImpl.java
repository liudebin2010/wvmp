package com.genersoft.iot.wvmp.service.impl;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.util.*;

import javax.sip.InvalidArgumentException;
import javax.sip.ResponseEvent;
import javax.sip.SipException;

import com.genersoft.iot.wvmp.conf.exception.ControllerException;
import com.genersoft.iot.wvmp.conf.exception.ServiceException;
import com.genersoft.iot.wvmp.conf.exception.SsrcTransactionNotFoundException;
import com.genersoft.iot.wvmp.gb28181.bean.*;
import com.genersoft.iot.wvmp.service.IDeviceService;
import com.genersoft.iot.wvmp.utils.redis.RedisUtil;
import com.genersoft.iot.wvmp.vmanager.bean.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.genersoft.iot.wvmp.common.StreamInfo;
import com.genersoft.iot.wvmp.conf.DynamicTask;
import com.genersoft.iot.wvmp.conf.UserSetting;
import com.genersoft.iot.wvmp.gb28181.event.SipSubscribe;
import com.genersoft.iot.wvmp.gb28181.session.VideoStreamSessionManager;
import com.genersoft.iot.wvmp.gb28181.transmit.callback.DeferredResultHolder;
import com.genersoft.iot.wvmp.gb28181.transmit.callback.RequestMessage;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.impl.SipCommander;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.impl.SipCommanderFroPlatform;
import com.genersoft.iot.wvmp.zlm.dto.HookSubscribeFactory;
import com.genersoft.iot.wvmp.zlm.dto.HookSubscribeForStreamChange;
import com.genersoft.iot.wvmp.utils.DateUtil;
import com.genersoft.iot.wvmp.zlm.AssistRestfulUtils;
import com.genersoft.iot.wvmp.zlm.ZlmHttpHookSubscribe;
import com.genersoft.iot.wvmp.zlm.ZlmRestfulUtils;
import com.genersoft.iot.wvmp.zlm.dto.MediaServerItem;
import com.genersoft.iot.wvmp.service.IMediaServerService;
import com.genersoft.iot.wvmp.service.IMediaService;
import com.genersoft.iot.wvmp.service.IPlayService;
import com.genersoft.iot.wvmp.service.bean.InviteTimeOutCallback;
import com.genersoft.iot.wvmp.service.bean.PlayBackCallback;
import com.genersoft.iot.wvmp.service.bean.PlayBackResult;
import com.genersoft.iot.wvmp.service.bean.SsrcInfo;
import com.genersoft.iot.wvmp.storager.IRedisCatchStorage;
import com.genersoft.iot.wvmp.storager.IVideoManagerStorage;
import com.genersoft.iot.wvmp.gb28181.protocol.play.bean.PlayResult;

@SuppressWarnings(value = {"rawtypes", "unchecked"})
@Service
public class PlayServiceImpl implements IPlayService {

    private final static Logger logger = LoggerFactory.getLogger(PlayServiceImpl.class);

    @Autowired
    private IVideoManagerStorage storager;

    @Autowired
    private SipCommander cmder;

    @Autowired
    private SipCommanderFroPlatform sipCommanderFroPlatform;

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Autowired
    private DeferredResultHolder resultHolder;

    @Autowired
    private ZlmRestfulUtils zlmresTfulUtils;

    @Autowired
    private AssistRestfulUtils assistRESTfulUtils;

    @Autowired
    private IMediaService mediaService;

    @Autowired
    private IMediaServerService mediaServerService;

    @Autowired
    private VideoStreamSessionManager streamSession;


    @Autowired
    private IDeviceService deviceService;

    @Autowired
    private UserSetting userSetting;

    @Autowired
    private DynamicTask dynamicTask;

    @Autowired
    private ZlmHttpHookSubscribe subscribe;


    @Qualifier("taskExecutor")
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;



    @Override
    public PlayResult play(MediaServerItem mediaServerItem, String deviceId, String channelId,
                           ZlmHttpHookSubscribe.Event hookEvent, SipSubscribe.Event errorEvent,
                           Runnable timeoutCallback) {
        if (mediaServerItem == null) {
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "??????????????????zlm");
        }
        PlayResult playResult = new PlayResult();
        RequestMessage msg = new RequestMessage();
        String key = DeferredResultHolder.CALLBACK_CMD_PLAY + deviceId + channelId;
        msg.setKey(key);
        String uuid = UUID.randomUUID().toString();
        msg.setId(uuid);
        playResult.setUuid(uuid);
        DeferredResult<com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<String>> result = new DeferredResult<>(userSetting.getPlayTimeout().longValue());
        playResult.setResult(result);
        // ???????????????channelId??????deviceId??????
        resultHolder.put(key, uuid, result);

        Device device = redisCatchStorage.getDevice(deviceId);
        StreamInfo streamInfo = redisCatchStorage.queryPlayByDevice(deviceId, channelId);
        playResult.setDevice(device);

        result.onCompletion(()->{
            // ?????????????????????????????????
            taskExecutor.execute(()->{
                // TODO ???????????????????????????????????????????????????????????????
                String path =  "snap";
                String fileName =  deviceId + "_" + channelId + ".jpg";
                com.genersoft.iot.wvmp.vmanager.bean.WvmpResult wvpResult =  (com.genersoft.iot.wvmp.vmanager.bean.WvmpResult)result.getResult();
                if (Objects.requireNonNull(wvpResult).getCode() == 0) {
                    StreamInfo streamInfoForSuccess = (StreamInfo)wvpResult.getData();
                    MediaServerItem mediaInfo = mediaServerService.getOne(streamInfoForSuccess.getMediaServerId());
                    String streamUrl = streamInfoForSuccess.getFmp4();
                    // ????????????
                    logger.info("[????????????]: " + fileName);
                    zlmresTfulUtils.getSnap(mediaInfo, streamUrl, 15, 1, path, fileName);
                }
            });
        });

        if (streamInfo != null) {
            String streamId = streamInfo.getStream();
            if (streamId == null) {
                com.genersoft.iot.wvmp.vmanager.bean.WvmpResult wvpResult = new com.genersoft.iot.wvmp.vmanager.bean.WvmpResult();
                wvpResult.setCode(ErrorCode.ERROR100.getCode());
                wvpResult.setMsg("??????????????? redis??????streamId??????null");
                msg.setData(wvpResult);
                resultHolder.invokeAllResult(msg);
                return playResult;
            }
            String mediaServerId = streamInfo.getMediaServerId();
            MediaServerItem mediaInfo = mediaServerService.getOne(mediaServerId);

            JSONObject rtpInfo = zlmresTfulUtils.getRtpInfo(mediaInfo, streamId);
            if(rtpInfo.getInteger("code") == 0){
                if (rtpInfo.getBoolean("exist")) {
                    int localPort = rtpInfo.getInteger("local_port");
                    if (localPort == 0) {
                        logger.warn("[??????]??????????????????rtpServerC?????????????????????????????????");
                        // ????????????rtpServer???????????????????????????????????????
                        com.genersoft.iot.wvmp.vmanager.bean.WvmpResult wvpResult = new com.genersoft.iot.wvmp.vmanager.bean.WvmpResult();
                        wvpResult.setCode(ErrorCode.ERROR100.getCode());
                        wvpResult.setMsg("??????????????????????????????????????????");
                        msg.setData(wvpResult);

                        resultHolder.invokeAllResult(msg);
                        return playResult;
                    }else {
                        com.genersoft.iot.wvmp.vmanager.bean.WvmpResult wvpResult = new com.genersoft.iot.wvmp.vmanager.bean.WvmpResult();
                        wvpResult.setCode(ErrorCode.SUCCESS.getCode());
                        wvpResult.setMsg(ErrorCode.SUCCESS.getMsg());
                        wvpResult.setData(streamInfo);
                        msg.setData(wvpResult);

                        resultHolder.invokeAllResult(msg);
                        if (hookEvent != null) {
                            hookEvent.response(mediaServerItem, JSONObject.parseObject(JSON.toJSONString(streamInfo)));
                        }
                    }

                }else {
                    redisCatchStorage.stopPlay(streamInfo);
                    storager.stopPlay(streamInfo.getDeviceID(), streamInfo.getChannelId());
                    streamInfo = null;
                }
            }else {
                //zlm????????????
                redisCatchStorage.stopPlay(streamInfo);
                storager.stopPlay(streamInfo.getDeviceID(), streamInfo.getChannelId());
                streamInfo = null;

            }
        }
        if (streamInfo == null) {
            String streamId = null;
            if (mediaServerItem.isRtpEnable()) {
                streamId = String.format("%s_%s", device.getDeviceId(), channelId);
            }
            SsrcInfo ssrcInfo = mediaServerService.openRTPServer(mediaServerItem, streamId, device.isSsrcCheck(), false);
            logger.info(JSONObject.toJSONString(ssrcInfo));
            play(mediaServerItem, ssrcInfo, device, channelId, (mediaServerItemInUse, response)->{
                if (hookEvent != null) {
                    hookEvent.response(mediaServerItem, response);
                }
            }, event -> {
                // sip error??????
                com.genersoft.iot.wvmp.vmanager.bean.WvmpResult wvpResult = new com.genersoft.iot.wvmp.vmanager.bean.WvmpResult();
                wvpResult.setCode(ErrorCode.ERROR100.getCode());
                wvpResult.setMsg(String.format("??????????????? ???????????? %s, %s", event.statusCode, event.msg));
                msg.setData(wvpResult);
                resultHolder.invokeAllResult(msg);
                if (errorEvent != null) {
                    errorEvent.response(event);
                }
            }, (code, msgStr)->{
                // invite????????????
                com.genersoft.iot.wvmp.vmanager.bean.WvmpResult wvpResult = new com.genersoft.iot.wvmp.vmanager.bean.WvmpResult();
                wvpResult.setCode(ErrorCode.ERROR100.getCode());
                if (code == 0) {
                    wvpResult.setMsg("??????????????????????????????");
                }else if (code == 1) {
                    wvpResult.setMsg("??????????????????????????????");
                }
                msg.setData(wvpResult);
                // ?????????????????????????????????
                resultHolder.invokeAllResult(msg);
            }, uuid);
        }
        return playResult;
    }



    @Override
    public void play(MediaServerItem mediaServerItem, SsrcInfo ssrcInfo, Device device, String channelId,
                     ZlmHttpHookSubscribe.Event hookEvent, SipSubscribe.Event errorEvent,
                     InviteTimeOutCallback timeoutCallback, String uuid) {

        String streamId = null;
        if (mediaServerItem.isRtpEnable()) {
            streamId = String.format("%s_%s", device.getDeviceId(), channelId);
        }
        if (ssrcInfo == null) {
            ssrcInfo = mediaServerService.openRTPServer(mediaServerItem, streamId, device.isSsrcCheck(), false);
        }
        logger.info("[????????????] deviceId: {}, channelId: {},??????????????? {}, ???????????????{}, SSRC: {}, SSRC?????????{}", device.getDeviceId(), channelId, ssrcInfo.getPort(), device.getStreamMode(), ssrcInfo.getSsrc(), device.isSsrcCheck() );
        // ????????????
        String timeOutTaskKey = UUID.randomUUID().toString();
        SsrcInfo finalSsrcInfo = ssrcInfo;
        System.out.println("????????????????????? " + timeOutTaskKey);
        dynamicTask.startDelay( timeOutTaskKey,()->{

            logger.info("[????????????] ???????????? deviceId: {}, channelId: {}????????????{}, SSRC: {}", device.getDeviceId(), channelId, finalSsrcInfo.getPort(), finalSsrcInfo.getSsrc());
            timeoutCallback.run(1, "????????????");
            // ??????????????????BYE ????????????ssrc???????????????????????????
            try {
                cmder.streamByeCmd(device, channelId, finalSsrcInfo.getStream(), null);
            } catch (InvalidArgumentException | ParseException | SipException e) {
                logger.error("[????????????]??? ??????BYE?????? {}", e.getMessage());
            } catch (SsrcTransactionNotFoundException e) {
                timeoutCallback.run(0, "????????????");
                mediaServerService.releaseSsrc(mediaServerItem.getId(), finalSsrcInfo.getSsrc());
                mediaServerService.closeRTPServer(mediaServerItem, finalSsrcInfo.getStream());
                streamSession.remove(device.getDeviceId(), channelId, finalSsrcInfo.getStream());
            }
        }, userSetting.getPlayTimeout());
        final String ssrc = ssrcInfo.getSsrc();
        final String stream = ssrcInfo.getStream();
        //?????????????????????ssrcInfo ??????????????????????????????
        if(ssrcInfo.getPort() <= 0){
            logger.info("[????????????????????????]???deviceId={},channelId={},ssrcInfo={}", device.getDeviceId(), channelId, ssrcInfo);
            return;
        }
        try {
            cmder.playStreamCmd(mediaServerItem, ssrcInfo, device, channelId, (MediaServerItem mediaServerItemInuse, JSONObject response) -> {
                logger.info("????????????????????? " + response.toJSONString());
                System.out.println("????????????????????? " + timeOutTaskKey);
                dynamicTask.stop(timeOutTaskKey);
                // hook??????
                onPublishHandlerForPlay(mediaServerItemInuse, response, device.getDeviceId(), channelId, uuid);
                hookEvent.response(mediaServerItemInuse, response);
                logger.info("[????????????] deviceId: {}, channelId: {}", device.getDeviceId(), channelId);

            }, (event) -> {
                ResponseEvent responseEvent = (ResponseEvent)event.event;
                String contentString = new String(responseEvent.getResponse().getRawContent());
                // ??????ssrc
                int ssrcIndex = contentString.indexOf("y=");
                // ???????????????y??????
                if (ssrcIndex >= 0) {
                    //ssrc???????????????10???????????????????????????????????????????????????f=????????? TODO ????????????????????????10???ssrc??????
                    String ssrcInResponse = contentString.substring(ssrcIndex + 2, ssrcIndex + 12);
                    // ?????????ssrc?????????????????????ssrc???????????????????????????
                    if (ssrc.equals(ssrcInResponse)) {
                        return;
                    }
                    logger.info("[????????????] ??????invite 200, ????????????????????????ssrc: {}", ssrcInResponse );
                    if (!mediaServerItem.isRtpEnable() || device.isSsrcCheck()) {
                        logger.info("[????????????] SSRC?????? {}->{}", ssrc, ssrcInResponse);

                        if (!mediaServerItem.getSsrcConfig().checkSsrc(ssrcInResponse)) {
                            // ssrc ?????????
                            // ??????ssrc
                            mediaServerService.releaseSsrc(mediaServerItem.getId(), finalSsrcInfo.getSsrc());
                            streamSession.remove(device.getDeviceId(), channelId, finalSsrcInfo.getStream());
                            event.msg = "??????????????????ssrc,?????????ssrc?????????";
                            event.statusCode = 400;
                            errorEvent.response(event);
                            return;
                        }

                        // ???????????????streamId???????????????????????????????????????
                        if (!mediaServerItem.isRtpEnable()) {
                            // ????????????
                            HookSubscribeForStreamChange hookSubscribe = HookSubscribeFactory.on_stream_changed("rtp", stream, true, "rtsp", mediaServerItem.getId());
                            subscribe.removeSubscribe(hookSubscribe);
                            hookSubscribe.getContent().put("stream", String.format("%08x", Integer.parseInt(ssrcInResponse)).toUpperCase());
                            subscribe.addSubscribe(hookSubscribe, (MediaServerItem mediaServerItemInUse, JSONObject response)->{
                                        logger.info("[ZLM HOOK] ssrc?????????????????????????????? " + response.toJSONString());
                                        dynamicTask.stop(timeOutTaskKey);
                                        // hook??????
                                        onPublishHandlerForPlay(mediaServerItemInUse, response, device.getDeviceId(), channelId, uuid);
                                        hookEvent.response(mediaServerItemInUse, response);
                                    });
                        }
                        // ??????rtp server
                        mediaServerService.closeRTPServer(mediaServerItem, finalSsrcInfo.getStream());
                        // ????????????ssrc server
                        mediaServerService.openRTPServer(mediaServerItem, finalSsrcInfo.getStream(), ssrcInResponse, device.isSsrcCheck(), false, finalSsrcInfo.getPort());

                    }
                }
            }, (event) -> {
                dynamicTask.stop(timeOutTaskKey);
                mediaServerService.closeRTPServer(mediaServerItem, finalSsrcInfo.getStream());
                // ??????ssrc
                mediaServerService.releaseSsrc(mediaServerItem.getId(), finalSsrcInfo.getSsrc());

                streamSession.remove(device.getDeviceId(), channelId, finalSsrcInfo.getStream());
                errorEvent.response(event);
            });
        } catch (InvalidArgumentException | SipException | ParseException e) {

            logger.error("[??????????????????] ????????????: {}", e.getMessage());
            dynamicTask.stop(timeOutTaskKey);
            mediaServerService.closeRTPServer(mediaServerItem, finalSsrcInfo.getStream());
            // ??????ssrc
            mediaServerService.releaseSsrc(mediaServerItem.getId(), finalSsrcInfo.getSsrc());

            streamSession.remove(device.getDeviceId(), channelId, finalSsrcInfo.getStream());
            SipSubscribe.EventResult eventResult = new SipSubscribe.EventResult(new CmdSendFailEvent(null));
            eventResult.msg = "??????????????????";
            errorEvent.response(eventResult);
        }
    }

    @Override
    public void onPublishHandlerForPlay(MediaServerItem mediaServerItem, JSONObject response, String deviceId, String channelId, String uuid) {
        RequestMessage msg = new RequestMessage();
        if (uuid != null) {
            msg.setId(uuid);
        }
        msg.setKey(DeferredResultHolder.CALLBACK_CMD_PLAY + deviceId + channelId);
        StreamInfo streamInfo = onPublishHandler(mediaServerItem, response, deviceId, channelId);
        if (streamInfo != null) {
            DeviceChannel deviceChannel = storager.queryChannel(deviceId, channelId);
            if (deviceChannel != null) {
                deviceChannel.setStreamId(streamInfo.getStream());
                storager.startPlay(deviceId, channelId, streamInfo.getStream());
            }
            redisCatchStorage.startPlay(streamInfo);

            com.genersoft.iot.wvmp.vmanager.bean.WvmpResult wvpResult = new com.genersoft.iot.wvmp.vmanager.bean.WvmpResult();
            wvpResult.setCode(ErrorCode.SUCCESS.getCode());
            wvpResult.setMsg(ErrorCode.SUCCESS.getMsg());
            wvpResult.setData(streamInfo);
            msg.setData(wvpResult);

            resultHolder.invokeAllResult(msg);
        } else {
            logger.warn("????????????API???????????????");
            msg.setData(com.genersoft.iot.wvmp.vmanager.bean.WvmpResult.fail(ErrorCode.ERROR100.getCode(), "????????????API???????????????"));
            resultHolder.invokeAllResult(msg);
        }
    }

    @Override
    public MediaServerItem getNewMediaServerItem(Device device) {
        if (device == null) {
            return null;
        }
        String mediaServerId = device.getMediaServerId();
        MediaServerItem mediaServerItem;
        if (mediaServerId == null) {
            mediaServerItem = mediaServerService.getMediaServerForMinimumLoad();
        }else {
            mediaServerItem = mediaServerService.getOne(mediaServerId);
        }
        if (mediaServerItem == null) {
            logger.warn("??????????????????????????????ZLM...");
        }
        return mediaServerItem;
    }

    @Override
    public DeferredResult<com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<StreamInfo>> playBack(String deviceId, String channelId, String startTime,
                                                                                               String endTime, InviteStreamCallback inviteStreamCallback,
                                                                                               PlayBackCallback callback) {
        Device device = storager.queryVideoDevice(deviceId);
        if (device == null) {
            return null;
        }
        MediaServerItem newMediaServerItem = getNewMediaServerItem(device);
        SsrcInfo ssrcInfo = mediaServerService.openRTPServer(newMediaServerItem, null, true, true);

        return playBack(newMediaServerItem, ssrcInfo, deviceId, channelId, startTime, endTime, inviteStreamCallback, callback);
    }

    @Override
    public DeferredResult<com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<StreamInfo>> playBack(MediaServerItem mediaServerItem, SsrcInfo ssrcInfo,
                                                                                               String deviceId, String channelId, String startTime,
                                                                                               String endTime, InviteStreamCallback infoCallBack,
                                                                                               PlayBackCallback playBackCallback) {
        if (mediaServerItem == null || ssrcInfo == null) {
            return null;
        }
        String uuid = UUID.randomUUID().toString();
        String key = DeferredResultHolder.CALLBACK_CMD_PLAYBACK + deviceId + channelId;
        Device device = storager.queryVideoDevice(deviceId);
        if (device == null) {
            throw new ControllerException(ErrorCode.ERROR100.getCode(), "????????? " + deviceId + "?????????");
        }
        DeferredResult<com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<StreamInfo>> result = new DeferredResult<>(30000L);
        resultHolder.put(DeferredResultHolder.CALLBACK_CMD_PLAYBACK + deviceId + channelId, uuid, result);
        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setId(uuid);
        requestMessage.setKey(key);
        PlayBackResult<RequestMessage> playBackResult = new PlayBackResult<>();
        String playBackTimeOutTaskKey = UUID.randomUUID().toString();
        dynamicTask.startDelay(playBackTimeOutTaskKey, ()->{
            logger.warn(String.format("?????????????????????deviceId???%s ???channelId???%s", deviceId, channelId));
            playBackResult.setCode(ErrorCode.ERROR100.getCode());
            playBackResult.setMsg("????????????");
            playBackResult.setData(requestMessage);

            try {
                cmder.streamByeCmd(device, channelId, ssrcInfo.getStream(), null);
            } catch (InvalidArgumentException | ParseException | SipException e) {
                logger.error("[?????????]???????????? ??????BYE?????? {}", e.getMessage());
            } catch (SsrcTransactionNotFoundException e) {
                // ??????????????????BYE ????????????ssrc???????????????????????????
                mediaServerService.releaseSsrc(mediaServerItem.getId(), ssrcInfo.getSsrc());
                mediaServerService.closeRTPServer(mediaServerItem, ssrcInfo.getStream());
                streamSession.remove(deviceId, channelId, ssrcInfo.getStream());
            }

            // ?????????????????????????????????
            playBackCallback.call(playBackResult);
            result.setResult(com.genersoft.iot.wvmp.vmanager.bean.WvmpResult.fail(ErrorCode.ERROR100.getCode(), "????????????"));
            resultHolder.exist(DeferredResultHolder.CALLBACK_CMD_PLAYBACK + deviceId + channelId, uuid);
        }, userSetting.getPlayTimeout());

        SipSubscribe.Event errorEvent = event -> {
            dynamicTask.stop(playBackTimeOutTaskKey);
            requestMessage.setData(com.genersoft.iot.wvmp.vmanager.bean.WvmpResult.fail(ErrorCode.ERROR100.getCode(), String.format("??????????????? ???????????? %s, %s", event.statusCode, event.msg)));
            playBackResult.setCode(ErrorCode.ERROR100.getCode());
            playBackResult.setMsg(String.format("??????????????? ???????????? %s, %s", event.statusCode, event.msg));
            playBackResult.setData(requestMessage);
            playBackResult.setEvent(event);
            playBackCallback.call(playBackResult);
            streamSession.remove(device.getDeviceId(), channelId, ssrcInfo.getStream());
        };

        InviteStreamCallback hookEvent = (InviteStreamInfo inviteStreamInfo) -> {
            logger.info("??????????????????????????? " + inviteStreamInfo.getResponse().toJSONString());
            dynamicTask.stop(playBackTimeOutTaskKey);
            StreamInfo streamInfo = onPublishHandler(inviteStreamInfo.getMediaServerItem(), inviteStreamInfo.getResponse(), deviceId, channelId);
            if (streamInfo == null) {
                logger.warn("????????????API???????????????");
                playBackResult.setCode(ErrorCode.ERROR100.getCode());
                playBackResult.setMsg("????????????API???????????????");
                playBackCallback.call(playBackResult);
                return;
            }
            redisCatchStorage.startPlayback(streamInfo, inviteStreamInfo.getCallId());
            com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<StreamInfo> success = com.genersoft.iot.wvmp.vmanager.bean.WvmpResult.success(streamInfo);
            requestMessage.setData(success);
            playBackResult.setCode(ErrorCode.SUCCESS.getCode());
            playBackResult.setMsg(ErrorCode.SUCCESS.getMsg());
            playBackResult.setData(requestMessage);
            playBackResult.setMediaServerItem(inviteStreamInfo.getMediaServerItem());
            playBackResult.setResponse(inviteStreamInfo.getResponse());
            playBackCallback.call(playBackResult);
        };

        try {
            cmder.playbackStreamCmd(mediaServerItem, ssrcInfo, device, channelId, startTime, endTime, infoCallBack,
                    hookEvent, eventResult -> {
                        if (eventResult.type == SipSubscribe.EventResultType.response) {
                            ResponseEvent responseEvent = (ResponseEvent)eventResult.event;
                            String contentString = new String(responseEvent.getResponse().getRawContent());
                            // ??????ssrc
                            int ssrcIndex = contentString.indexOf("y=");
                            // ???????????????y??????
                            if (ssrcIndex >= 0) {
                                //ssrc???????????????10???????????????????????????????????????????????????f=????????? TODO ????????????????????????10???ssrc??????
                                String ssrcInResponse = contentString.substring(ssrcIndex + 2, ssrcIndex + 12);
                                // ?????????ssrc?????????????????????ssrc???????????????????????????
                                if (ssrcInfo.getSsrc().equals(ssrcInResponse)) {
                                    return;
                                }
                                logger.info("[????????????] ??????invite 200, ????????????????????????ssrc: {}", ssrcInResponse );
                                if (!mediaServerItem.isRtpEnable() || device.isSsrcCheck()) {
                                    logger.info("[????????????] SSRC?????? {}->{}", ssrcInfo.getSsrc(), ssrcInResponse);

                                    if (!mediaServerItem.getSsrcConfig().checkSsrc(ssrcInResponse)) {
                                        // ssrc ?????????
                                        // ??????ssrc
                                        mediaServerService.releaseSsrc(mediaServerItem.getId(), ssrcInfo.getSsrc());
                                        streamSession.remove(device.getDeviceId(), channelId, ssrcInfo.getStream());
                                        eventResult.msg = "??????????????????ssrc,?????????ssrc?????????";
                                        eventResult.statusCode = 400;
                                        errorEvent.response(eventResult);
                                        return;
                                    }

                                    // ???????????????streamId???????????????????????????????????????
                                    if (!mediaServerItem.isRtpEnable()) {
                                        // ????????????
                                        HookSubscribeForStreamChange hookSubscribe = HookSubscribeFactory.on_stream_changed("rtp", ssrcInfo.getStream(), true, "rtsp", mediaServerItem.getId());
                                        subscribe.removeSubscribe(hookSubscribe);
                                        hookSubscribe.getContent().put("stream", String.format("%08x", Integer.parseInt(ssrcInResponse)).toUpperCase());
                                        subscribe.addSubscribe(hookSubscribe, (MediaServerItem mediaServerItemInUse, JSONObject response)->{
                                            logger.info("[ZLM HOOK] ssrc?????????????????????????????? " + response.toJSONString());
                                            dynamicTask.stop(playBackTimeOutTaskKey);
                                            // hook??????
                                            onPublishHandlerForPlay(mediaServerItemInUse, response, device.getDeviceId(), channelId, uuid);
                                            hookEvent.call(new InviteStreamInfo(mediaServerItem, null, eventResult.callId, "rtp", ssrcInfo.getStream()));
                                        });
                                    }
                                    // ??????rtp server
                                    mediaServerService.closeRTPServer(mediaServerItem, ssrcInfo.getStream());
                                    // ????????????ssrc server
                                    mediaServerService.openRTPServer(mediaServerItem, ssrcInfo.getStream(), ssrcInResponse, device.isSsrcCheck(), true, ssrcInfo.getPort());
                                }
                            }
                        }

                    }, errorEvent);
        } catch (InvalidArgumentException | SipException | ParseException e) {
            logger.error("[??????????????????] ??????: {}", e.getMessage());

            SipSubscribe.EventResult eventResult = new SipSubscribe.EventResult(new CmdSendFailEvent(null));
            eventResult.msg = "??????????????????";
            errorEvent.response(eventResult);
        }
        return result;
    }

    @Override
    public DeferredResult<com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<StreamInfo>> download(String deviceId, String channelId, String startTime, String endTime, int downloadSpeed, InviteStreamCallback infoCallBack, PlayBackCallback hookCallBack) {
        Device device = storager.queryVideoDevice(deviceId);
        if (device == null) {
            return null;
        }
        MediaServerItem newMediaServerItem = getNewMediaServerItem(device);
        SsrcInfo ssrcInfo = mediaServerService.openRTPServer(newMediaServerItem, null, true, true);

        return download(newMediaServerItem, ssrcInfo, deviceId, channelId, startTime, endTime, downloadSpeed,infoCallBack, hookCallBack);
    }

    @Override
    public DeferredResult<com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<StreamInfo>> download(MediaServerItem mediaServerItem, SsrcInfo ssrcInfo, String deviceId, String channelId, String startTime, String endTime, int downloadSpeed, InviteStreamCallback infoCallBack, PlayBackCallback hookCallBack) {
        if (mediaServerItem == null || ssrcInfo == null) {
            return null;
        }
        String uuid = UUID.randomUUID().toString();
        String key = DeferredResultHolder.CALLBACK_CMD_DOWNLOAD + deviceId + channelId;
        DeferredResult<com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<StreamInfo>> result = new DeferredResult<>(30000L);
        Device device = storager.queryVideoDevice(deviceId);
        if (device == null) {
            throw new ControllerException(ErrorCode.ERROR400.getCode(), "?????????" + deviceId + "?????????");
        }

        resultHolder.put(key, uuid, result);
        RequestMessage requestMessage = new RequestMessage();
        requestMessage.setId(uuid);
        requestMessage.setKey(key);
        com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<StreamInfo> wvpResult = new com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<>();
        requestMessage.setData(wvpResult);
        PlayBackResult<RequestMessage> downloadResult = new PlayBackResult<>();
        downloadResult.setData(requestMessage);

        String downLoadTimeOutTaskKey = UUID.randomUUID().toString();
        dynamicTask.startDelay(downLoadTimeOutTaskKey, ()->{
            logger.warn(String.format("???????????????????????????deviceId???%s ???channelId???%s", deviceId, channelId));
            wvpResult.setCode(ErrorCode.ERROR100.getCode());
            wvpResult.setMsg("????????????????????????");
            downloadResult.setCode(ErrorCode.ERROR100.getCode());
            downloadResult.setMsg("????????????????????????");
            hookCallBack.call(downloadResult);

            // ??????????????????BYE ????????????ssrc???????????????????????????
            try {
                cmder.streamByeCmd(device, channelId, ssrcInfo.getStream(), null);
            } catch (InvalidArgumentException | ParseException | SipException e) {
                logger.error("[?????????]??????????????????????????? ??????BYE?????? {}", e.getMessage());
            } catch (SsrcTransactionNotFoundException e) {
                mediaServerService.releaseSsrc(mediaServerItem.getId(), ssrcInfo.getSsrc());
                mediaServerService.closeRTPServer(mediaServerItem, ssrcInfo.getStream());
                streamSession.remove(deviceId, channelId, ssrcInfo.getStream());
            }
            // ?????????????????????????????????
            hookCallBack.call(downloadResult);
        }, userSetting.getPlayTimeout());

        SipSubscribe.Event errorEvent = event -> {
            dynamicTask.stop(downLoadTimeOutTaskKey);
            downloadResult.setCode(ErrorCode.ERROR100.getCode());
            downloadResult.setMsg(String.format("????????????????????? ???????????? %s, %s", event.statusCode, event.msg));
            wvpResult.setCode(ErrorCode.ERROR100.getCode());
            wvpResult.setMsg(String.format("????????????????????? ???????????? %s, %s", event.statusCode, event.msg));
            downloadResult.setEvent(event);
            hookCallBack.call(downloadResult);
            streamSession.remove(device.getDeviceId(), channelId, ssrcInfo.getStream());
        };

        try {
            cmder.downloadStreamCmd(mediaServerItem, ssrcInfo, device, channelId, startTime, endTime, downloadSpeed, infoCallBack,
                    inviteStreamInfo -> {
                        logger.info("????????????????????? " + inviteStreamInfo.getResponse().toJSONString());
                        dynamicTask.stop(downLoadTimeOutTaskKey);
                        StreamInfo streamInfo = onPublishHandler(inviteStreamInfo.getMediaServerItem(), inviteStreamInfo.getResponse(), deviceId, channelId);
                        streamInfo.setStartTime(startTime);
                        streamInfo.setEndTime(endTime);
                        redisCatchStorage.startDownload(streamInfo, inviteStreamInfo.getCallId());
                        wvpResult.setCode(ErrorCode.SUCCESS.getCode());
                        wvpResult.setMsg(ErrorCode.SUCCESS.getMsg());
                        wvpResult.setData(streamInfo);
                        downloadResult.setCode(ErrorCode.SUCCESS.getCode());
                        downloadResult.setMsg(ErrorCode.SUCCESS.getMsg());
                        downloadResult.setMediaServerItem(inviteStreamInfo.getMediaServerItem());
                        downloadResult.setResponse(inviteStreamInfo.getResponse());
                        hookCallBack.call(downloadResult);
                    }, errorEvent);
        } catch (InvalidArgumentException | SipException | ParseException e) {
            logger.error("[??????????????????] ????????????: {}", e.getMessage());

            SipSubscribe.EventResult eventResult = new SipSubscribe.EventResult(new CmdSendFailEvent(null));
            eventResult.msg = "??????????????????";
            errorEvent.response(eventResult);
        }
        return result;
    }

    @Override
    public StreamInfo getDownLoadInfo(String deviceId, String channelId, String stream) {
        StreamInfo streamInfo = redisCatchStorage.queryDownload(deviceId, channelId, stream, null);
        if (streamInfo != null) {
            if (streamInfo.getProgress() == 1) {
                return streamInfo;
            }

            // ???????????????????????????
            String mediaServerId = streamInfo.getMediaServerId();
            MediaServerItem mediaServerItem = mediaServerService.getOne(mediaServerId);
            if (mediaServerItem == null) {
                logger.warn("??????????????????????????????????????????");
                return null;
            }
            if (mediaServerItem.getRecordAssistPort() > 0) {
                JSONObject jsonObject = assistRESTfulUtils.fileDuration(mediaServerItem, streamInfo.getApp(), streamInfo.getStream(), null);
                if (jsonObject != null && jsonObject.getInteger("code") == 0) {
                    long duration = jsonObject.getLong("data");

                    if (duration == 0) {
                        streamInfo.setProgress(0);
                    }else {
                        String startTime = streamInfo.getStartTime();
                        String endTime = streamInfo.getEndTime();
                        long start = DateUtil.yyyy_MM_dd_HH_mm_ssToTimestamp(startTime);
                        long end = DateUtil.yyyy_MM_dd_HH_mm_ssToTimestamp(endTime);

                        BigDecimal currentCount = new BigDecimal(duration/1000);
                        BigDecimal totalCount = new BigDecimal(end-start);
                        BigDecimal divide = currentCount.divide(totalCount,2, RoundingMode.HALF_UP);
                        double process = divide.doubleValue();
                        streamInfo.setProgress(process);
                    }
                }
            }
        }
        return streamInfo;
    }

    @Override
    public void onPublishHandlerForDownload(InviteStreamInfo inviteStreamInfo, String deviceId, String channelId, String uuid) {
        RequestMessage msg = new RequestMessage();
        msg.setKey(DeferredResultHolder.CALLBACK_CMD_DOWNLOAD + deviceId + channelId);
        msg.setId(uuid);
        StreamInfo streamInfo = onPublishHandler(inviteStreamInfo.getMediaServerItem(), inviteStreamInfo.getResponse(), deviceId, channelId);
        if (streamInfo != null) {
            redisCatchStorage.startDownload(streamInfo, inviteStreamInfo.getCallId());
            msg.setData(JSON.toJSONString(streamInfo));
            resultHolder.invokeResult(msg);
        } else {
            logger.warn("????????????API???????????????");
            msg.setData(com.genersoft.iot.wvmp.vmanager.bean.WvmpResult.fail(ErrorCode.ERROR100.getCode(), "????????????API???????????????"));
            resultHolder.invokeResult(msg);
        }
    }


    public StreamInfo onPublishHandler(MediaServerItem mediaServerItem, JSONObject resonse, String deviceId, String channelId) {
        String streamId = resonse.getString("stream");
        JSONArray tracks = resonse.getJSONArray("tracks");
        StreamInfo streamInfo = mediaService.getStreamInfoByAppAndStream(mediaServerItem,"rtp", streamId, tracks, null);
        streamInfo.setDeviceID(deviceId);
        streamInfo.setChannelId(channelId);
        return streamInfo;
    }

    @Override
    public void zlmServerOffline(String mediaServerId) {
        // ???????????????????????????????????????
        List<SendRtpItem> sendRtpItems = redisCatchStorage.querySendRTPServer(null);
        if (sendRtpItems.size() > 0) {
            for (SendRtpItem sendRtpItem : sendRtpItems) {
                if (sendRtpItem.getMediaServerId().equals(mediaServerId)) {
                    ParentPlatform platform = storager.queryParentPlatByServerGBId(sendRtpItem.getPlatformId());
                    try {
                        sipCommanderFroPlatform.streamByeCmd(platform, sendRtpItem.getCallId());
                    } catch (SipException | InvalidArgumentException | ParseException e) {
                        logger.error("[??????????????????] ???????????? ??????BYE: {}", e.getMessage());
                    }
                }
            }
        }
        // ?????????????????????????????????
        List<SsrcTransaction> allSsrc = streamSession.getAllSsrc();
        if (allSsrc.size() > 0) {
            for (SsrcTransaction ssrcTransaction : allSsrc) {
                if(ssrcTransaction.getMediaServerId().equals(mediaServerId)) {
                    Device device = deviceService.queryDevice(ssrcTransaction.getDeviceId());
                    if (device == null) {
                        continue;
                    }
                    try {
                        cmder.streamByeCmd(device, ssrcTransaction.getChannelId(),
                                ssrcTransaction.getStream(), null);
                    } catch (InvalidArgumentException | ParseException | SipException |
                             SsrcTransactionNotFoundException e) {
                        logger.error("[zlm??????]??????????????????zlm???????????? ??????BYE?????? {}", e.getMessage());
                    }
                }
            }
        }
    }

    @Override
    public void zlmServerOnline(String mediaServerId) {
        // TODO ????????????????????????????????????????????????????????????bye
//        MediaServerItem mediaServerItem = mediaServerService.getOne(mediaServerId);
//        zlmresTfulUtils.getMediaList(mediaServerItem, (mediaList ->{
//            Integer code = mediaList.getInteger("code");
//            if (code == 0) {
//                JSONArray data = mediaList.getJSONArray("data");
//                if (data == null || data.size() == 0) {
//                    zlmServerOffline(mediaServerId);
//                }else {
//                    Map<String, JSONObject> mediaListMap = new HashMap<>();
//                    for (int i = 0; i < data.size(); i++) {
//                        JSONObject json = data.getJSONObject(i);
//                        String app = json.getString("app");
//                        if ("rtp".equals(app)) {
//                            String stream = json.getString("stream");
//                            if (mediaListMap.get(stream) != null) {
//                                continue;
//                            }
//                            mediaListMap.put(stream, json);
//                            // ?????????????????????????????????
//                            List<SsrcTransaction> ssrcTransactions = streamSession.getSsrcTransactionForAll(null, null, null, stream);
//                            if (ssrcTransactions.size() > 0) {
//                                for (SsrcTransaction ssrcTransaction : ssrcTransactions) {
//                                    if(ssrcTransaction.getMediaServerId().equals(mediaServerId)) {
//                                        cmder.streamByeCmd(ssrcTransaction.getDeviceId(), ssrcTransaction.getChannelId(),
//                                                ssrcTransaction.getStream(), null);
//                                    }
//                                }
//                            }
//                        }
//                    }
//                    if (mediaListMap.size() > 0 ) {
//                        // ???????????????????????????????????????
//                        List<SendRtpItem> sendRtpItems = redisCatchStorage.querySendRTPServer(null);
//                        if (sendRtpItems.size() > 0) {
//                            for (SendRtpItem sendRtpItem : sendRtpItems) {
//                                if (sendRtpItem.getMediaServerId().equals(mediaServerId)) {
//                                    if (mediaListMap.get(sendRtpItem.getStreamId()) == null) {
//                                        ParentPlatform platform = storager.queryPlatformByServerGBId(sendRtpItem.getPlatformId());
//                                        sipCommanderFroPlatform.streamByeCmd(platform, sendRtpItem.getCallId());
//                                    }
//                                }
//                            }
//                        }
//                    }
//                }
//            }
//        }));
    }

    @Override
    public void pauseRtp(String streamId) throws ServiceException, InvalidArgumentException, ParseException, SipException {
        String key = redisCatchStorage.queryPlaybackForKey(null, null, streamId, null);
        StreamInfo streamInfo = redisCatchStorage.queryPlayback(null, null, streamId, null);
        if (null == streamInfo) {
            logger.warn("streamId?????????!");
            throw new ServiceException("streamId?????????");
        }
        streamInfo.setPause(true);
        RedisUtil.set(key, streamInfo);
        MediaServerItem mediaServerItem = mediaServerService.getOne(streamInfo.getMediaServerId());
        if (null == mediaServerItem) {
            logger.warn("mediaServer ?????????!");
            throw new ServiceException("mediaServer?????????");
        }
        // zlm ??????RTP????????????
        JSONObject jsonObject = zlmresTfulUtils.pauseRtpCheck(mediaServerItem, streamId);
        if (jsonObject == null || jsonObject.getInteger("code") != 0) {
            throw new ServiceException("??????RTP????????????");
        }
        Device device = storager.queryVideoDevice(streamInfo.getDeviceID());
        cmder.playPauseCmd(device, streamInfo);
    }

    @Override
    public void resumeRtp(String streamId) throws ServiceException, InvalidArgumentException, ParseException, SipException {
        String key = redisCatchStorage.queryPlaybackForKey(null, null, streamId, null);
        StreamInfo streamInfo = redisCatchStorage.queryPlayback(null, null, streamId, null);
        if (null == streamInfo) {
            logger.warn("streamId?????????!");
            throw new ServiceException("streamId?????????");
        }
        streamInfo.setPause(false);
        RedisUtil.set(key, streamInfo);
        MediaServerItem mediaServerItem = mediaServerService.getOne(streamInfo.getMediaServerId());
        if (null == mediaServerItem) {
            logger.warn("mediaServer ?????????!");
            throw new ServiceException("mediaServer?????????");
        }
        // zlm ??????RTP????????????
        JSONObject jsonObject = zlmresTfulUtils.resumeRtpCheck(mediaServerItem, streamId);
        if (jsonObject == null || jsonObject.getInteger("code") != 0) {
            throw new ServiceException("??????RTP????????????");
        }
        Device device = storager.queryVideoDevice(streamInfo.getDeviceID());
        cmder.playResumeCmd(device, streamInfo);
    }
}
