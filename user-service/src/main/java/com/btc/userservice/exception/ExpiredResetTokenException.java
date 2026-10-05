package com.btc.userservice.exception;

/** A password reset link past its expiry (HTTP 410). */
public class ExpiredResetTokenException extends RuntimeException {

    public ExpiredResetTokenException(String message) {
        super(message);
    }
}
