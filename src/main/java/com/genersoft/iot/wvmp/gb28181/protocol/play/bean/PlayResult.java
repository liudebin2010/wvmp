package com.genersoft.iot.wvmp.gb28181.protocol.play.bean;

import com.genersoft.iot.wvmp.gb28181.bean.Device;
import com.genersoft.iot.wvmp.vmanager.bean.WvmpResult;
import org.springframework.web.context.request.async.DeferredResult;

public class PlayResult {

    private DeferredResult<WvmpResult<String>> result;
    private String uuid;

    private Device device;

    public DeferredResult<WvmpResult<String>> getResult() {
        return result;
    }

    public void setResult(DeferredResult<WvmpResult<String>> result) {
        this.result = result;
    }

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public Device getDevice() {
        return device;
    }

    public void setDevice(Device device) {
        this.device = device;
    }
}
