package com.btc.expenseservice.exception;

/** The expense is locked by a claim, or was locked/changed concurrently. */
public class ExpenseConflictException extends RuntimeException {

    public ExpenseConflictException(String message) {
        super(message);
    }
}
