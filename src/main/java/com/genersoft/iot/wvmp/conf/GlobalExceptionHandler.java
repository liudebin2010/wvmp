package com.genersoft.iot.wvmp.conf;

import com.genersoft.iot.wvmp.conf.exception.ControllerException;
import com.genersoft.iot.wvmp.vmanager.bean.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final static Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * 默认异常处理
     * @param e 异常
     * @return 统一返回结果
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<String> exceptionHandler(Exception e) {
        logger.error("[全局异常]： ", e);
        return com.genersoft.iot.wvmp.vmanager.bean.WvmpResult.fail(ErrorCode.ERROR500.getCode(), e.getMessage());
    }


    /**
     * 自定义异常处理， 处理controller中返回的错误
     * @param e 异常
     * @return 统一返回结果
     */
    @ExceptionHandler(ControllerException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<String> exceptionHandler(ControllerException e) {
        return com.genersoft.iot.wvmp.vmanager.bean.WvmpResult.fail(e.getCode(), e.getMsg());
    }

    /**
     * 登陆失败
     * @param e 异常
     * @return 统一返回结果
     */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public com.genersoft.iot.wvmp.vmanager.bean.WvmpResult<String> exceptionHandler(BadCredentialsException e) {
        return com.genersoft.iot.wvmp.vmanager.bean.WvmpResult.fail(ErrorCode.ERROR100.getCode(), e.getMessage());
    }
}
