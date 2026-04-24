package org.example.banking;

import java.math.BigDecimal;
import java.util.Objects;

public class CheckingAccount extends BankAccount {
    private final BigDecimal overdraftLimit;

    public CheckingAccount(String id, String ownerId, BigDecimal balance) {
        this(id, ownerId, balance, new BigDecimal("500.00"));
    }

    public CheckingAccount(String id, String ownerId, BigDecimal balance, BigDecimal overdraftLimit) {
        super(id, ownerId, AccountType.CHECKING, balance);
        this.overdraftLimit = Objects.requireNonNull(overdraftLimit, "Overdraft limit cannot be null");
    }

    public BigDecimal getOverdraftLimit() {
        return overdraftLimit;
    }

    @Override
    public CheckingAccount clone() {
        return (CheckingAccount) super.clone();
    }

    @Override
    public String toString() {
        return String.format("CheckingAccount[id=%s, balance=%s, overdraftLimit=%s]",
                getId(), getBalance().toPlainString(), overdraftLimit.toPlainString());
    }
}