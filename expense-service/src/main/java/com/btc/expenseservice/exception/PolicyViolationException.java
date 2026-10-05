package com.btc.expenseservice.exception;

/** A new or changed expense breaks the applicable travel policy (HTTP 422); nothing is saved. */
public class PolicyViolationException extends RuntimeException {

    public PolicyViolationException(String message) {
        super(message);
    }
}
