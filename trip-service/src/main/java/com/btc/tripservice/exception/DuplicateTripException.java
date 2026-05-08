package com.btc.tripservice.exception;

public class DuplicateTripException extends RuntimeException {

    public DuplicateTripException(String message) {
        super(message);
    }
}
