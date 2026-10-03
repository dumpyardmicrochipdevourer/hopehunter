package com.antonk404.hhbot.hh;

/** hh не узнал сессию: куки протухли или пользователь вышел на сайте. */
public class HhSessionExpiredException extends HhException {

    public HhSessionExpiredException() {
        super("hh session expired");
    }
}
