package com.genersoft.iot.wvmp.gb28181.transmit.event.response.impl;

import com.genersoft.iot.wvmp.conf.SipConfig;
import com.genersoft.iot.wvmp.gb28181.SipLayer;
import com.genersoft.iot.wvmp.gb28181.transmit.SipProcessorObserver;
import com.genersoft.iot.wvmp.gb28181.transmit.event.response.SipResponseProcessorAbstract;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.sip.ResponseEvent;

/**    
 * @description: CANCEL响应处理器
 * @author: panlinlin
 * @date:   2021年11月5日 16:35
 */
@Component
public class CancelResponseProcessor extends SipResponseProcessorAbstract {

	private final String method = "CANCEL";

	@Autowired
	private SipLayer sipLayer;

	@Autowired
	private SipConfig config;

	@Autowired
	private SipProcessorObserver sipProcessorObserver;

	@Override
	public void afterPropertiesSet() throws Exception {
		// 添加消息处理的订阅
		sipProcessorObserver.addResponseProcessor(method, this);
	}
	/**   
	 * 处理CANCEL响应
	 *  
	 * @param evt
	 */
	@Override
	public void process(ResponseEvent evt) {
		// TODO Auto-generated method stub
		
	}

}
