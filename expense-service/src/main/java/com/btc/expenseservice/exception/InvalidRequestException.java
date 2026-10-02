package com.btc.expenseservice.exception;

/** A request that is well-formed JSON but not acceptable (bad paging/sort parameters, etc.). */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
