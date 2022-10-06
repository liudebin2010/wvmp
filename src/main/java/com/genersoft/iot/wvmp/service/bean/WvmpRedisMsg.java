package com.genersoft.iot.wvmp.service.bean;

/**
 * @author lin
 */
public class WvmpRedisMsg {

    public static WvmpRedisMsg getInstance(String fromId, String toId, String type, String cmd, String serial, String content){
        WvmpRedisMsg wvmpRedisMsg = new WvmpRedisMsg();
        wvmpRedisMsg.setFromId(fromId);
        wvmpRedisMsg.setToId(toId);
        wvmpRedisMsg.setType(type);
        wvmpRedisMsg.setCmd(cmd);
        wvmpRedisMsg.setSerial(serial);
        wvmpRedisMsg.setContent(content);
        return wvmpRedisMsg;
    }

    private String fromId;

    private String toId;
    /**
     * req 请求, res 回复
     */
    private String type;
    private String cmd;

    /**
     * 消息的ID
     */
    private String serial;
    private Object content;

    private final static String requestTag = "req";
    private final static String responseTag = "res";

    public static WvmpRedisMsg getRequestInstance(String fromId, String toId, String cmd, String serial, Object content) {
        WvmpRedisMsg wvmpRedisMsg = new WvmpRedisMsg();
        wvmpRedisMsg.setType(requestTag);
        wvmpRedisMsg.setFromId(fromId);
        wvmpRedisMsg.setToId(toId);
        wvmpRedisMsg.setCmd(cmd);
        wvmpRedisMsg.setSerial(serial);
        wvmpRedisMsg.setContent(content);
        return wvmpRedisMsg;
    }

    public static WvmpRedisMsg getResponseInstance() {
        WvmpRedisMsg wvmpRedisMsg = new WvmpRedisMsg();
        wvmpRedisMsg.setType(responseTag);
        return wvmpRedisMsg;
    }

    public static WvmpRedisMsg getResponseInstance(String fromId, String toId, String cmd, String serial, Object content) {
        WvmpRedisMsg wvmpRedisMsg = new WvmpRedisMsg();
        wvmpRedisMsg.setType(responseTag);
        wvmpRedisMsg.setFromId(fromId);
        wvmpRedisMsg.setToId(toId);
        wvmpRedisMsg.setCmd(cmd);
        wvmpRedisMsg.setSerial(serial);
        wvmpRedisMsg.setContent(content);
        return wvmpRedisMsg;
    }

    public static boolean isRequest(WvmpRedisMsg wvmpRedisMsg) {
        return requestTag.equals(wvmpRedisMsg.getType());
    }

    public String getSerial() {
        return serial;
    }

    public void setSerial(String serial) {
        this.serial = serial;
    }

    public String getFromId() {
        return fromId;
    }

    public void setFromId(String fromId) {
        this.fromId = fromId;
    }

    public String getToId() {
        return toId;
    }

    public void setToId(String toId) {
        this.toId = toId;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getCmd() {
        return cmd;
    }

    public void setCmd(String cmd) {
        this.cmd = cmd;
    }

    public Object getContent() {
        return content;
    }

    public void setContent(Object content) {
        this.content = content;
    }
}
