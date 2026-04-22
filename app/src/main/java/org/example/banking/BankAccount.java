package org.example.banking;

import java.math.BigDecimal;

public class BankAccount {
    public enum AccountType { CHECKING, SAVINGS }

    private final String id;
    private final String ownerId;
    private final AccountType accountType;
    private BigDecimal balance;

    public BankAccount(String id, String ownerId, AccountType accountType, BigDecimal balance) {
        this.id = id;
        this.ownerId = ownerId;
        this.accountType = accountType;
        this.balance = balance;
    }

    public String getId() { return id; }
    public String getOwnerId() { return ownerId; }
    public AccountType getAccountType() { return accountType; }
    public BigDecimal getBalance() { return balance; }

    void setBalance(BigDecimal balance) { this.balance = balance; }

    @Override
    public String toString() {
        return String.format("BankAccount[id=%s, type=%s, balance=%s]", id, accountType, balance.toPlainString());
    }
}
