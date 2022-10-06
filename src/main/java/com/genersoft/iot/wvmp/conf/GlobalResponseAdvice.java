package com.genersoft.iot.wvmp.conf;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.support.spring.FastJsonHttpMessageConverter;
import com.genersoft.iot.wvmp.vmanager.bean.ErrorCode;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.http.HttpMessageConverters;
import org.springframework.context.annotation.Bean;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 全局统一返回结果
 * @author lin
 */
@RestControllerAdvice
public class GlobalResponseAdvice implements ResponseBodyAdvice<Object> {


    @Override
    public boolean supports(@NotNull MethodParameter returnType, @NotNull Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }


    @Override
    public Object beforeBodyWrite(Object body, @NotNull MethodParameter returnType, @NotNull MediaType selectedContentType, @NotNull Class<? extends HttpMessageConverter<?>> selectedConverterType, @NotNull ServerHttpRequest request, @NotNull ServerHttpResponse response) {
        // 排除api文档的接口，这个接口不需要统一
        String[] excludePath = {"/v3/api-docs","/api/v1","/index/hook"};
        for (String path : excludePath) {
            if (request.getURI().getPath().startsWith(path)) {
                return body;
            }
        }

        if (body instanceof com.genersoft.iot.wvmp.vmanager.bean.WvmpResult) {
            return body;
        }

        if (body instanceof ErrorCode) {
            ErrorCode errorCode = (ErrorCode) body;
            return new com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<>(errorCode.getCode(), errorCode.getMsg(), null);
        }

        if (body instanceof String) {
            return JSON.toJSONString(com.genersoft.iot.wvmp.vmanager.bean.WvmpResult.success(body));
        }

        return com.genersoft.iot.wvmp.vmanager.bean.WvmpResult.success(body);
    }

    /**
     * 防止返回string时出错
     * @return
     */
    @Bean
    public HttpMessageConverters custHttpMessageConverter() {
        return new HttpMessageConverters(new FastJsonHttpMessageConverter());
    }
}
