package com.btc.userservice.exception;

/** Rate limit exceeded (HTTP 429). */
public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}
