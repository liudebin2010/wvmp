package com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.query.cmd;

import com.genersoft.iot.wvmp.conf.SipConfig;
import com.genersoft.iot.wvmp.gb28181.bean.*;
import com.genersoft.iot.wvmp.gb28181.event.EventPublisher;
import com.genersoft.iot.wvmp.gb28181.event.record.RecordEndEventListener;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.impl.SipCommander;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.impl.SipCommanderFroPlatform;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.SipRequestProcessorParent;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.query.QueryMessageHandler;
import com.genersoft.iot.wvmp.utils.DateUtil;
import com.genersoft.iot.wvmp.storager.IVideoManagerStorage;
import com.genersoft.iot.wvmp.storager.dao.dto.ChannelSourceInfo;
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
import javax.sip.header.FromHeader;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.List;

@Component
public class RecordInfoQueryMessageHandler extends SipRequestProcessorParent implements InitializingBean, IMessageHandler {

    private Logger logger = LoggerFactory.getLogger(RecordInfoQueryMessageHandler.class);
    private final String cmdType = "RecordInfo";

    @Autowired
    private QueryMessageHandler queryMessageHandler;

    @Autowired
    private IVideoManagerStorage storager;

    @Autowired
    private SipCommanderFroPlatform cmderFroPlatform;

    @Autowired
    private SipCommander commander;

    @Autowired
    private RecordEndEventListener recordEndEventListener;

    @Autowired
    private SipConfig config;

    @Autowired
    private EventPublisher publisher;

    @Override
    public void afterPropertiesSet() throws Exception {
        queryMessageHandler.addHandler(cmdType, this);
    }

    @Override
    public void handForDevice(RequestEvent evt, Device device, Element element) {

    }

    @Override
    public void handForPlatform(RequestEvent evt, ParentPlatform parentPlatform, Element rootElement) {

        FromHeader fromHeader = (FromHeader) evt.getRequest().getHeader(FromHeader.NAME);
        ServerTransaction serverTransaction = getServerTransaction(evt);
        Element snElement = rootElement.element("SN");
        int sn = Integer.parseInt(snElement.getText());
        Element deviceIDElement = rootElement.element("DeviceID");
        String channelId = deviceIDElement.getText();
        Element startTimeElement = rootElement.element("StartTime");
        String startTime = null;
        if (startTimeElement != null) {
            startTime = startTimeElement.getText();
        }
        Element endTimeElement = rootElement.element("EndTime");
        String endTime = null;
        if (endTimeElement != null) {
            endTime = endTimeElement.getText();
        }
        Element secrecyElement = rootElement.element("Secrecy");
        int secrecy = 0;
        if (secrecyElement != null) {
            secrecy = Integer.parseInt(secrecyElement.getText().trim());
        }
        String type = "all";
        Element typeElement = rootElement.element("Type");
        if (typeElement != null) {
            type =  typeElement.getText();
        }
        // ?????????????????????????????? ?????????????????????????????????????????????????????????
        List<ChannelSourceInfo> channelSources = storager.getChannelSource(parentPlatform.getServerGBId(), channelId);

        if (channelSources.get(0).getCount() > 0) { // ??????
            // ?????????????????????????????????
            Device device = storager.queryVideoDeviceByPlatformIdAndChannelId(parentPlatform.getServerGBId(), channelId);
            DeviceChannel deviceChannel = storager.queryChannelInParentPlatform(parentPlatform.getServerGBId(), channelId);
            // ??????????????????
            recordEndEventListener.addEndEventHandler(deviceChannel.getDeviceId(), channelId, (recordInfo)->{
                try {
                    cmderFroPlatform.recordInfo(deviceChannel, parentPlatform, fromHeader.getTag(), recordInfo);
                } catch (SipException | InvalidArgumentException | ParseException e) {
                    logger.error("[??????????????????] ???????????? ??????????????????: {}", e.getMessage());
                }
            });
            try {
                commander.recordInfoQuery(device, channelId, DateUtil.ISO8601Toyyyy_MM_dd_HH_mm_ss(startTime),
                        DateUtil.ISO8601Toyyyy_MM_dd_HH_mm_ss(endTime), sn, secrecy, type, (eventResult -> {
                            // ??????200 OK
                            try {
                                responseAck(serverTransaction, Response.OK);
                            } catch (SipException | InvalidArgumentException | ParseException e) {
                                logger.error("[??????????????????] ??????????????????: {}", e.getMessage());
                            }
                        }),(eventResult -> {
                            // ????????????
                            try {
                                responseAck(serverTransaction, eventResult.statusCode, eventResult.msg);
                            } catch (SipException | InvalidArgumentException | ParseException e) {
                                logger.error("[??????????????????] ??????????????????: {}", e.getMessage());
                            }
                        }));
            } catch (InvalidArgumentException | ParseException | SipException e) {
                logger.error("[??????????????????] ????????????: {}", e.getMessage());
            }

        }else if (channelSources.get(1).getCount() > 0) { // ?????????
            // TODO
            try {
                responseAck(serverTransaction, Response.NOT_IMPLEMENTED); // ???????????????
            } catch (SipException | InvalidArgumentException | ParseException e) {
                logger.error("[??????????????????] ????????????: {}", e.getMessage());
            }
        }else { // ???????????????
            try {
                responseAck(serverTransaction, Response.BAD_REQUEST);
            } catch (SipException | InvalidArgumentException | ParseException e) {
                logger.error("[??????????????????] ????????????: {}", e.getMessage());
            }
        }
    }
}
