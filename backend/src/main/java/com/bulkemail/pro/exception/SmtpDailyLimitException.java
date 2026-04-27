package com.bulkemail.pro.exception;

public class SmtpDailyLimitException extends RuntimeException {
    public SmtpDailyLimitException(String message) {
        super(message);
    }
}
