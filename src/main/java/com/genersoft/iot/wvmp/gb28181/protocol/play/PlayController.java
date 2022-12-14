package com.genersoft.iot.wvmp.gb28181.protocol.play;

import com.alibaba.fastjson.JSONArray;
import com.genersoft.iot.wvmp.common.StreamInfo;
import com.genersoft.iot.wvmp.conf.exception.ControllerException;
import com.genersoft.iot.wvmp.conf.exception.SsrcTransactionNotFoundException;
import com.genersoft.iot.wvmp.gb28181.bean.SsrcTransaction;
import com.genersoft.iot.wvmp.gb28181.session.VideoStreamSessionManager;
import com.genersoft.iot.wvmp.gb28181.bean.Device;
import com.genersoft.iot.wvmp.gb28181.transmit.callback.DeferredResultHolder;
import com.genersoft.iot.wvmp.gb28181.transmit.callback.RequestMessage;
import com.genersoft.iot.wvmp.zlm.ZlmRestfulUtils;
import com.genersoft.iot.wvmp.zlm.dto.MediaServerItem;
import com.genersoft.iot.wvmp.service.IMediaServerService;
import com.genersoft.iot.wvmp.storager.IRedisCatchStorage;
import com.genersoft.iot.wvmp.vmanager.bean.ErrorCode;
import com.genersoft.iot.wvmp.vmanager.bean.WvmpResult;
import com.genersoft.iot.wvmp.gb28181.protocol.play.bean.PlayResult;
import com.genersoft.iot.wvmp.service.IMediaService;
import com.genersoft.iot.wvmp.service.IPlayService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.alibaba.fastjson.JSONObject;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.impl.SipCommander;
import com.genersoft.iot.wvmp.storager.IVideoManagerStorage;
import org.springframework.web.context.request.async.DeferredResult;

import javax.sip.InvalidArgumentException;
import javax.sip.SipException;
import java.text.ParseException;
import java.util.List;
import java.util.UUID;

@Tag(name  = "??????????????????")
@CrossOrigin
@RestController
@RequestMapping("/api/play")
public class PlayController {

	private final static Logger logger = LoggerFactory.getLogger(PlayController.class);

	@Autowired
	private SipCommander cmder;

	@Autowired
	private VideoStreamSessionManager streamSession;

	@Autowired
	private IVideoManagerStorage storager;

	@Autowired
	private IRedisCatchStorage redisCatchStorage;

	@Autowired
	private ZlmRestfulUtils zlmresTfulUtils;

	@Autowired
	private DeferredResultHolder resultHolder;

	@Autowired
	private IPlayService playService;

	@Autowired
	private IMediaService mediaService;

	@Autowired
	private IMediaServerService mediaServerService;

	@Operation(summary = "????????????")
	@Parameter(name = "deviceId", description = "??????????????????", required = true)
	@Parameter(name = "channelId", description = "??????????????????", required = true)
	@GetMapping("/start/{deviceId}/{channelId}")
	public DeferredResult<WvmpResult<String>> play(@PathVariable String deviceId,
													   @PathVariable String channelId) {

		// ???????????????zlm
		Device device = storager.queryVideoDevice(deviceId);
		MediaServerItem newMediaServerItem = playService.getNewMediaServerItem(device);
		PlayResult playResult = playService.play(newMediaServerItem, deviceId, channelId, null, null, null);

		return playResult.getResult();
	}


	@Operation(summary = "????????????")
	@Parameter(name = "deviceId", description = "??????????????????", required = true)
	@Parameter(name = "channelId", description = "??????????????????", required = true)
	@GetMapping("/stop/{deviceId}/{channelId}")
	public JSONObject playStop(@PathVariable String deviceId, @PathVariable String channelId) {

		logger.debug(String.format("????????????/????????????API?????????streamId???%s_%s", deviceId, channelId ));

		if (deviceId == null || channelId == null) {
			throw new ControllerException(ErrorCode.ERROR400);
		}

		Device device = storager.queryVideoDevice(deviceId);
		if (device == null) {
			throw new ControllerException(ErrorCode.ERROR100.getCode(), "??????[" + deviceId + "]?????????");
		}

		StreamInfo streamInfo = redisCatchStorage.queryPlayByDevice(deviceId, channelId);
		if (streamInfo == null) {
			throw new ControllerException(ErrorCode.ERROR100.getCode(), "???????????????");
		}

		try {
			logger.warn("[????????????] {}/{}", device.getDeviceId(), channelId);
			cmder.streamByeCmd(device, channelId, streamInfo.getStream(), null, null);
		} catch (InvalidArgumentException | SipException | ParseException | SsrcTransactionNotFoundException e) {
			logger.error("[??????????????????] ??????????????? ??????BYE: {}", e.getMessage());
			throw new ControllerException(ErrorCode.ERROR100.getCode(), "??????????????????: " + e.getMessage());
		}
		redisCatchStorage.stopPlay(streamInfo);

		storager.stopPlay(streamInfo.getDeviceID(), streamInfo.getChannelId());
		JSONObject json = new JSONObject();
		json.put("deviceId", deviceId);
		json.put("channelId", channelId);
		return json;

	}

	/**
	 * ?????????h264???????????????ffmpeg ?????????h264 + aac
	 * @param streamId ???ID
	 */
	@Operation(summary = "?????????h264???????????????ffmpeg ?????????h264 + aac")
	@Parameter(name = "streamId", description = "?????????ID", required = true)
	@PostMapping("/convert/{streamId}")
	public JSONObject playConvert(@PathVariable String streamId) {
		StreamInfo streamInfo = redisCatchStorage.queryPlayByStreamId(streamId);
		if (streamInfo == null) {
			streamInfo = redisCatchStorage.queryPlayback(null, null, streamId, null);
		}
		if (streamInfo == null) {
			logger.warn("????????????API???????????????, ?????????????????????!");
			throw new ControllerException(ErrorCode.ERROR100.getCode(), "????????????????????????, ???????????????????????????");
		}
		MediaServerItem mediaInfo = mediaServerService.getOne(streamInfo.getMediaServerId());
		JSONObject rtpInfo = zlmresTfulUtils.getRtpInfo(mediaInfo, streamId);
		if (!rtpInfo.getBoolean("exist")) {
			logger.warn("????????????API???????????????, ????????????????????????!");
			throw new ControllerException(ErrorCode.ERROR100.getCode(), "????????????????????????, ??????????????????????????????");
		} else {
			String dstUrl = String.format("rtmp://%s:%s/convert/%s", "127.0.0.1", mediaInfo.getRtmpPort(),
					streamId );
			String srcUrl = String.format("rtsp://%s:%s/rtp/%s", "127.0.0.1", mediaInfo.getRtspPort(), streamId);
			JSONObject jsonObject = zlmresTfulUtils.addFFmpegSource(mediaInfo, srcUrl, dstUrl, "1000000", true, false, null);
			logger.info(jsonObject.toJSONString());
			if (jsonObject != null && jsonObject.getInteger("code") == 0) {
				JSONObject data = jsonObject.getJSONObject("data");
				if (data != null) {
					JSONObject result = new JSONObject();
					result.put("key", data.getString("key"));
					StreamInfo streamInfoResult = mediaService.getStreamInfoByAppAndStreamWithCheck("convert", streamId, mediaInfo.getId(), false);
					result.put("StreamInfo", streamInfoResult);
					return result;
				}else {
					throw new ControllerException(ErrorCode.ERROR100.getCode(), "????????????");
				}
			}else {
				throw new ControllerException(ErrorCode.ERROR100.getCode(), "????????????");
			}
		}
	}

	/**
	 * ????????????
	 */
	@Operation(summary = "????????????")
	@Parameter(name = "key", description = "?????????key", required = true)
	@Parameter(name = "mediaServerId", description = "???????????????ID", required = true)
	@PostMapping("/convertStop/{key}")
	public void playConvertStop(@PathVariable String key, String mediaServerId) {
		if (mediaServerId == null) {
			throw new ControllerException(ErrorCode.ERROR400.getCode(), "????????????" + mediaServerId + "?????????" );
		}
		MediaServerItem mediaInfo = mediaServerService.getOne(mediaServerId);
		if (mediaInfo == null) {
			throw new ControllerException(ErrorCode.ERROR100.getCode(), "????????????????????????????????????" );
		}else {
			JSONObject jsonObject = zlmresTfulUtils.delFFmpegSource(mediaInfo, key);
			logger.info(jsonObject.toJSONString());
			if (jsonObject != null && jsonObject.getInteger("code") == 0) {
				JSONObject data = jsonObject.getJSONObject("data");
				if (data == null || data.getBoolean("flag") == null || !data.getBoolean("flag")) {
					throw new ControllerException(ErrorCode.ERROR100 );
				}
			}else {
				throw new ControllerException(ErrorCode.ERROR100 );
			}
		}
	}

	@Operation(summary = "??????????????????")
	@Parameter(name = "deviceId", description = "??????????????????", required = true)
    @GetMapping("/broadcast/{deviceId}")
    @PostMapping("/broadcast/{deviceId}")
    public DeferredResult<String> broadcastApi(@PathVariable String deviceId) {
        if (logger.isDebugEnabled()) {
            logger.debug("????????????API??????");
        }
        Device device = storager.queryVideoDevice(deviceId);
		DeferredResult<String> result = new DeferredResult<>(3 * 1000L);
		String key  = DeferredResultHolder.CALLBACK_CMD_BROADCAST + deviceId;
		if (resultHolder.exist(key, null)) {
			result.setResult("???????????????");
			return result;
		}
		String uuid  = UUID.randomUUID().toString();
        if (device == null) {

			resultHolder.put(key, key,  result);
			RequestMessage msg = new RequestMessage();
			msg.setKey(key);
			msg.setId(uuid);
			JSONObject json = new JSONObject();
			json.put("DeviceID", deviceId);
			json.put("CmdType", "Broadcast");
			json.put("Result", "Failed");
			json.put("Description", "Device ?????????");
			msg.setData(json);
			resultHolder.invokeResult(msg);
			return result;
		}
		try {
			cmder.audioBroadcastCmd(device, (event) -> {
				RequestMessage msg = new RequestMessage();
				msg.setKey(key);
				msg.setId(uuid);
				JSONObject json = new JSONObject();
				json.put("DeviceID", deviceId);
				json.put("CmdType", "Broadcast");
				json.put("Result", "Failed");
				json.put("Description", String.format("??????????????????????????????????????? %s, %s", event.statusCode, event.msg));
				msg.setData(json);
				resultHolder.invokeResult(msg);
			});
		} catch (InvalidArgumentException | SipException | ParseException e) {
			logger.error("[??????????????????] ????????????: {}", e.getMessage());
			throw new ControllerException(ErrorCode.ERROR100.getCode(), "??????????????????: " + e.getMessage());
		}

		result.onTimeout(() -> {
			logger.warn("????????????????????????, ???????????????????????????");
			RequestMessage msg = new RequestMessage();
			msg.setKey(key);
			msg.setId(uuid);
			JSONObject json = new JSONObject();
			json.put("DeviceID", deviceId);
			json.put("CmdType", "Broadcast");
			json.put("Result", "Failed");
			json.put("Error", "Timeout. Device did not response to broadcast command.");
			msg.setData(json);
			resultHolder.invokeResult(msg);
		});
		resultHolder.put(key, uuid, result);
		return result;
	}

	@Operation(summary = "???????????????ssrc")
	@GetMapping("/ssrc")
	public JSONObject getSSRC() {
		if (logger.isDebugEnabled()) {
			logger.debug("???????????????ssrc");
		}
		JSONArray objects = new JSONArray();
		List<SsrcTransaction> allSsrc = streamSession.getAllSsrc();
		for (SsrcTransaction transaction : allSsrc) {
			JSONObject jsonObject = new JSONObject();
			jsonObject.put("deviceId", transaction.getDeviceId());
			jsonObject.put("channelId", transaction.getChannelId());
			jsonObject.put("ssrc", transaction.getSsrc());
			jsonObject.put("streamId", transaction.getStream());
			objects.add(jsonObject);
		}

		JSONObject jsonObject = new JSONObject();
		jsonObject.put("data", objects);
		jsonObject.put("count", objects.size());
		return jsonObject;
	}

}

