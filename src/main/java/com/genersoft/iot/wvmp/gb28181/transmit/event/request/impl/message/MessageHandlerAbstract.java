package com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message;

import com.genersoft.iot.wvmp.gb28181.bean.Device;
import com.genersoft.iot.wvmp.gb28181.bean.ParentPlatform;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.SipRequestProcessorParent;
import org.dom4j.Element;

import javax.sip.RequestEvent;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.genersoft.iot.wvmp.gb28181.utils.XmlUtil.getText;

public abstract class MessageHandlerAbstract extends SipRequestProcessorParent implements IMessageHandler{

    public Map<String, IMessageHandler> messageHandlerMap = new ConcurrentHashMap<>();

    public void addHandler(String cmdType, IMessageHandler messageHandler) {
        messageHandlerMap.put(cmdType, messageHandler);
    }

    @Override
    public void handForDevice(RequestEvent evt, Device device, Element element) {
        String cmd = getText(element, "CmdType");
        IMessageHandler messageHandler = messageHandlerMap.get(cmd);
        if (messageHandler != null) {
            messageHandler.handForDevice(evt, device, element);
        }
    }

    @Override
    public void handForPlatform(RequestEvent evt, ParentPlatform parentPlatform, Element element) {
        String cmd = getText(element, "CmdType");
        IMessageHandler messageHandler = messageHandlerMap.get(cmd);
        if (messageHandler != null) {
            messageHandler.handForPlatform(evt, parentPlatform, element);
        }
    }
}
