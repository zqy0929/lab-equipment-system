package com.lab.equipment.common;

import lombok.Data;

/** 统一接口返回结构 */
@Data
public class Result<T> {
    private int code;      // 0 成功，非 0 失败
    private String msg;
    private T data;

    public static <T> Result<T> ok() { return ok(null); }

    public static <T> Result<T> ok(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.msg = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> fail(String msg) { return fail(400, msg); }

    public static <T> Result<T> fail(int code, String msg) {
        Result<T> r = new Result<>();
        r.code = code;
        r.msg = msg;
        return r;
    }
}
