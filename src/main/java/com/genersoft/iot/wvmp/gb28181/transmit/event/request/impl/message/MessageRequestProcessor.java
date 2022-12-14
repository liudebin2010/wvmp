package com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message;

import com.genersoft.iot.wvmp.gb28181.bean.Device;
import com.genersoft.iot.wvmp.gb28181.bean.DeviceNotFoundEvent;
import com.genersoft.iot.wvmp.gb28181.bean.ParentPlatform;
import com.genersoft.iot.wvmp.gb28181.bean.SsrcTransaction;
import com.genersoft.iot.wvmp.gb28181.event.SipSubscribe;
import com.genersoft.iot.wvmp.gb28181.session.VideoStreamSessionManager;
import com.genersoft.iot.wvmp.gb28181.transmit.SipProcessorObserver;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.ISipRequestProcessor;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.SipRequestProcessorParent;
import com.genersoft.iot.wvmp.gb28181.utils.SipUtils;
import com.genersoft.iot.wvmp.storager.IRedisCatchStorage;
import com.genersoft.iot.wvmp.storager.IVideoManagerStorage;
import gov.nist.javax.sip.message.SIPRequest;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.header.CallIdHeader;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MessageRequestProcessor extends SipRequestProcessorParent implements InitializingBean, ISipRequestProcessor {

    private final static Logger logger = LoggerFactory.getLogger(MessageRequestProcessor.class);

    private final String method = "MESSAGE";

    private static Map<String, IMessageHandler> messageHandlerMap = new ConcurrentHashMap<>();

    @Autowired
    private SipProcessorObserver sipProcessorObserver;

    @Autowired
    private IVideoManagerStorage storage;

    @Autowired
    private SipSubscribe sipSubscribe;

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Autowired
    private VideoStreamSessionManager sessionManager;

    @Override
    public void afterPropertiesSet() throws Exception {
        // ???????????????????????????
        sipProcessorObserver.addRequestProcessor(method, this);
    }

    public void addHandler(String name, IMessageHandler handler) {
        messageHandlerMap.put(name, handler);
    }

    @Override
    public void process(RequestEvent evt) {
        SIPRequest sipRequest = (SIPRequest)evt.getRequest();
        logger.debug("??????????????????" + evt.getRequest());
        String deviceId = SipUtils.getUserIdFromFromHeader(evt.getRequest());
        CallIdHeader callIdHeader = sipRequest.getCallIdHeader();
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
                Element rootElement = null;
                try {
                    rootElement = getRootElement(evt);
                    if (rootElement == null) {
                        logger.error("??????MESSAGE??????  ?????????????????????{}", evt.getRequest());
                        responseAck(serverTransaction, Response.BAD_REQUEST, "content is null");
                        return;
                    }
                } catch (DocumentException e) {
                    logger.warn("??????XML??????????????????", e);
                    // ??????????????????404
                    responseAck(serverTransaction, Response.BAD_REQUEST, e.getMessage());
                }
                String name = rootElement.getName();
                IMessageHandler messageHandler = messageHandlerMap.get(name);
                if (messageHandler != null) {
                    if (device != null) {
                        messageHandler.handForDevice(evt, device, rootElement);
                    }else { // ??????????????????????????????null??????????????????????????????device???parentPlatform??????????????????null
                        messageHandler.handForPlatform(evt, parentPlatform, rootElement);
                    }
                }else {
                    // ????????????message
                    // ??????????????????415
                    responseAck(serverTransaction, Response.UNSUPPORTED_MEDIA_TYPE, "Unsupported message type, must Control/Notify/Query/Response");
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
