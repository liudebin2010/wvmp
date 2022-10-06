package com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.response.cmd;

import com.alibaba.fastjson.JSONObject;
import com.genersoft.iot.wvmp.conf.UserSetting;
import com.genersoft.iot.wvmp.gb28181.bean.*;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.SipRequestProcessorParent;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.response.ResponseMessageHandler;
import com.genersoft.iot.wvmp.gb28181.utils.NumericUtil;
import com.genersoft.iot.wvmp.service.IDeviceChannelService;
import com.genersoft.iot.wvmp.storager.IRedisCatchStorage;
import com.genersoft.iot.wvmp.storager.IVideoManagerStorage;
import com.genersoft.iot.wvmp.utils.DateUtil;
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
 * 移动设备位置数据查询回复
 * @author lin
 */
@Component
public class MobilePositionResponseMessageHandler extends SipRequestProcessorParent implements InitializingBean, IMessageHandler {

    private Logger logger = LoggerFactory.getLogger(MobilePositionResponseMessageHandler.class);
    private final String cmdType = "MobilePosition";

    @Autowired
    private ResponseMessageHandler responseMessageHandler;

    @Autowired
    private UserSetting userSetting;

    @Autowired
    private IVideoManagerStorage storager;

    @Autowired
    private IRedisCatchStorage redisCatchStorage;

    @Autowired
    private IDeviceChannelService deviceChannelService;

    @Override
    public void afterPropertiesSet() throws Exception {
        responseMessageHandler.addHandler(cmdType, this);
    }

    @Override
    public void handForDevice(RequestEvent evt, Device device, Element rootElement) {

        ServerTransaction serverTransaction = getServerTransaction(evt);

        try {
            rootElement = getRootElement(evt, device.getCharset());
            if (rootElement == null) {
                logger.warn("[ 移动设备位置数据查询回复 ] content cannot be null, {}", evt.getRequest());
                responseAck(serverTransaction, Response.BAD_REQUEST);
                return;
            }
            MobilePosition mobilePosition = new MobilePosition();
            mobilePosition.setCreateTime(DateUtil.getNow());
            if (!ObjectUtils.isEmpty(device.getName())) {
                mobilePosition.setDeviceName(device.getName());
            }
            mobilePosition.setDeviceId(device.getDeviceId());
            mobilePosition.setChannelId(getText(rootElement, "DeviceID"));
            mobilePosition.setTime(getText(rootElement, "Time"));
            mobilePosition.setLongitude(Double.parseDouble(getText(rootElement, "Longitude")));
            mobilePosition.setLatitude(Double.parseDouble(getText(rootElement, "Latitude")));
            if (NumericUtil.isDouble(getText(rootElement, "Speed"))) {
                mobilePosition.setSpeed(Double.parseDouble(getText(rootElement, "Speed")));
            } else {
                mobilePosition.setSpeed(0.0);
            }
            if (NumericUtil.isDouble(getText(rootElement, "Direction"))) {
                mobilePosition.setDirection(Double.parseDouble(getText(rootElement, "Direction")));
            } else {
                mobilePosition.setDirection(0.0);
            }
            if (NumericUtil.isDouble(getText(rootElement, "Altitude"))) {
                mobilePosition.setAltitude(Double.parseDouble(getText(rootElement, "Altitude")));
            } else {
                mobilePosition.setAltitude(0.0);
            }
            mobilePosition.setReportSource("Mobile Position");

            // 更新device channel 的经纬度
            DeviceChannel deviceChannel = new DeviceChannel();
            deviceChannel.setDeviceId(device.getDeviceId());
            deviceChannel.setChannelId(mobilePosition.getChannelId());
            deviceChannel.setLongitude(mobilePosition.getLongitude());
            deviceChannel.setLatitude(mobilePosition.getLatitude());
            deviceChannel.setGpsTime(mobilePosition.getTime());

            deviceChannel = deviceChannelService.updateGps(deviceChannel, device);

            mobilePosition.setLongitudeWgs84(deviceChannel.getLongitudeWgs84());
            mobilePosition.setLatitudeWgs84(deviceChannel.getLatitudeWgs84());
            mobilePosition.setLongitudeGcj02(deviceChannel.getLongitudeGcj02());
            mobilePosition.setLatitudeGcj02(deviceChannel.getLatitudeGcj02());

            if (userSetting.getSavePositionHistory()) {
                storager.insertMobilePosition(mobilePosition);
            }
            storager.updateChannelPosition(deviceChannel);

            // 发送redis消息。 通知位置信息的变化
            JSONObject jsonObject = new JSONObject();
            jsonObject.put("time", mobilePosition.getTime());
            jsonObject.put("serial", deviceChannel.getDeviceId());
            jsonObject.put("code", deviceChannel.getChannelId());
            jsonObject.put("longitude", mobilePosition.getLongitude());
            jsonObject.put("latitude", mobilePosition.getLatitude());
            jsonObject.put("altitude", mobilePosition.getAltitude());
            jsonObject.put("direction", mobilePosition.getDirection());
            jsonObject.put("speed", mobilePosition.getSpeed());
            redisCatchStorage.sendMobilePositionMsg(jsonObject);
            //回复 200 OK
            responseAck(serverTransaction, Response.OK);
        } catch (DocumentException | SipException | InvalidArgumentException | ParseException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void handForPlatform(RequestEvent evt, ParentPlatform parentPlatform, Element element) {

    }
}
