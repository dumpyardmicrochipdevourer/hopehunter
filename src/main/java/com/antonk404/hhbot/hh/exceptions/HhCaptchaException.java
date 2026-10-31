package com.antonk404.hhbot.hh.exceptions;

/** The site asks the user to solve a captcha; the session may still be valid. */
public class HhCaptchaException extends HhException {
    public HhCaptchaException() { super("hh просит пройти капчу"); }
}
