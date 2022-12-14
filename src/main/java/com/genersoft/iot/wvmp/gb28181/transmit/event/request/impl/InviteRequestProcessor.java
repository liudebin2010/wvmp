package com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl;

import com.alibaba.fastjson.JSONObject;
import com.genersoft.iot.wvmp.conf.DynamicTask;
import com.genersoft.iot.wvmp.conf.UserSetting;
import com.genersoft.iot.wvmp.gb28181.bean.*;
import com.genersoft.iot.wvmp.gb28181.event.SipSubscribe;
import com.genersoft.iot.wvmp.gb28181.session.VideoStreamSessionManager;
import com.genersoft.iot.wvmp.gb28181.transmit.SipProcessorObserver;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.ISipCommander;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.impl.SipCommanderFroPlatform;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.ISipRequestProcessor;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.SipRequestProcessorParent;
import com.genersoft.iot.wvmp.gb28181.utils.SipUtils;
import com.genersoft.iot.wvmp.zlm.ZlmHttpHookSubscribe;
import com.genersoft.iot.wvmp.zlm.ZlmMediaListManager;
import com.genersoft.iot.wvmp.zlm.ZlmRtpServerFactory;
import com.genersoft.iot.wvmp.service.IMediaServerService;
import com.genersoft.iot.wvmp.service.IPlayService;
import com.genersoft.iot.wvmp.service.IStreamProxyService;
import com.genersoft.iot.wvmp.service.IStreamPushService;
import com.genersoft.iot.wvmp.service.bean.MessageForPushChannel;
import com.genersoft.iot.wvmp.service.bean.SsrcInfo;
import com.genersoft.iot.wvmp.service.redisMsg.RedisGbPlayMsgListener;
import com.genersoft.iot.wvmp.service.redisMsg.RedisPushStreamResponseListener;
import com.genersoft.iot.wvmp.storager.IRedisCatchStorage;
import com.genersoft.iot.wvmp.storager.IVideoManagerStorage;
import com.genersoft.iot.wvmp.utils.DateUtil;
import com.genersoft.iot.wvmp.zlm.dto.*;
import gov.nist.javax.sdp.TimeDescriptionImpl;
import gov.nist.javax.sdp.fields.TimeField;
import gov.nist.javax.sip.message.SIPRequest;
import gov.nist.javax.sip.message.SIPResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sdp.*;
import javax.sip.*;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.time.Instant;
import java.util.Vector;

/**
 * SIP??????????????? INVITE??????
 */
@SuppressWarnings("rawtypes")
@Component
public class InviteRequestProcessor extends SipRequestProcessorParent implements InitializingBean, ISipRequestProcessor {

    private final static Logger logger = LoggerFactory.getLogger(InviteRequestProcessor.class);

    private final String method = "INVITE";

    @Autowired
    private SipCommanderFroPlatform cmderFroPlatform;

    @Autowired
    private IVideoManagerStorage storager;

    @Autowired
    private IStreamPushService streamPushService;
    @Autowired
    private IStreamProxyService streamProxyService;

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Autowired
    private DynamicTask dynamicTask;

    @Autowired
    private RedisPushStreamResponseListener redisPushStreamResponseListener;

    @Autowired
    private IPlayService playService;

    @Autowired
    private ISipCommander commander;

    @Autowired
    private ZlmRtpServerFactory zlmrtpServerFactory;

    @Autowired
    private IMediaServerService mediaServerService;

    @Autowired
    private ZlmHttpHookSubscribe zlmHttpHookSubscribe;

    @Autowired
    private SipProcessorObserver sipProcessorObserver;

    @Autowired
    private VideoStreamSessionManager sessionManager;

    @Autowired
    private UserSetting userSetting;

    @Autowired
    private ZlmMediaListManager mediaListManager;


    @Autowired
    private RedisGbPlayMsgListener redisGbPlayMsgListener;


    @Override
    public void afterPropertiesSet() throws Exception {
        // ???????????????????????????
        sipProcessorObserver.addRequestProcessor(method, this);
    }

    /**
     * ??????invite??????
     *
     * @param evt ????????????
     */
    @Override
    public void process(RequestEvent evt) {
        //  Invite Request???????????????????????????????????????????????????????????????????????????????????????
        try {
            Request request = evt.getRequest();
            String channelId = SipUtils.getChannelIdFromRequest(request);
            String requesterId = SipUtils.getUserIdFromFromHeader(request);
            CallIdHeader callIdHeader = (CallIdHeader) request.getHeader(CallIdHeader.NAME);
            ServerTransaction serverTransaction = getServerTransaction(evt);
            if (requesterId == null || channelId == null) {
                logger.info("?????????FromHeader???Address??????????????????id?????????400");
                // ??????????????? ???400???????????????
                responseAck(serverTransaction, Response.BAD_REQUEST);
                return;
            }


            // ????????????????????????????????????\??????
            ParentPlatform platform = storager.queryParentPlatByServerGBId(requesterId);
            if (platform == null) {
                inviteFromDeviceHandle(serverTransaction, requesterId);
            } else {
                // ?????????????????????????????????
                DeviceChannel channel = storager.queryChannelInParentPlatform(requesterId, channelId);
                GbStream gbStream = storager.queryStreamInParentPlatform(requesterId, channelId);
                PlatformCatalog catalog = storager.getCatalog(channelId);

                MediaServerItem mediaServerItem = null;
                StreamPushItem streamPushItem = null;
                StreamProxyItem proxyByAppAndStream =null;
                // ??????????????????????????????
                if (channel != null && gbStream == null) {
//                    if (channel.getStatus() == 0) {
//                        logger.info("?????????????????????400");
//                        responseAck(serverTransaction, Response.BAD_REQUEST, "channel [" + channel.getChannelId() + "] offline");
//                        return;
//                    }
                    // ??????????????????100???TRYING
                    responseAck(serverTransaction, Response.TRYING);
                } else if (channel == null && gbStream != null) {

                    String mediaServerId = gbStream.getMediaServerId();
                    mediaServerItem = mediaServerService.getOne(mediaServerId);
                    if (mediaServerItem == null) {
                        if ("proxy".equals(gbStream.getStreamType())) {
                            logger.info("[ app={}, stream={} ]?????????zlm {}?????????410", gbStream.getApp(), gbStream.getStream(), mediaServerId);
                            responseAck(serverTransaction, Response.GONE);
                            return;
                        } else {
                            streamPushItem = streamPushService.getPush(gbStream.getApp(), gbStream.getStream());
                            if (streamPushItem == null || streamPushItem.getServerId().equals(userSetting.getServerId())) {
                                logger.info("[ app={}, stream={} ]?????????zlm {}?????????410", gbStream.getApp(), gbStream.getStream(), mediaServerId);
                                responseAck(serverTransaction, Response.GONE);
                                return;
                            }
                        }
                    } else {
                        if ("push".equals(gbStream.getStreamType())) {
                            streamPushItem = streamPushService.getPush(gbStream.getApp(), gbStream.getStream());
                            if (streamPushItem == null) {
                                logger.info("[ app={}, stream={} ]?????????zlm {}?????????410", gbStream.getApp(), gbStream.getStream(), mediaServerId);
                                responseAck(serverTransaction, Response.GONE);
                                return;
                            }
                        }else if("proxy".equals(gbStream.getStreamType())){
                            proxyByAppAndStream = streamProxyService.getStreamProxyByAppAndStream(gbStream.getApp(), gbStream.getStream());
                            if (proxyByAppAndStream == null) {
                                logger.info("[ app={}, stream={} ]?????????zlm {}?????????410", gbStream.getApp(), gbStream.getStream(), mediaServerId);
                                responseAck(serverTransaction, Response.GONE);
                                return;
                            }
                        }
                    }
                    responseAck(serverTransaction, Response.CALL_IS_BEING_FORWARDED); // ??????????????????181??????????????????
                } else if (catalog != null) {
                    responseAck(serverTransaction, Response.BAD_REQUEST, "catalog channel can not play"); // ?????????????????????
                    return;
                } else {
                    logger.info("????????????????????????404");
                    responseAck(serverTransaction, Response.NOT_FOUND); // ?????????????????????404??????????????????
                    return;
                }
                // ??????sdp??????, ??????jainsip ?????????sdp????????????
                String contentString = new String(request.getRawContent());

                // jainSip?????????y=????????? ??????????????????
                int ssrcIndex = contentString.indexOf("y=");
                // ???????????????y??????
                String ssrcDefault = "0000000000";
                String ssrc;
                SessionDescription sdp;
                if (ssrcIndex >= 0) {
                    //ssrc???????????????10??????????????????????????????????????????????????????f=?????????
                    ssrc = contentString.substring(ssrcIndex + 2, ssrcIndex + 12);
                    String substring = contentString.substring(0, contentString.indexOf("y="));
                    sdp = SdpFactory.getInstance().createSessionDescription(substring);
                } else {
                    ssrc = ssrcDefault;
                    sdp = SdpFactory.getInstance().createSessionDescription(contentString);
                }
                String sessionName = sdp.getSessionName().getValue();

                Long startTime = null;
                Long stopTime = null;
                Instant start = null;
                Instant end = null;
                if (sdp.getTimeDescriptions(false) != null && sdp.getTimeDescriptions(false).size() > 0) {
                    TimeDescriptionImpl timeDescription = (TimeDescriptionImpl) (sdp.getTimeDescriptions(false).get(0));
                    TimeField startTimeFiled = (TimeField) timeDescription.getTime();
                    startTime = startTimeFiled.getStartTime();
                    stopTime = startTimeFiled.getStopTime();

                    start = Instant.ofEpochSecond(startTime);
                    end = Instant.ofEpochSecond(stopTime);
                }
                //  ?????????????????????
                Vector mediaDescriptions = sdp.getMediaDescriptions(true);
                // ??????????????????PS ??????96
                //String ip = null;
                int port = -1;
                boolean mediaTransmissionTCP = false;
                Boolean tcpActive = null;
                for (Object description : mediaDescriptions) {
                    MediaDescription mediaDescription = (MediaDescription) description;
                    Media media = mediaDescription.getMedia();

                    Vector mediaFormats = media.getMediaFormats(false);
                    if (mediaFormats.contains("96")) {
                        port = media.getMediaPort();
                        //String mediaType = media.getMediaType();
                        String protocol = media.getProtocol();

                        // ??????TCP????????????udp??? ????????????udp
                        if ("TCP/RTP/AVP".equalsIgnoreCase(protocol)) {
                            String setup = mediaDescription.getAttribute("setup");
                            if (setup != null) {
                                mediaTransmissionTCP = true;
                                if ("active".equalsIgnoreCase(setup)) {
                                    tcpActive = true;
                                } else if ("passive".equalsIgnoreCase(setup)) {
                                    tcpActive = false;
                                }
                            }
                        }
                        break;
                    }
                }
                if (port == -1) {
                    logger.info("?????????????????????????????????415");
                    // ????????????????????????
                    responseAck(serverTransaction, Response.UNSUPPORTED_MEDIA_TYPE); // ????????????????????????415
                    return;
                }
                String username = sdp.getOrigin().getUsername();
                String addressStr = sdp.getOrigin().getAddress();

                logger.info("[????????????]?????????{}??? ?????????{}, ?????????{}:{}??? ssrc???{}", username, channelId, addressStr, port, ssrc);
                Device device = null;
                // ?????? channel ??? gbStream ?????????null ???????????????????????????????????????
                if (channel != null) {
                    device = storager.queryVideoDeviceByPlatformIdAndChannelId(requesterId, channelId);
                    if (device == null) {
                        logger.warn("????????????{}?????????{}????????????????????????", requesterId, channel);
                        responseAck(serverTransaction, Response.SERVER_INTERNAL_ERROR);
                        return;
                    }
                    mediaServerItem = playService.getNewMediaServerItem(device);
                    if (mediaServerItem == null) {
                        logger.warn("??????????????????zlm");
                        responseAck(serverTransaction, Response.BUSY_HERE);
                        return;
                    }
                    SendRtpItem sendRtpItem = zlmrtpServerFactory.createSendRtpItem(mediaServerItem, addressStr, port, ssrc, requesterId,
                            device.getDeviceId(), channelId,
                            mediaTransmissionTCP);

                    if (tcpActive != null) {
                        sendRtpItem.setTcpActive(tcpActive);
                    }
                    if (sendRtpItem == null) {
                        logger.warn("???????????????????????????");
                        responseAck(serverTransaction, Response.BUSY_HERE);
                        return;
                    }
                    sendRtpItem.setCallId(callIdHeader.getCallId());
                    sendRtpItem.setPlayType("Play".equalsIgnoreCase(sessionName) ? InviteStreamType.PLAY : InviteStreamType.PLAYBACK);

                    Long finalStartTime = startTime;
                    Long finalStopTime = stopTime;
                    ZlmHttpHookSubscribe.Event hookEvent = (mediaServerItemInUSe, responseJSON) -> {
                        String app = responseJSON.getString("app");
                        String stream = responseJSON.getString("stream");
                        logger.info("[????????????]??????????????????????????? ??????200OK(SDP)??? {}/{}", app, stream);
                        //     * 0 ????????????????????????
                        //     * 1 ?????????????????????????????????????????????ack
                        //     * 2 ?????????
                        sendRtpItem.setStatus(1);
                        redisCatchStorage.updateSendRTPSever(sendRtpItem);

                        StringBuffer content = new StringBuffer(200);
                        content.append("v=0\r\n");
                        content.append("o=" + channelId + " 0 0 IN IP4 " + mediaServerItemInUSe.getSdpIp() + "\r\n");
                        content.append("s=" + sessionName + "\r\n");
                        content.append("c=IN IP4 " + mediaServerItemInUSe.getSdpIp() + "\r\n");
                        if ("Playback".equalsIgnoreCase(sessionName)) {
                            content.append("t=" + finalStartTime + " " + finalStopTime + "\r\n");
                        } else {
                            content.append("t=0 0\r\n");
                        }
                        content.append("m=video " + sendRtpItem.getLocalPort() + " RTP/AVP 96\r\n");
                        content.append("a=sendonly\r\n");
                        content.append("a=rtpmap:96 PS/90000\r\n");
                        content.append("y=" + sendRtpItem.getSsrc() + "\r\n");
                        content.append("f=\r\n");

                        try {
                            // ???????????????Ack????????????bye,?????????????????????10???
                            dynamicTask.startDelay(callIdHeader.getCallId(), () -> {
                                logger.info("Ack ????????????");
                                mediaServerService.releaseSsrc(mediaServerItemInUSe.getId(), sendRtpItem.getSsrc());
                                // ??????bye
                                try {
                                    cmderFroPlatform.streamByeCmd(platform, callIdHeader.getCallId());
                                } catch (SipException | InvalidArgumentException | ParseException e) {
                                    logger.error("[??????????????????] ???????????? ??????BYE: {}", e.getMessage());
                                }
                            }, 60 * 1000);
                            responseSdpAck(serverTransaction, content.toString(), platform);

                        } catch (SipException e) {
                            e.printStackTrace();
                        } catch (InvalidArgumentException e) {
                            e.printStackTrace();
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                    };
                    SipSubscribe.Event errorEvent = ((event) -> {
                        // ????????????????????????????????????????????????
                        Response response = null;
                        try {
                            response = getMessageFactory().createResponse(event.statusCode, evt.getRequest());
                            serverTransaction.sendResponse(response);
                            System.out.println("????????????????????????????????????????????????");
                            if (serverTransaction.getDialog() != null) {
                                serverTransaction.getDialog().delete();
                            }
                        } catch (ParseException | SipException | InvalidArgumentException e) {
                            e.printStackTrace();
                        }
                    });
                    sendRtpItem.setApp("rtp");
                    if ("Playback".equalsIgnoreCase(sessionName)) {
                        sendRtpItem.setPlayType(InviteStreamType.PLAYBACK);
                        SsrcInfo ssrcInfo = mediaServerService.openRTPServer(mediaServerItem, null, true, true);
                        sendRtpItem.setStreamId(ssrcInfo.getStream());
                        // ??????redis??? ???????????????
                        redisCatchStorage.updateSendRTPSever(sendRtpItem);
                        playService.playBack(mediaServerItem, ssrcInfo, device.getDeviceId(), channelId, DateUtil.formatter.format(start),
                                DateUtil.formatter.format(end), null, result -> {
                                    if (result.getCode() != 0) {
                                        logger.warn("??????????????????");
                                        if (result.getEvent() != null) {
                                            errorEvent.response(result.getEvent());
                                        }
                                        redisCatchStorage.deleteSendRTPServer(platform.getServerGBId(), channelId, callIdHeader.getCallId(), null);
                                        try {
                                            responseAck(serverTransaction, Response.REQUEST_TIMEOUT);
                                        } catch (SipException e) {
                                            e.printStackTrace();
                                        } catch (InvalidArgumentException e) {
                                            e.printStackTrace();
                                        } catch (ParseException e) {
                                            e.printStackTrace();
                                        }
                                    } else {
                                        if (result.getMediaServerItem() != null) {
                                            hookEvent.response(result.getMediaServerItem(), result.getResponse());
                                        }
                                    }
                                });
                    } else {
                        sendRtpItem.setPlayType(InviteStreamType.PLAY);
                        SsrcTransaction playTransaction = sessionManager.getSsrcTransaction(device.getDeviceId(), channelId, "play", null);
                        if (playTransaction != null) {
                            Boolean streamReady = zlmrtpServerFactory.isStreamReady(mediaServerItem, "rtp", playTransaction.getStream());
                            if (!streamReady) {
                                boolean hasRtpServer = mediaServerService.checkRtpServer(mediaServerItem, "rtp", playTransaction.getStream());
                                if (hasRtpServer) {
                                    logger.info("[????????????]????????????rtpServer????????????????????????????????????????????????");
                                    HookSubscribeForStreamChange hookSubscribe = HookSubscribeFactory.on_stream_changed("rtp", playTransaction.getStream(), true, "rtsp", mediaServerItem.getId());
                                    zlmHttpHookSubscribe.addSubscribe(hookSubscribe, hookEvent);
                                }else {
                                    playTransaction = null;
                                }
                            }
                        }
                        if (playTransaction == null) {
                            String streamId = null;
                            if (mediaServerItem.isRtpEnable()) {
                                streamId = String.format("%s_%s", device.getDeviceId(), channelId);
                            }
                            SsrcInfo ssrcInfo = mediaServerService.openRTPServer(mediaServerItem, streamId, null, device.isSsrcCheck(), false);
                            logger.info(JSONObject.toJSONString(ssrcInfo));
                            sendRtpItem.setStreamId(ssrcInfo.getStream());
                            sendRtpItem.setSsrc(ssrc.equals(ssrcDefault) ? ssrcInfo.getSsrc() : ssrc);

                            // ??????redis??? ???????????????
                            redisCatchStorage.updateSendRTPSever(sendRtpItem);
                            playService.play(mediaServerItem, ssrcInfo, device, channelId, hookEvent, errorEvent, (code, msg) -> {
                                logger.info("[????????????]??????, ?????????{}??? ?????????{}", username, channelId);
                                redisCatchStorage.deleteSendRTPServer(platform.getServerGBId(), channelId, callIdHeader.getCallId(), null);
                            }, null);
                        } else {
                            sendRtpItem.setStreamId(playTransaction.getStream());
                            // ??????redis??? ???????????????
                            redisCatchStorage.updateSendRTPSever(sendRtpItem);
                            JSONObject jsonObject = new JSONObject();
                            jsonObject.put("app", sendRtpItem.getApp());
                            jsonObject.put("stream", sendRtpItem.getStreamId());
                            hookEvent.response(mediaServerItem, jsonObject);
                        }
                    }
                } else if (gbStream != null) {
                    if("push".equals(gbStream.getStreamType())) {
                        if (streamPushItem != null && streamPushItem.isPushIng()) {
                            // ????????????
                            pushStream(evt, serverTransaction, gbStream, streamPushItem, platform, callIdHeader, mediaServerItem, port, tcpActive,
                                    mediaTransmissionTCP, channelId, addressStr, ssrc, requesterId);
                        } else {
                            // ????????? ??????
                            notifyStreamOnline(evt, serverTransaction,gbStream, streamPushItem, platform, callIdHeader, mediaServerItem, port, tcpActive,
                                    mediaTransmissionTCP, channelId, addressStr, ssrc, requesterId);
                        }
                    }else if ("proxy".equals(gbStream.getStreamType())){
                        if(null != proxyByAppAndStream &&proxyByAppAndStream.isStatus()){
                            pushProxyStream(evt, serverTransaction, gbStream,  platform, callIdHeader, mediaServerItem, port, tcpActive,
                                    mediaTransmissionTCP, channelId, addressStr, ssrc, requesterId);
                        }else{
                            //??????????????????
                            boolean start1 = streamProxyService.start(gbStream.getApp(), gbStream.getStream());
                            if(start1) {
                                pushProxyStream(evt, serverTransaction, gbStream,  platform, callIdHeader, mediaServerItem, port, tcpActive,
                                        mediaTransmissionTCP, channelId, addressStr, ssrc, requesterId);
                            }else{
                                //???????????????
                                notifyStreamOnline(evt, serverTransaction,gbStream, null, platform, callIdHeader, mediaServerItem, port, tcpActive,
                                        mediaTransmissionTCP, channelId, addressStr, ssrc, requesterId);
                            }
                        }

                    }
                }
            }

        } catch (SipException | InvalidArgumentException | ParseException e) {
            e.printStackTrace();
            logger.warn("sdp????????????");
            e.printStackTrace();
        } catch (SdpParseException e) {
            e.printStackTrace();
        } catch (SdpException e) {
            e.printStackTrace();
        }
    }

    /**
     * ????????????
     */
    private void pushProxyStream(RequestEvent evt, ServerTransaction serverTransaction, GbStream gbStream, ParentPlatform platform,
                            CallIdHeader callIdHeader, MediaServerItem mediaServerItem,
                            int port, Boolean tcpActive, boolean mediaTransmissionTCP,
                            String channelId, String addressStr, String ssrc, String requesterId) throws InvalidArgumentException, ParseException, SipException {
            Boolean streamReady = zlmrtpServerFactory.isStreamReady(mediaServerItem, gbStream.getApp(), gbStream.getStream());
            if (streamReady) {
                // ???????????????
                SendRtpItem sendRtpItem = zlmrtpServerFactory.createSendRtpItem(mediaServerItem, addressStr, port, ssrc, requesterId,
                        gbStream.getApp(), gbStream.getStream(), channelId,
                        mediaTransmissionTCP);

                if (sendRtpItem == null) {
                    logger.warn("???????????????????????????");
                    responseAck(serverTransaction, Response.BUSY_HERE);
                    return;
                }
                if (tcpActive != null) {
                    sendRtpItem.setTcpActive(tcpActive);
                }
                sendRtpItem.setPlayType(InviteStreamType.PUSH);
                // ??????redis??? ???????????????
                sendRtpItem.setStatus(1);
                sendRtpItem.setCallId(callIdHeader.getCallId());
                SIPRequest request = (SIPRequest) evt.getRequest();
                sendRtpItem.setFromTag(request.getFromTag());

                SIPResponse response = sendStreamAck(mediaServerItem, serverTransaction, sendRtpItem, platform, evt);
                if (response != null) {
                    sendRtpItem.setToTag(response.getToTag());
                }
                redisCatchStorage.updateSendRTPSever(sendRtpItem);

        }

    }
    private void pushStream(RequestEvent evt, ServerTransaction serverTransaction, GbStream gbStream, StreamPushItem streamPushItem, ParentPlatform platform,
                            CallIdHeader callIdHeader, MediaServerItem mediaServerItem,
                            int port, Boolean tcpActive, boolean mediaTransmissionTCP,
                            String channelId, String addressStr, String ssrc, String requesterId) throws InvalidArgumentException, ParseException, SipException {
        // ??????
        if (streamPushItem.isSelf()) {
            Boolean streamReady = zlmrtpServerFactory.isStreamReady(mediaServerItem, gbStream.getApp(), gbStream.getStream());
            if (streamReady) {
                // ???????????????
                SendRtpItem sendRtpItem = zlmrtpServerFactory.createSendRtpItem(mediaServerItem, addressStr, port, ssrc, requesterId,
                        gbStream.getApp(), gbStream.getStream(), channelId,
                        mediaTransmissionTCP);

                if (sendRtpItem == null) {
                    logger.warn("???????????????????????????");
                    responseAck(serverTransaction, Response.BUSY_HERE);
                    return;
                }
                if (tcpActive != null) {
                    sendRtpItem.setTcpActive(tcpActive);
                }
                sendRtpItem.setPlayType(InviteStreamType.PUSH);
                // ??????redis??? ???????????????
                sendRtpItem.setStatus(1);
                sendRtpItem.setCallId(callIdHeader.getCallId());

                SIPRequest request = (SIPRequest) evt.getRequest();
                sendRtpItem.setFromTag(request.getFromTag());
                SIPResponse response = sendStreamAck(mediaServerItem, serverTransaction, sendRtpItem, platform, evt);
                if (response != null) {
                    sendRtpItem.setToTag(response.getToTag());
                }

                redisCatchStorage.updateSendRTPSever(sendRtpItem);

            } else {
                // ????????? ??????
                notifyStreamOnline(evt, serverTransaction,gbStream, streamPushItem, platform, callIdHeader, mediaServerItem, port, tcpActive,
                        mediaTransmissionTCP, channelId, addressStr, ssrc, requesterId);
            }

        } else {
            // ??????????????????
            otherWvpPushStream(evt, serverTransaction, gbStream, streamPushItem, platform, callIdHeader, mediaServerItem, port, tcpActive,
                    mediaTransmissionTCP, channelId, addressStr, ssrc, requesterId);
        }
    }
    /**
     * ???????????????
     */
    private void notifyStreamOnline(RequestEvent evt, ServerTransaction serverTransaction, GbStream gbStream, StreamPushItem streamPushItem, ParentPlatform platform,
                                    CallIdHeader callIdHeader, MediaServerItem mediaServerItem,
                                    int port, Boolean tcpActive, boolean mediaTransmissionTCP,
                                    String channelId, String addressStr, String ssrc, String requesterId) throws InvalidArgumentException, ParseException, SipException {
        if ("proxy".equals(gbStream.getStreamType())) {
            // TODO ??????????????????????????????
            logger.info("[ app={}, stream={} ]??????????????????????????????????????????", gbStream.getApp(), gbStream.getStream());
            responseAck(serverTransaction, Response.BAD_REQUEST, "channel [" + gbStream.getGbId() + "] offline");
        } else if ("push".equals(gbStream.getStreamType())) {
            if (!platform.isStartOfflinePush()) {
                // ????????????????????????????????????????????????????????????
                responseAck(serverTransaction, Response.TEMPORARILY_UNAVAILABLE, "channel stream not pushing");
                return;
            }
            // ??????redis????????????????????????
            logger.info("[ app={}, stream={} ]????????????????????????redis??????????????????????????????", gbStream.getApp(), gbStream.getStream());

            MessageForPushChannel messageForPushChannel = MessageForPushChannel.getInstance(1,
                    gbStream.getApp(), gbStream.getStream(), gbStream.getGbId(), gbStream.getPlatformId(),
                    platform.getName(), null, gbStream.getMediaServerId());
            redisCatchStorage.sendStreamPushRequestedMsg(messageForPushChannel);
            // ????????????
            dynamicTask.startDelay(callIdHeader.getCallId(), () -> {
                logger.info("[ app={}, stream={} ] ??????????????????????????????", gbStream.getApp(), gbStream.getStream());
                try {
                    mediaListManager.removedChannelOnlineEventLister(gbStream.getApp(), gbStream.getStream());
                    responseAck(serverTransaction, Response.REQUEST_TIMEOUT); // ??????
                } catch (SipException e) {
                    e.printStackTrace();
                } catch (InvalidArgumentException e) {
                    e.printStackTrace();
                } catch (ParseException e) {
                    e.printStackTrace();
                }
            }, userSetting.getPlatformPlayTimeout());
            // ????????????
            int finalPort = port;
            Boolean finalTcpActive = tcpActive;

            // ??????????????????????????????
            mediaListManager.addChannelOnlineEventLister(gbStream.getApp(), gbStream.getStream(), (app, stream, serverId) -> {
                dynamicTask.stop(callIdHeader.getCallId());
                if (serverId.equals(userSetting.getServerId())) {
                    SendRtpItem sendRtpItem = zlmrtpServerFactory.createSendRtpItem(mediaServerItem, addressStr, finalPort, ssrc, requesterId,
                            app, stream, channelId, mediaTransmissionTCP);

                    if (sendRtpItem == null) {
                        logger.warn("??????????????????sendRTPItem?????????????????????????????????????????????");
                        try {
                            responseAck(serverTransaction, Response.BUSY_HERE);
                        } catch (SipException e) {
                            e.printStackTrace();
                        } catch (InvalidArgumentException e) {
                            e.printStackTrace();
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        return;
                    }
                    if (finalTcpActive != null) {
                        sendRtpItem.setTcpActive(finalTcpActive);
                    }
                    sendRtpItem.setPlayType(InviteStreamType.PUSH);
                    // ??????redis??? ???????????????
                    sendRtpItem.setStatus(1);
                    sendRtpItem.setCallId(callIdHeader.getCallId());

                    SIPRequest request = (SIPRequest) evt.getRequest();
                    sendRtpItem.setFromTag(request.getFromTag());
                    SIPResponse response = sendStreamAck(mediaServerItem, serverTransaction, sendRtpItem, platform, evt);
                    if (response != null) {
                        sendRtpItem.setToTag(response.getToTag());
                    }
                    redisCatchStorage.updateSendRTPSever(sendRtpItem);
                } else {
                    // ??????????????????
                    otherWvpPushStream(evt, serverTransaction, gbStream, streamPushItem, platform, callIdHeader, mediaServerItem, port, tcpActive,
                            mediaTransmissionTCP, channelId, addressStr, ssrc, requesterId);
                }
            });

            // ??????????????????????????????????????????
            redisPushStreamResponseListener.addEvent(gbStream.getApp(), gbStream.getStream(), response -> {
                if (response.getCode() != 0) {
                    dynamicTask.stop(callIdHeader.getCallId());
                    mediaListManager.removedChannelOnlineEventLister(gbStream.getApp(), gbStream.getStream());
                    try {
                        responseAck(serverTransaction, Response.TEMPORARILY_UNAVAILABLE, response.getMsg());
                    } catch (SipException | InvalidArgumentException | ParseException e) {
                        logger.error("[??????????????????] ???????????? ????????????: {}", e.getMessage());
                    }
                }
            });
        }
    }

    /**
     * ????????????wvp?????????
     */
    private void otherWvpPushStream(RequestEvent evt, ServerTransaction serverTransaction, GbStream gbStream, StreamPushItem streamPushItem, ParentPlatform platform,
                                    CallIdHeader callIdHeader, MediaServerItem mediaServerItem,
                                    int port, Boolean tcpActive, boolean mediaTransmissionTCP,
                                    String channelId, String addressStr, String ssrc, String requesterId) {
        logger.info("[????????????]????????????????????????????????????redis??????");
        // ??????redis??????
        redisGbPlayMsgListener.sendMsg(streamPushItem.getServerId(), streamPushItem.getMediaServerId(),
                streamPushItem.getApp(), streamPushItem.getStream(), addressStr, port, ssrc, requesterId,
                channelId, mediaTransmissionTCP, null, responseSendItemMsg -> {
                    SendRtpItem sendRtpItem = responseSendItemMsg.getSendRtpItem();
                    if (sendRtpItem == null || responseSendItemMsg.getMediaServerItem() == null) {
                        logger.warn("???????????????????????????");
                        try {
                            responseAck(serverTransaction, Response.BUSY_HERE);
                        } catch (SipException e) {
                            e.printStackTrace();
                        } catch (InvalidArgumentException e) {
                            e.printStackTrace();
                        } catch (ParseException e) {
                            e.printStackTrace();
                        }
                        return;
                    }
                    // ??????sendItem
                    if (tcpActive != null) {
                        sendRtpItem.setTcpActive(tcpActive);
                    }
                    sendRtpItem.setPlayType(InviteStreamType.PUSH);
                    // ??????redis??? ???????????????
                    sendRtpItem.setStatus(1);
                    sendRtpItem.setCallId(callIdHeader.getCallId());

                    SIPRequest request = (SIPRequest) evt.getRequest();
                    sendRtpItem.setFromTag(request.getFromTag());
                    SIPResponse response = sendStreamAck(responseSendItemMsg.getMediaServerItem(), serverTransaction,sendRtpItem, platform, evt);
                    if (response != null) {
                        sendRtpItem.setToTag(response.getToTag());
                    }
                    redisCatchStorage.updateSendRTPSever(sendRtpItem);
                }, (wvpResult) -> {
                    try {
                        // ??????
                        if (wvpResult.getCode() == RedisGbPlayMsgListener.ERROR_CODE_OFFLINE) {
                            // ??????
                            // ??????????????????????????????
                            StreamPushItem currentStreamPushItem = streamPushService.getPush(streamPushItem.getApp(), streamPushItem.getStream());
                            if (currentStreamPushItem.isPushIng()) {
                                // ????????????
                                pushStream(evt, serverTransaction, gbStream, streamPushItem, platform, callIdHeader, mediaServerItem, port, tcpActive,
                                        mediaTransmissionTCP, channelId, addressStr, ssrc, requesterId);

                            } else {
                                // ????????? ??????
                                notifyStreamOnline(evt, serverTransaction, gbStream, streamPushItem, platform, callIdHeader, mediaServerItem, port, tcpActive,
                                        mediaTransmissionTCP, channelId, addressStr, ssrc, requesterId);
                            }
                        }
                    } catch (InvalidArgumentException | ParseException | SipException e) {
                        logger.error("[??????????????????] ???????????? ????????????: {}", e.getMessage());
                    }


                    try {
                        responseAck(serverTransaction, Response.BUSY_HERE);
                    } catch (SipException e) {
                        e.printStackTrace();
                    } catch (InvalidArgumentException e) {
                        e.printStackTrace();
                    } catch (ParseException e) {
                        e.printStackTrace();
                    }
                    return;
                });
    }

    public SIPResponse sendStreamAck(MediaServerItem mediaServerItem, ServerTransaction serverTransaction, SendRtpItem sendRtpItem, ParentPlatform platform, RequestEvent evt) {

        StringBuffer content = new StringBuffer(200);
        content.append("v=0\r\n");
        content.append("o=" + sendRtpItem.getChannelId() + " 0 0 IN IP4 " + mediaServerItem.getSdpIp() + "\r\n");
        content.append("s=Play\r\n");
        content.append("c=IN IP4 " + mediaServerItem.getSdpIp() + "\r\n");
        content.append("t=0 0\r\n");
        content.append("m=video " + sendRtpItem.getLocalPort() + " RTP/AVP 96\r\n");
        content.append("a=sendonly\r\n");
        content.append("a=rtpmap:96 PS/90000\r\n");
        if (sendRtpItem.isTcp()) {
            content.append("a=connection:new\r\n");
            if (!sendRtpItem.isTcpActive()) {
                content.append("a=setup:active\r\n");
            } else {
                content.append("a=setup:passive\r\n");
            }
        }
        content.append("y=" + sendRtpItem.getSsrc() + "\r\n");
        content.append("f=\r\n");

        try {
            return responseSdpAck(serverTransaction, content.toString(), platform);
        } catch (SipException e) {
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }
        return null;
    }

    public void inviteFromDeviceHandle(ServerTransaction serverTransaction, String requesterId) throws InvalidArgumentException, ParseException, SipException, SdpException {

        // ??????????????????????????????????????????????????????????????????????????????????????????
        Device device = redisCatchStorage.getDevice(requesterId);
        if (device != null) {
            logger.info("????????????" + requesterId + "???????????????Invite??????");
            responseAck(serverTransaction, Response.TRYING);

            String contentString = new String(serverTransaction.getRequest().getRawContent());
            // jainSip?????????y=????????? ????????????????????????
            String substring = contentString;
            String ssrc = "0000000404";
            int ssrcIndex = contentString.indexOf("y=");
            if (ssrcIndex > 0) {
                substring = contentString.substring(0, ssrcIndex);
                ssrc = contentString.substring(ssrcIndex + 2, ssrcIndex + 12);
            }
            ssrcIndex = substring.indexOf("f=");
            if (ssrcIndex > 0) {
                substring = contentString.substring(0, ssrcIndex);
            }
            SessionDescription sdp = SdpFactory.getInstance().createSessionDescription(substring);

            //  ?????????????????????
            Vector mediaDescriptions = sdp.getMediaDescriptions(true);
            // ??????????????????PS ??????96
            int port = -1;
            //boolean recvonly = false;
            boolean mediaTransmissionTCP = false;
            Boolean tcpActive = null;
            for (int i = 0; i < mediaDescriptions.size(); i++) {
                MediaDescription mediaDescription = (MediaDescription) mediaDescriptions.get(i);
                Media media = mediaDescription.getMedia();

                Vector mediaFormats = media.getMediaFormats(false);
                if (mediaFormats.contains("8")) {
                    port = media.getMediaPort();
                    String protocol = media.getProtocol();
                    // ??????TCP????????????udp??? ????????????udp
                    if ("TCP/RTP/AVP".equals(protocol)) {
                        String setup = mediaDescription.getAttribute("setup");
                        if (setup != null) {
                            mediaTransmissionTCP = true;
                            if ("active".equals(setup)) {
                                tcpActive = true;
                            } else if ("passive".equals(setup)) {
                                tcpActive = false;
                            }
                        }
                    }
                    break;
                }
            }
            if (port == -1) {
                logger.info("?????????????????????????????????415");
                // ????????????????????????
                responseAck(serverTransaction, Response.UNSUPPORTED_MEDIA_TYPE); // ????????????????????????415
                return;
            }
            String username = sdp.getOrigin().getUsername();
            String addressStr = sdp.getOrigin().getAddress();
            logger.info("??????{}???????????????????????????{}:{}???ssrc???{}", username, addressStr, port, ssrc);

        } else {
            logger.warn("??????????????????/???????????????");
            responseAck(serverTransaction, Response.BAD_REQUEST);
        }
    }
}
