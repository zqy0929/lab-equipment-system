package com.lab.equipment.common;

/** 业务异常：由全局异常处理器转成统一错误返回 */
public class BusinessException extends RuntimeException {
    public BusinessException(String message) { super(message); }
}
