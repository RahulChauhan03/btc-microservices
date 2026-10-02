package com.btc.tripservice.exception;

/** The trip still has dependent records (expenses) and cannot be removed. */
public class TripInUseException extends RuntimeException {

    public TripInUseException(String message) {
        super(message);
    }
}
