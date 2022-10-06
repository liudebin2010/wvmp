package com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.query.cmd;

import com.genersoft.iot.wvmp.gb28181.bean.Device;
import com.genersoft.iot.wvmp.gb28181.bean.ParentPlatform;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.impl.SipCommanderFroPlatform;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.SipRequestProcessorParent;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.query.QueryMessageHandler;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.SipException;
import javax.sip.header.FromHeader;
import javax.sip.message.Response;
import java.text.ParseException;

@Component
public class DeviceInfoQueryMessageHandler extends SipRequestProcessorParent implements InitializingBean, IMessageHandler {

    private Logger logger = LoggerFactory.getLogger(DeviceInfoQueryMessageHandler.class);
    private final String cmdType = "DeviceInfo";

    @Autowired
    private QueryMessageHandler queryMessageHandler;

    @Autowired
    private SipCommanderFroPlatform cmderFroPlatform;

    @Override
    public void afterPropertiesSet() throws Exception {
        queryMessageHandler.addHandler(cmdType, this);
    }

    @Override
    public void handForDevice(RequestEvent evt, Device device, Element rootElement) {

    }

    @Override
    public void handForPlatform(RequestEvent evt, ParentPlatform parentPlatform, Element rootElement) {
        logger.info("[DeviceInfo查询]消息");
        FromHeader fromHeader = (FromHeader) evt.getRequest().getHeader(FromHeader.NAME);
        try {
            // 回复200 OK
            responseAck(getServerTransaction(evt), Response.OK);
        } catch (SipException | InvalidArgumentException | ParseException e) {
            logger.error("[命令发送失败] DeviceInfo查询回复: {}", e.getMessage());
        }
        String sn = rootElement.element("SN").getText();
        try {
            cmderFroPlatform.deviceInfoResponse(parentPlatform, sn, fromHeader.getTag());
        } catch (SipException | InvalidArgumentException | ParseException e) {
            logger.error("[命令发送失败] 国标级联 DeviceInfo查询回复: {}", e.getMessage());
        }
    }
}
