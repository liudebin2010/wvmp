package com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.response.cmd;

import com.genersoft.iot.wvmp.gb28181.bean.*;
import com.genersoft.iot.wvmp.gb28181.event.EventPublisher;
import com.genersoft.iot.wvmp.gb28181.session.RecordDataCatch;
import com.genersoft.iot.wvmp.gb28181.transmit.callback.DeferredResultHolder;
import com.genersoft.iot.wvmp.gb28181.transmit.callback.RequestMessage;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.SipRequestProcessorParent;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.IMessageHandler;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl.message.response.ResponseMessageHandler;
import com.genersoft.iot.wvmp.utils.DateUtil;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.SipException;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

import static com.genersoft.iot.wvmp.gb28181.utils.XmlUtil.getText;

/**
 * @author lin
 */
@Component
public class RecordInfoResponseMessageHandler extends SipRequestProcessorParent implements InitializingBean, IMessageHandler {

    private final Logger logger = LoggerFactory.getLogger(RecordInfoResponseMessageHandler.class);
    private final String cmdType = "RecordInfo";

    private ConcurrentLinkedQueue<HandlerCatchData> taskQueue = new ConcurrentLinkedQueue<>();

    private boolean taskQueueHandlerRun = false;
    @Autowired
    private ResponseMessageHandler responseMessageHandler;

    @Autowired
    private RecordDataCatch recordDataCatch;

    @Autowired
    private DeferredResultHolder deferredResultHolder;

    @Autowired
    private EventPublisher eventPublisher;

    @Qualifier("taskExecutor")
    @Autowired
    private ThreadPoolTaskExecutor taskExecutor;

    @Override
    public void afterPropertiesSet() throws Exception {
        responseMessageHandler.addHandler(cmdType, this);
    }

    @Override
    public void handForDevice(RequestEvent evt, Device device, Element rootElement) {

        // ??????200 OK
        try {
            responseAck(getServerTransaction(evt), Response.OK);
            taskQueue.offer(new HandlerCatchData(evt, device, rootElement));
            if (!taskQueueHandlerRun) {
                taskQueueHandlerRun = true;
                taskExecutor.execute(()->{
                    while (!taskQueue.isEmpty()) {
                        try {
                            HandlerCatchData take = taskQueue.poll();
                            Element rootElementForCharset = getRootElement(take.getEvt(), take.getDevice().getCharset());
                            if (rootElement == null) {
                                logger.warn("[ ???????????? ] content cannot be null, {}", evt.getRequest());
                                continue;
                            }
                            String sn = getText(rootElementForCharset, "SN");
                            String channelId = getText(rootElementForCharset, "DeviceID");
                            RecordInfo recordInfo = new RecordInfo();
                            recordInfo.setChannelId(channelId);
                            recordInfo.setDeviceId(take.getDevice().getDeviceId());
                            recordInfo.setSn(sn);
                            recordInfo.setName(getText(rootElementForCharset, "Name"));
                            String sumNumStr = getText(rootElementForCharset, "SumNum");
                            int sumNum = 0;
                            if (!ObjectUtils.isEmpty(sumNumStr)) {
                                sumNum = Integer.parseInt(sumNumStr);
                            }
                            recordInfo.setSumNum(sumNum);
                            Element recordListElement = rootElementForCharset.element("RecordList");
                            if (recordListElement == null || sumNum == 0) {
                                logger.info("???????????????");
                                eventPublisher.recordEndEventPush(recordInfo);
                                recordDataCatch.put(take.getDevice().getDeviceId(), sn, sumNum, new ArrayList<>());
                                releaseRequest(take.getDevice().getDeviceId(), sn);
                            } else {
                                Iterator<Element> recordListIterator = recordListElement.elementIterator();
                                if (recordListIterator != null) {
                                    List<RecordItem> recordList = new ArrayList<>();
                                    // ??????DeviceList
                                    while (recordListIterator.hasNext()) {
                                        Element itemRecord = recordListIterator.next();
                                        Element recordElement = itemRecord.element("DeviceID");
                                        if (recordElement == null) {
                                            logger.info("????????????????????????...");
                                            continue;
                                        }
                                        RecordItem record = new RecordItem();
                                        record.setDeviceId(getText(itemRecord, "DeviceID"));
                                        record.setName(getText(itemRecord, "Name"));
                                        record.setFilePath(getText(itemRecord, "FilePath"));
                                        record.setFileSize(getText(itemRecord, "FileSize"));
                                        record.setAddress(getText(itemRecord, "Address"));

                                        String startTimeStr = getText(itemRecord, "StartTime");
                                        record.setStartTime(DateUtil.ISO8601Toyyyy_MM_dd_HH_mm_ss(startTimeStr));

                                        String endTimeStr = getText(itemRecord, "EndTime");
                                        record.setEndTime(DateUtil.ISO8601Toyyyy_MM_dd_HH_mm_ss(endTimeStr));

                                        record.setSecrecy(itemRecord.element("Secrecy") == null ? 0
                                                : Integer.parseInt(getText(itemRecord, "Secrecy")));
                                        record.setType(getText(itemRecord, "Type"));
                                        record.setRecorderId(getText(itemRecord, "RecorderID"));
                                        recordList.add(record);
                                    }
                                    recordInfo.setRecordList(recordList);
                                    // ?????????????????????????????????????????????????????????????????????????????????
                                    eventPublisher.recordEndEventPush(recordInfo);
                                    int count = recordDataCatch.put(take.getDevice().getDeviceId(), sn, sumNum, recordList);
                                    logger.info("[????????????]??? {}->{}: {}/{}", take.getDevice().getDeviceId(), sn, count, sumNum);
                                }

                                if (recordDataCatch.isComplete(take.getDevice().getDeviceId(), sn)){
                                    releaseRequest(take.getDevice().getDeviceId(), sn);
                                }
                            }
                        } catch (DocumentException e) {
                            logger.error("xml??????????????? ", e);
                        }
                    }
                    taskQueueHandlerRun = false;
                });
            }

        } catch (SipException | InvalidArgumentException | ParseException e) {
            logger.error("[??????????????????] ???????????? ????????????: {}", e.getMessage());
        } finally {
            taskQueueHandlerRun = false;
        }
    }

    @Override
    public void handForPlatform(RequestEvent evt, ParentPlatform parentPlatform, Element element) {

    }

    public void releaseRequest(String deviceId, String sn){
        String key = DeferredResultHolder.CALLBACK_CMD_RECORDINFO + deviceId + sn;
        // ?????????????????????
        Collections.sort(recordDataCatch.getRecordInfo(deviceId, sn).getRecordList());

        RequestMessage msg = new RequestMessage();
        msg.setKey(key);
        msg.setData(recordDataCatch.getRecordInfo(deviceId, sn));
        deferredResultHolder.invokeAllResult(msg);
        recordDataCatch.remove(deviceId, sn);
    }
}
