package com.genersoft.iot.wvmp.service.bean;

import com.genersoft.iot.wvmp.gb28181.transmit.callback.RequestMessage;

public interface PlayBackCallback {

    void call(PlayBackResult<RequestMessage> msg);

}
