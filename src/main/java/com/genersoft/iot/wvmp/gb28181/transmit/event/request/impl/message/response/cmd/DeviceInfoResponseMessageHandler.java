package com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.response.cmd;

import com.genersoft.iot.wvmp.conf.SipConfig;
import com.genersoft.iot.wvmp.gb28181.bean.Device;
import com.genersoft.iot.wvmp.gb28181.bean.ParentPlatform;
import com.genersoft.iot.wvmp.gb28181.event.EventPublisher;
import com.genersoft.iot.wvmp.gb28181.transmit.callback.DeferredResultHolder;
import com.genersoft.iot.wvmp.gb28181.transmit.callback.RequestMessage;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.SipRequestProcessorParent;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.response.ResponseMessageHandler;
import com.genersoft.iot.wvmp.service.IDeviceService;
import com.genersoft.iot.wvmp.storager.IRedisCatchStorage;
import com.genersoft.iot.wvmp.storager.IVideoManagerStorage;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.ServerTransaction;
import javax.sip.SipException;
import javax.sip.message.Response;
import java.text.ParseException;

import static com.genersoft.iot.wvmp.gb28181.utils.XmlUtil.getText;

/**
 * @author lin
 */
@Component
public class DeviceInfoResponseMessageHandler extends SipRequestProcessorParent implements InitializingBean, IMessageHandler {

    private Logger logger = LoggerFactory.getLogger(DeviceInfoResponseMessageHandler.class);
    private final String cmdType = "DeviceInfo";

    @Autowired
    private ResponseMessageHandler responseMessageHandler;

    @Autowired
    private IVideoManagerStorage storager;

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Autowired
    private DeferredResultHolder deferredResultHolder;

    @Autowired
    private SipConfig config;

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private IDeviceService deviceService;

    @Override
    public void afterPropertiesSet() throws Exception {
        responseMessageHandler.addHandler(cmdType, this);
    }

    @Override
    public void handForDevice(RequestEvent evt, Device device, Element rootElement) {
        logger.debug("?????????DeviceInfo????????????");
        // ??????????????????????????? ?????????????????????
        if (device == null || device.getOnline() == 0) {
            logger.warn("[?????????DeviceInfo????????????,????????????????????????]???" + (device != null ? device.getDeviceId():"" ));
            return;
        }
        ServerTransaction serverTransaction = getServerTransaction(evt);
        try {
            rootElement = getRootElement(evt, device.getCharset());
            if (rootElement == null) {
                logger.warn("[ ?????????DeviceInfo???????????? ] content cannot be null, {}", evt.getRequest());
                responseAck(serverTransaction, Response.BAD_REQUEST);
                return;
            }
            Element deviceIdElement = rootElement.element("DeviceID");
            String channelId = deviceIdElement.getTextTrim();
            String key = DeferredResultHolder.CALLBACK_CMD_DEVICEINFO + device.getDeviceId() + channelId;
            device.setName(getText(rootElement, "DeviceName"));

            device.setManufacturer(getText(rootElement, "Manufacturer"));
            device.setModel(getText(rootElement, "Model"));
            device.setFirmware(getText(rootElement, "Firmware"));
            if (ObjectUtils.isEmpty(device.getStreamMode())) {
                device.setStreamMode("UDP");
            }
            deviceService.updateDevice(device);

            RequestMessage msg = new RequestMessage();
            msg.setKey(key);
            msg.setData(device);
            deferredResultHolder.invokeAllResult(msg);
            // ??????200 OK
            responseAck(serverTransaction, Response.OK);
        } catch (DocumentException e) {
            e.printStackTrace();
        } catch (InvalidArgumentException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        } catch (SipException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handForPlatform(RequestEvent evt, ParentPlatform parentPlatform, Element rootElement) {

    }
}
