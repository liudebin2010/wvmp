package com.genersoft.iot.wvmp.gb28181.transmit.event.request.impl;

import com.genersoft.iot.wvmp.gb28181.transmit.SipProcessorObserver;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.ISipRequestProcessor;
import com.genersoft.iot.wvmp.gb28181.transmit.event.request.SipRequestProcessorParent;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sip.RequestEvent;

/**
 * SIP命令类型： CANCEL请求
 */
@Component
public class CancelRequestProcessor extends SipRequestProcessorParent implements InitializingBean, ISipRequestProcessor {

	private final String method = "CANCEL";

	@Autowired
	private SipProcessorObserver sipProcessorObserver;

	@Override
	public void afterPropertiesSet() throws Exception {
		// 添加消息处理的订阅
		sipProcessorObserver.addRequestProcessor(method, this);
	}

	/**   
	 * 处理CANCEL请求
	 *  
	 * @param evt 事件
	 */
	@Override
	public void process(RequestEvent evt) {
		// TODO 优先级99 Cancel Request消息实现，此消息一般为级联消息，上级给下级发送请求取消指令
		
	}

}
