package com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.info;

import com.genersoft.iot.wvmp.common.StreamInfo;
import com.genersoft.iot.wvmp.gb28181.bean.*;
import com.genersoft.iot.wvmp.gb28181.event.SipSubscribe;
import com.genersoft.iot.wvmp.gb28181.session.VideoStreamSessionManager;
import com.genersoft.iot.wvmp.gb28181.transmit.SipProcessorObserver;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.impl.SipCommander;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.ISipRequestProcessor;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.SipRequestProcessorParent;
import com.genersoft.iot.wvmp.gb28181.utils.SipUtils;
import com.genersoft.iot.wvmp.storager.IRedisCatchStorage;
import com.genersoft.iot.wvmp.storager.IVideoManagerStorage;
import gov.nist.javax.sip.message.SIPRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.header.*;
import javax.sip.message.Response;
import java.text.ParseException;

@Component
public class InfoRequestProcessor extends SipRequestProcessorParent implements InitializingBean, ISipRequestProcessor {

    private final static Logger logger = LoggerFactory.getLogger(InfoRequestProcessor.class);

    private final String method = "INFO";

    @Autowired
    private SipProcessorObserver sipProcessorObserver;

    @Autowired
    private IVideoManagerStorage storage;

    @Autowired
    private SipSubscribe sipSubscribe;

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Autowired
    private IVideoManagerStorage storager;

    @Autowired
    private SipCommander cmder;

    @Autowired
    private VideoStreamSessionManager sessionManager;

    @Override
    public void afterPropertiesSet() throws Exception {
        // ???????????????????????????
        sipProcessorObserver.addRequestProcessor(method, this);
    }

    @Override
    public void process(RequestEvent evt) {
        logger.debug("??????????????????" + evt.getRequest());
        String deviceId = SipUtils.getUserIdFromFromHeader(evt.getRequest());
        CallIdHeader callIdHeader = (CallIdHeader)evt.getRequest().getHeader(CallIdHeader.NAME);
        // ?????????????????????
        SsrcTransaction ssrcTransaction = sessionManager.getSsrcTransaction(null, null, callIdHeader.getCallId(), null);

        // ???????????? ???????????? ??????from??????????????????ID?????????
        if (ssrcTransaction != null) {
            deviceId = ssrcTransaction.getDeviceId();
        }
        ServerTransaction serverTransaction = getServerTransaction(evt);
        // ????????????????????????
        Device device = redisCatchStorage.getDevice(deviceId);
        // ??????????????????????????????
        ParentPlatform parentPlatform = storage.queryParentPlatByServerGBId(deviceId);
        try {
            if (device != null && parentPlatform != null) {
                logger.warn("[??????]??????????????????????????????{}", deviceId);
                SIPRequest request = (SIPRequest) evt.getRequest();
                String hostAddress = request.getRemoteAddress().getHostAddress();
                int remotePort = request.getRemotePort();
                if (device.getHostAddress().equals(hostAddress + ":" + remotePort)) {
                    parentPlatform = null;
                }else {
                    device = null;
                }
            }
            if (device == null && parentPlatform == null) {
                // ??????????????????404
                responseAck(serverTransaction, Response.NOT_FOUND, "device "+ deviceId +" not found");
                logger.warn("[??????????????? ]??? {}", deviceId);
                if (sipSubscribe.getErrorSubscribe(callIdHeader.getCallId()) != null){
                    DeviceNotFoundEvent deviceNotFoundEvent = new DeviceNotFoundEvent(evt.getDialog());
                    deviceNotFoundEvent.setCallId(callIdHeader.getCallId());
                    SipSubscribe.EventResult eventResult = new SipSubscribe.EventResult(deviceNotFoundEvent);
                    sipSubscribe.getErrorSubscribe(callIdHeader.getCallId()).response(eventResult);
                };
            }else {
                ContentTypeHeader header = (ContentTypeHeader)evt.getRequest().getHeader(ContentTypeHeader.NAME);
                String contentType = header.getContentType();
                String contentSubType = header.getContentSubType();
                if ("Application".equalsIgnoreCase(contentType) && "MANSRTSP".equalsIgnoreCase(contentSubType)) {
                    SendRtpItem sendRtpItem = redisCatchStorage.querySendRTPServer(null, null, null, callIdHeader.getCallId());
                    String streamId = sendRtpItem.getStreamId();
                    StreamInfo streamInfo = redisCatchStorage.queryPlayback(null, null, streamId, null);
                    if (null == streamInfo) {
                        responseAck(serverTransaction, Response.NOT_FOUND, "stream " + streamId + " not found");
                        return;
                    }
                    Device device1 = storager.queryVideoDevice(streamInfo.getDeviceID());
                    cmder.playbackControlCmd(device1,streamInfo,new String(evt.getRequest().getRawContent()),eventResult -> {
                        // ???????????????
                        try {
                            responseAck(serverTransaction, eventResult.statusCode, eventResult.msg);
                        } catch (SipException | InvalidArgumentException | ParseException e) {
                            logger.error("[??????????????????] ???????????? ????????????: {}", e.getMessage());
                        }
                    }, eventResult -> {
                        // ???????????????
                        try {
                            responseAck(serverTransaction, eventResult.statusCode);
                        } catch (SipException | InvalidArgumentException | ParseException e) {
                            logger.error("[??????????????????] ???????????? ????????????: {}", e.getMessage());
                        }
                    });
                }
            }
        } catch (SipException e) {
            logger.warn("SIP ????????????", e);
        } catch (InvalidArgumentException e) {
            logger.warn("????????????", e);
        } catch (ParseException e) {
            logger.warn("SIP?????????????????????", e);
        }
    }


}
