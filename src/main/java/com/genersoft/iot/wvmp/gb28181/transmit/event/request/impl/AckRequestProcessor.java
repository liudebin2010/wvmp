package com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl;

import com.alibaba.fastjson.JSONObject;
import com.genersoft.iot.wvmp.conf.DynamicTask;
import com.genersoft.iot.wvmp.gb28181.bean.ParentPlatform;
import com.genersoft.iot.wvmp.gb28181.bean.SendRtpItem;
import com.genersoft.iot.wvmp.gb28181.transmit.SipProcessorObserver;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.ISipCommander;
import com.genersoft.iot.wvmp.gb28181.transmit.cmd.ISipCommanderForPlatform;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.ISipRequestProcessor;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.SipRequestProcessorParent;
import com.genersoft.iot.wvmp.zlm.ZlmHttpHookSubscribe;
import com.genersoft.iot.wvmp.zlm.ZlmRtpServerFactory;
import com.genersoft.iot.wvmp.zlm.dto.MediaServerItem;
import com.genersoft.iot.wvmp.service.IMediaServerService;
import com.genersoft.iot.wvmp.service.bean.RequestPushStreamMsg;
import com.genersoft.iot.wvmp.service.redisMsg.RedisGbPlayMsgListener;
import com.genersoft.iot.wvmp.storager.IRedisCatchStorage;
import com.genersoft.iot.wvmp.storager.IVideoManagerStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sip.InvalidArgumentException;
import javax.sip.RequestEvent;
import javax.sip.SipException;
import javax.sip.address.SipURI;
import javax.sip.header.CallIdHeader;
import javax.sip.header.FromHeader;
import javax.sip.header.HeaderAddress;
import javax.sip.header.ToHeader;
import java.text.ParseException;
import java.util.*;

/**
 * SIP命令类型： ACK请求
 */
@Component
public class AckRequestProcessor extends SipRequestProcessorParent implements InitializingBean, ISipRequestProcessor {

	private Logger logger = LoggerFactory.getLogger(AckRequestProcessor.class);
	private final String method = "ACK";

	@Autowired
	private SipProcessorObserver sipProcessorObserver;

	@Override
	public void afterPropertiesSet() throws Exception {
		// 添加消息处理的订阅
		sipProcessorObserver.addRequestProcessor(method, this);
	}

	@Autowired
    private IRedisCatchStorage redisCatchStorage;

	@Autowired
	private IVideoManagerStorage storager;

	@Autowired
	private ZlmRtpServerFactory zlmrtpServerFactory;

	@Autowired
	private IMediaServerService mediaServerService;

	@Autowired
	private ZlmHttpHookSubscribe subscribe;

	@Autowired
	private DynamicTask dynamicTask;

	@Autowired
	private ISipCommander cmder;

	@Autowired
	private ISipCommanderForPlatform commanderForPlatform;

	@Autowired
	private RedisGbPlayMsgListener redisGbPlayMsgListener;


	/**   
	 * 处理  ACK请求
	 * 
	 * @param evt
	 */
	@Override
	public void process(RequestEvent evt) {
		CallIdHeader callIdHeader = (CallIdHeader)evt.getRequest().getHeader(CallIdHeader.NAME);

		String platformGbId = ((SipURI) ((HeaderAddress) evt.getRequest().getHeader(FromHeader.NAME)).getAddress().getURI()).getUser();
		logger.info("[收到ACK]： platformGbId->{}", platformGbId);
		ParentPlatform parentPlatform = storager.queryParentPlatByServerGBId(platformGbId);
		// 取消设置的超时任务
		dynamicTask.stop(callIdHeader.getCallId());
		String channelId = ((SipURI) ((HeaderAddress) evt.getRequest().getHeader(ToHeader.NAME)).getAddress().getURI()).getUser();
		SendRtpItem sendRtpItem =  redisCatchStorage.querySendRTPServer(platformGbId, channelId, null, callIdHeader.getCallId());
		if (sendRtpItem == null) {
			logger.warn("[收到ACK]：未找到通道({})的推流信息", channelId);
			return;
		}
		String is_Udp = sendRtpItem.isTcp() ? "0" : "1";
		MediaServerItem mediaInfo = mediaServerService.getOne(sendRtpItem.getMediaServerId());
		logger.info("收到ACK，rtp/{}开始向上级推流, 目标={}:{}，SSRC={}", sendRtpItem.getStreamId(), sendRtpItem.getIp(), sendRtpItem.getPort(), sendRtpItem.getSsrc());
		Map<String, Object> param = new HashMap<>(12);
		param.put("vhost","__defaultVhost__");
		param.put("app",sendRtpItem.getApp());
		param.put("stream",sendRtpItem.getStreamId());
		param.put("ssrc", sendRtpItem.getSsrc());
		param.put("dst_url",sendRtpItem.getIp());
		param.put("dst_port", sendRtpItem.getPort());
		param.put("is_udp", is_Udp);
		param.put("src_port", sendRtpItem.getLocalPort());
		param.put("pt", sendRtpItem.getPt());
		param.put("use_ps", sendRtpItem.isUsePs() ? "1" : "0");
		param.put("only_audio", sendRtpItem.isOnlyAudio() ? "1" : "0");
		if (!sendRtpItem.isTcp() && parentPlatform.isRtcp()) {
			// 开启rtcp保活
			param.put("udp_rtcp_timeout", "1");
		}

		if (mediaInfo == null) {
			RequestPushStreamMsg requestPushStreamMsg = RequestPushStreamMsg.getInstance(
					sendRtpItem.getMediaServerId(), sendRtpItem.getApp(), sendRtpItem.getStreamId(),
					sendRtpItem.getIp(), sendRtpItem.getPort(), sendRtpItem.getSsrc(), sendRtpItem.isTcp(),
					sendRtpItem.getLocalPort(), sendRtpItem.getPt(), sendRtpItem.isUsePs(), sendRtpItem.isOnlyAudio());
			redisGbPlayMsgListener.sendMsgForStartSendRtpStream(sendRtpItem.getServerId(), requestPushStreamMsg, jsonObject->{
				startSendRtpStreamHand(evt, sendRtpItem, parentPlatform, jsonObject, param, callIdHeader);
			});
		}else {
			JSONObject jsonObject = zlmrtpServerFactory.startSendRtpStream(mediaInfo, param);
			startSendRtpStreamHand(evt, sendRtpItem, parentPlatform, jsonObject, param, callIdHeader);
		}
	}
	private void startSendRtpStreamHand(RequestEvent evt, SendRtpItem sendRtpItem, ParentPlatform parentPlatform,
										JSONObject jsonObject, Map<String, Object> param, CallIdHeader callIdHeader) {
		if (jsonObject == null) {
			logger.error("RTP推流失败: 请检查ZLM服务");
		} else if (jsonObject.getInteger("code") == 0) {
			logger.info("调用ZLM推流接口, 结果： {}",  jsonObject);
			logger.info("RTP推流成功[ {}/{} ]，{}->{}:{}, " ,param.get("app"), param.get("stream"), jsonObject.getString("local_port"), param.get("dst_url"), param.get("dst_port"));
		} else {
			logger.error("RTP推流失败: {}, 参数：{}",jsonObject.getString("msg"),JSONObject.toJSON(param));
			if (sendRtpItem.isOnlyAudio()) {
				// TODO 可能是语音对讲
			}else {
				// 向上级平台
				try {
					commanderForPlatform.streamByeCmd(parentPlatform, callIdHeader.getCallId());
				} catch (SipException | InvalidArgumentException | ParseException e) {
					logger.error("[命令发送失败] 国标级联 发送BYE: {}", e.getMessage());
				}
			}
		}
	}
}
