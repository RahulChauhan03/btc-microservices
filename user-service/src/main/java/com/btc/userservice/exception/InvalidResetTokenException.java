package com.btc.userservice.exception;

/** A password reset link that does not exist, was already used or was superseded (HTTP 404). */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException(String message) {
        super(message);
    }
}
