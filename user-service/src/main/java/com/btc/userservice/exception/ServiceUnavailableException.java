package com.btc.userservice.exception;

/** A required integration (e.g. email delivery) is not configured or not reachable (HTTP 503). */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }
}
