package com.genersoft.iot.wvmp.service.bean;

import com.genersoft.iot.wvmp.gb28181.bean.SendRtpItem;
import com.genersoft.iot.wvmp.zlm.dto.MediaServerItem;

/**
 * redis消息：下级回复推送信息
 * @author lin
 */
public class ResponseSendItemMsg {

    private SendRtpItem sendRtpItem;

    private MediaServerItem mediaServerItem;

    public SendRtpItem getSendRtpItem() {
        return sendRtpItem;
    }

    public void setSendRtpItem(SendRtpItem sendRtpItem) {
        this.sendRtpItem = sendRtpItem;
    }

    public MediaServerItem getMediaServerItem() {
        return mediaServerItem;
    }

    public void setMediaServerItem(MediaServerItem mediaServerItem) {
        this.mediaServerItem = mediaServerItem;
    }
}
