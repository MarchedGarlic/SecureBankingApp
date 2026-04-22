package org.example.banking;

import java.math.BigDecimal;
import java.time.Instant;

public class Transaction {
    public enum TransactionType { DEPOSIT, WITHDRAWAL, TRANSFER }

    private final String id;
    private final String fromAccountId; // null for deposits
    private final String toAccountId;   // null for withdrawals
    private final BigDecimal amount;
    private final TransactionType type;
    private final Instant createdAt;

    public Transaction(String id, String fromAccountId, String toAccountId,
                       BigDecimal amount, TransactionType type, Instant createdAt) {
        this.id = id;
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.type = type;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getFromAccountId() { return fromAccountId; }
    public String getToAccountId() { return toAccountId; }
    public BigDecimal getAmount() { return amount; }
    public TransactionType getType() { return type; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public String toString() {
        return String.format("Transaction[id=%s, type=%s, amount=%s, from=%s, to=%s, at=%s]",
                id, type, amount.toPlainString(), fromAccountId, toAccountId, createdAt);
    }
}
