package org.example.banking;

import java.math.BigDecimal;
import java.util.Objects;

public class SavingsAccount extends BankAccount {
    private final BigDecimal interestRate;

    public SavingsAccount(String id, String ownerId, BigDecimal balance) {
        this(id, ownerId, balance, new BigDecimal("0.02"));
    }

    public SavingsAccount(String id, String ownerId, BigDecimal balance, BigDecimal interestRate) {
        super(id, ownerId, AccountType.SAVINGS, balance);
        this.interestRate = Objects.requireNonNull(interestRate, "Interest rate cannot be null");
    }

    public BigDecimal getInterestRate() {
        return interestRate;
    }

    @Override
    public SavingsAccount clone() {
        return (SavingsAccount) super.clone();
    }

    @Override
    public String toString() {
        return String.format("SavingsAccount[id=%s, balance=%s, interestRate=%s]",
                getId(), getBalance().toPlainString(), interestRate.toPlainString());
    }
}