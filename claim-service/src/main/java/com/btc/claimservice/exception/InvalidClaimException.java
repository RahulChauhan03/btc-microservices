package com.btc.claimservice.exception;

public class InvalidClaimException extends RuntimeException {

    public InvalidClaimException(String message) {
        super(message);
    }
}
