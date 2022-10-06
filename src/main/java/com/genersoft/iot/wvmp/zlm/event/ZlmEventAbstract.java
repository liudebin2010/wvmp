package com.genersoft.iot.wvmp.zlm.event;

import org.springframework.context.ApplicationEvent;

public abstract class ZlmEventAbstract extends ApplicationEvent {

    private static final long serialVersionUID = 1L;

    private String mediaServerId;

    public ZlmEventAbstract(Object source) {
        super(source);
    }

    public String getMediaServerId() {
        return mediaServerId;
    }

    public void setMediaServerId(String mediaServerId) {
        this.mediaServerId = mediaServerId;
    }

}
