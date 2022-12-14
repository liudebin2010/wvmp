package com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.query.cmd;

import com.genersoft.iot.wvmp.conf.SipConfig;
import com.genersoft.iot.wvmp.gb28181.bean.*;
import com.genersoft.iot.wvmp.gb28181.event.EventPublisher;
import com.genersoft.iot.wvmp.gb28181.transmit.callback.DeferredResultHolder;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.impl.SipCommanderFroPlatform;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.SipRequestProcessorParent;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.query.QueryMessageHandler;
import com.genersoft.iot.wvmp.storager.IVideoManagerStorage;
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
import java.util.ArrayList;
import java.util.List;

@Component
public class CatalogQueryMessageHandler extends SipRequestProcessorParent implements InitializingBean, IMessageHandler {

    private Logger logger = LoggerFactory.getLogger(CatalogQueryMessageHandler.class);
    private final String cmdType = "Catalog";

    @Autowired
    private QueryMessageHandler queryMessageHandler;

    @Autowired
    private IVideoManagerStorage storager;

    @Autowired
    private SipCommanderFroPlatform cmderFroPlatform;

    @Autowired
    private SipConfig config;

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private IVideoManagerStorage storage;

    @Override
    public void afterPropertiesSet() throws Exception {
        queryMessageHandler.addHandler(cmdType, this);
    }

    @Override
    public void handForDevice(RequestEvent evt, Device device, Element element) {

    }

    @Override
    public void handForPlatform(RequestEvent evt, ParentPlatform parentPlatform, Element rootElement) {

        String key = DeferredResultHolder.CALLBACK_CMD_CATALOG + parentPlatform.getServerGBId();
        FromHeader fromHeader = (FromHeader) evt.getRequest().getHeader(FromHeader.NAME);
        try {
            // ??????200 OK
            responseAck(getServerTransaction(evt), Response.OK);
            Element snElement = rootElement.element("SN");
            String sn = snElement.getText();
            // ????????????????????????
            List<DeviceChannel> deviceChannelInPlatforms = storager.queryChannelWithCatalog(parentPlatform.getServerGBId());
            // ???????????????????????????
            List<DeviceChannel> gbStreams = storager.queryGbStreamListInPlatform(parentPlatform.getServerGBId());
            // ??????????????????
            List<DeviceChannel> catalogs =  storager.queryCatalogInPlatform(parentPlatform.getServerGBId());

            List<DeviceChannel> allChannels = new ArrayList<>();

            // ????????????
//            DeviceChannel deviceChannel = getChannelForPlatform(parentPlatform);
//            allChannels.add(deviceChannel);

            // ????????????
            if (catalogs.size() > 0) {
                allChannels.addAll(catalogs);
            }
            // ?????????????????????
            if (deviceChannelInPlatforms.size() > 0) {
                allChannels.addAll(deviceChannelInPlatforms);
            }
            // ?????????????????????
            if (gbStreams.size() > 0) {
                allChannels.addAll(gbStreams);
            }
            if (allChannels.size() > 0) {
                cmderFroPlatform.catalogQuery(allChannels, parentPlatform, sn, fromHeader.getTag());
            }else {
                // ???????????????
                cmderFroPlatform.catalogQuery(null, parentPlatform, sn, fromHeader.getTag(), 0);
            }
        } catch (SipException | InvalidArgumentException | ParseException e) {
            logger.error("[??????????????????] ???????????? ????????????: {}", e.getMessage());
        }

    }

    private DeviceChannel getChannelForPlatform(ParentPlatform platform) {
        DeviceChannel deviceChannel = new DeviceChannel();

        deviceChannel.setChannelId(platform.getDeviceGBId());
        deviceChannel.setName(platform.getName());
        deviceChannel.setManufacture("wvp-pro");
        deviceChannel.setOwner("wvp-pro");
        deviceChannel.setCivilCode(platform.getAdministrativeDivision());
        deviceChannel.setAddress("wvp-pro");
        deviceChannel.setRegisterWay(0);
        deviceChannel.setSecrecy("0");

        return deviceChannel;
    }
}
