package com.btc.claimservice.exception;

/** A downstream service refused a request in a way retrying cannot fix; the outbox marks the event FAILED. */
public class PermanentDeliveryException extends RuntimeException {

    public PermanentDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
