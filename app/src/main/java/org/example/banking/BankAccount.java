package org.example.banking;

import java.math.BigDecimal;
import java.util.Objects;

public class BankAccount {
    public enum AccountType { CHECKING, SAVINGS }

    private final String id;
    private final String ownerId;
    private final AccountType accountType;
    private BigDecimal balance;

    public BankAccount(String id, String ownerId, AccountType accountType, BigDecimal balance) {
        this.id = Objects.requireNonNull(id, "Id cannot be null");
        this.ownerId = Objects.requireNonNull(ownerId, "Owner ID cannot be null");
        this.accountType = Objects.requireNonNull(accountType, "Account type cannot be null");
        this.balance = Objects.requireNonNull(balance, "Balance cannot be null");
    }

    public String getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public AccountType getAccountType() { return accountType; }
    public BigDecimal getBalance() { return balance; }

    /**
     * Updates balance only when the value is valid.
     */
    void setBalance(BigDecimal balance) {
        if (balance == null) {
            throw new IllegalArgumentException("Balance cannot be null");
        }
        if (balance.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Balance cannot be negative: " + balance.toPlainString());
        }
        this.balance = balance;
    }

    @Override
    public String toString() {
        return String.format("BankAccount[id=%s, type=%s, balance=%s]", id, accountType, balance.toPlainString());
    }
}
