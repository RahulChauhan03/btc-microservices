package com.btc.claimservice.exception;

/** expense-service answered with a client error that retrying will not fix (e.g. 403, 404). */
public class ExpenseServiceRejectedException extends RuntimeException {

    public ExpenseServiceRejectedException(String message, Throwable cause) {
        super(message, cause);
    }
}
