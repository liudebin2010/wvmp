package com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.control.cmd;

import com.genersoft.iot.wvmp.VManageBootstrap;
import com.genersoft.iot.wvmp.gb28181.bean.Device;
import com.genersoft.iot.wvmp.gb28181.bean.ParentPlatform;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.impl.SipCommander;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.impl.SipCommanderFroPlatform;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.SipRequestProcessorParent;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.control.ControlMessageHandler;
import com.genersoft.iot.wvmp.storager.IVideoManagerStorage;
import com.genersoft.iot.wvmp.utils.SpringBeanFactory;
import gov.nist.javax.sip.SipStackImpl;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.sip.*;
import javax.sip.address.SipURI;
import javax.sip.header.HeaderAddress;
import javax.sip.header.ToHeader;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.Iterator;

import static com.genersoft.iot.wvmp.gb28181.utils.XmlUtil.getText;

@Component
public class DeviceControlQueryMessageHandler extends SipRequestProcessorParent implements InitializingBean, IMessageHandler {

    private Logger logger = LoggerFactory.getLogger(DeviceControlQueryMessageHandler.class);
    private final String cmdType = "DeviceControl";

    @Autowired
    private ControlMessageHandler controlMessageHandler;

    @Autowired
    private IVideoManagerStorage storager;

    @Autowired
    private SipCommander cmder;

    @Autowired
    private SipCommanderFroPlatform cmderFroPlatform;

    @Qualifier("taskExecutor")
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Override
    public void afterPropertiesSet() throws Exception {
        controlMessageHandler.addHandler(cmdType, this);
    }

    @Override
    public void handForDevice(RequestEvent evt, Device device, Element element) {

    }

    @Override
    public void handForPlatform(RequestEvent evt, ParentPlatform parentPlatform, Element rootElement) {

        ServerTransaction serverTransaction = getServerTransaction(evt);

        // ????????????????????????DeviceControl??????
        String targetGBId = ((SipURI) ((HeaderAddress) evt.getRequest().getHeader(ToHeader.NAME)).getAddress().getURI()).getUser();
        String channelId = getText(rootElement, "DeviceID");
        // ??????????????????
        if (!ObjectUtils.isEmpty(getText(rootElement, "TeleBoot"))) {
            if (parentPlatform.getServerGBId().equals(targetGBId)) {
                // ????????????????????????????????????????????????????????????SipStack??????
                logger.info("?????????????????????????????????");
                try {
                    cmderFroPlatform.unregister(parentPlatform, null, null);
                } catch (InvalidArgumentException | ParseException | SipException e) {
                    logger.error("[??????????????????] ???????????? ??????: {}", e.getMessage());
                }
                taskExecutor.execute(()->{
                    try {
                        Thread.sleep(3000);
                        SipProvider up = (SipProvider) SpringBeanFactory.getBean("udpSipProvider");
                        SipStackImpl stack = (SipStackImpl)up.getSipStack();
                        stack.stop();
                        Iterator listener = stack.getListeningPoints();
                        while (listener.hasNext()) {
                            stack.deleteListeningPoint((ListeningPoint) listener.next());
                        }
                        Iterator providers = stack.getSipProviders();
                        while (providers.hasNext()) {
                            stack.deleteSipProvider((SipProvider) providers.next());
                        }
                        VManageBootstrap.restart();
                    } catch (InterruptedException | ObjectInUseException e) {
                        logger.error("[??????????????????] ????????????: {}", e.getMessage());
                    }
                });
            } else {
                // ????????????????????????
            }
        }
        // ??????/??????????????????
        if (!ObjectUtils.isEmpty(getText(rootElement,"PTZCmd")) && !parentPlatform.getServerGBId().equals(targetGBId)) {
            String cmdString = getText(rootElement,"PTZCmd");
            Device deviceForPlatform = storager.queryVideoDeviceByPlatformIdAndChannelId(parentPlatform.getServerGBId(), channelId);
            if (deviceForPlatform == null) {
                try {
                    responseAck(serverTransaction, Response.NOT_FOUND);
                    return;
                } catch (SipException | InvalidArgumentException | ParseException e) {
                    logger.error("[??????????????????] ????????????: {}", e.getMessage());
                }
            }
            try {
                cmder.fronEndCmd(deviceForPlatform, channelId, cmdString, eventResult -> {
                    // ???????????????
                    try {
                        responseAck(serverTransaction, eventResult.statusCode, eventResult.msg);
                    } catch (SipException | InvalidArgumentException | ParseException e) {
                        logger.error("[??????????????????] ??????/????????????: {}", e.getMessage());
                    }
                }, eventResult -> {
                    // ???????????????
                    try {
                        responseAck(serverTransaction, eventResult.statusCode);
                    } catch (SipException | InvalidArgumentException | ParseException e) {
                        logger.error("[??????????????????] ??????/????????????: {}", e.getMessage());
                    }
                });
            } catch (InvalidArgumentException | SipException | ParseException e) {
                logger.error("[??????????????????] ??????/??????: {}", e.getMessage());
            }
        }
    }
}
