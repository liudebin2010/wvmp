package com.genersoft.iot.wvmp.zlm;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.genersoft.iot.wvmp.conf.DynamicTask;
import com.genersoft.iot.wvmp.conf.MediaConfig;
import com.genersoft.iot.wvmp.gb28181.event.EventPublisher;
import com.genersoft.iot.wvmp.zlm.dto.HookSubscribeFactory;
import com.genersoft.iot.wvmp.zlm.dto.HookSubscribeForServerStarted;
import com.genersoft.iot.wvmp.zlm.dto.MediaServerItem;
import com.genersoft.iot.wvmp.service.IMediaServerService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@Order(value=1)
public class ZlmRunner implements CommandLineRunner {

    private final static Logger logger = LoggerFactory.getLogger(ZlmRunner.class);

    private Map<String, Boolean> startGetMedia;

    @Autowired
    private ZlmRestfulUtils zlmresTfulUtils;

    @Autowired
    private ZlmHttpHookSubscribe hookSubscribe;

    @Autowired
    private EventPublisher publisher;

    @Autowired
    private IMediaServerService mediaServerService;

    @Autowired
    private MediaConfig mediaConfig;

    @Autowired
    private DynamicTask dynamicTask;

    @Override
    public void run(String... strings) throws Exception {
        mediaServerService.clearMediaServerForOnline();
        MediaServerItem defaultMediaServer = mediaServerService.getDefaultMediaServer();
        if (defaultMediaServer == null) {
            mediaServerService.addToDatabase(mediaConfig.getMediaSerItem());
        }else {
            MediaServerItem mediaSerItem = mediaConfig.getMediaSerItem();
            mediaServerService.updateToDatabase(mediaSerItem);
        }
        mediaServerService.syncCatchFromDatabase();
        HookSubscribeForServerStarted hookSubscribeForServerStarted = HookSubscribeFactory.on_server_started();
        // ?????? zlm????????????, ??????zlm???????????????????????????
        hookSubscribe.addSubscribe(hookSubscribeForServerStarted,
                (MediaServerItem mediaServerItem, JSONObject response)->{
            ZlmServerConfig zlmServerConfig = JSONObject.toJavaObject(response, ZlmServerConfig.class);
            if (zlmServerConfig !=null ) {
                if (startGetMedia != null) {
                    startGetMedia.remove(zlmServerConfig.getGeneralMediaServerId());
                    if (startGetMedia.size() == 0) {
                        hookSubscribe.removeSubscribe(HookSubscribeFactory.on_server_started());
                    }
                }
            }
        });

        // ??????zlm??????
        logger.info("[zlm] ????????????zlm???...");

        // ???????????????zlm??? ?????????????????????
        List<MediaServerItem> all = mediaServerService.getAllFromDatabase();
        mediaServerService.updateVmServer(all);
        if (all.size() == 0) {
            all.add(mediaConfig.getMediaSerItem());
        }
        for (MediaServerItem mediaServerItem : all) {
            if (startGetMedia == null) {
                startGetMedia = new HashMap<>();
            }
            startGetMedia.put(mediaServerItem.getId(), true);
            connectZlmServer(mediaServerItem);
        }
        String taskKey = "zlm-connect-timeout";
        dynamicTask.startDelay(taskKey, ()->{
            if (startGetMedia != null) {
                Set<String> allZlmId = startGetMedia.keySet();
                for (String id : allZlmId) {
                    logger.error("[ {} ]]???????????????????????????????????????", id);
                }
                startGetMedia = null;
            }
        //  TODO ?????????????????????redis????????????zlm
        }, 60 * 1000 );
    }

    @Async("taskExecutor")
    public void connectZlmServer(MediaServerItem mediaServerItem){
        String connectZlmServerTaskKey = "connect-zlm-" + mediaServerItem.getId();
        ZlmServerConfig zlmServerConfigFirst = getMediaServerConfig(mediaServerItem);
        if (zlmServerConfigFirst != null) {
            zlmServerConfigFirst.setIp(mediaServerItem.getIp());
            zlmServerConfigFirst.setHttpPort(mediaServerItem.getHttpPort());
            startGetMedia.remove(mediaServerItem.getId());
            if (startGetMedia.size() == 0) {
                hookSubscribe.removeSubscribe(HookSubscribeFactory.on_server_started());
            }
            mediaServerService.zlmServerOnline(zlmServerConfigFirst);
        }else {
            logger.info("[ {} ]-[ {}:{} ]??????????????????, ????????????????????? ????????????????????????",
                    mediaServerItem.getId(), mediaServerItem.getIp(), mediaServerItem.getHttpPort());
            publisher.zlmOfflineEventPublish(mediaServerItem.getId());
        }

        dynamicTask.startCron(connectZlmServerTaskKey, ()->{
            ZlmServerConfig zlmServerConfig = getMediaServerConfig(mediaServerItem);
            if (zlmServerConfig != null) {
                dynamicTask.stop(connectZlmServerTaskKey);
                zlmServerConfig.setIp(mediaServerItem.getIp());
                zlmServerConfig.setHttpPort(mediaServerItem.getHttpPort());
                startGetMedia.remove(mediaServerItem.getId());
                if (startGetMedia.size() == 0) {
                    hookSubscribe.removeSubscribe(HookSubscribeFactory.on_server_started());
                }
                mediaServerService.zlmServerOnline(zlmServerConfig);
            }
        }, 2000);
    }

    public ZlmServerConfig getMediaServerConfig(MediaServerItem mediaServerItem) {
        if (startGetMedia == null) { return null;}
        if (!mediaServerItem.isDefaultServer() && mediaServerService.getOne(mediaServerItem.getId()) == null) {
            return null;
        }
        if ( startGetMedia.get(mediaServerItem.getId()) == null || !startGetMedia.get(mediaServerItem.getId())) {
            return null;
        }
        JSONObject responseJson = zlmresTfulUtils.getMediaServerConfig(mediaServerItem);
        ZlmServerConfig zlmServerConfig = null;
        if (responseJson != null) {
            JSONArray data = responseJson.getJSONArray("data");
            if (data != null && data.size() > 0) {
                zlmServerConfig = JSON.parseObject(JSON.toJSONString(data.get(0)), ZlmServerConfig.class);
            }
        } else {
            logger.error("[ {} ]-[ {}:{} ]??????????????????, 2s?????????",
                    mediaServerItem.getId(), mediaServerItem.getIp(), mediaServerItem.getHttpPort());
        }
        return zlmServerConfig;
    }
}
