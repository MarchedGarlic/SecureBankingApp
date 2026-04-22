package org.example.banking;

public class BankingError extends Exception {
    public BankingError(String message) {
        super(message);
    }
}
