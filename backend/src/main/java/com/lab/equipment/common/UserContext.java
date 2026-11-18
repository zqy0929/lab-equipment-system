package com.lab.equipment.common;

import com.lab.equipment.entity.User;

/** 当前登录用户上下文（由拦截器写入） */
public class UserContext {
    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    public static void set(User user) { HOLDER.set(user); }

    public static User get() { return HOLDER.get(); }

    public static Long getUserId() {
        User u = HOLDER.get();
        return u == null ? null : u.getId();
    }

    public static void clear() { HOLDER.remove(); }
}
