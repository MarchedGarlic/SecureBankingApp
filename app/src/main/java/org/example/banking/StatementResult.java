package org.example.banking;

import java.time.Instant;
import java.util.Objects;

public final class StatementResult {
    private final String jobId;
    private final String accountId;
    private final Instant generatedAt;
    private final String statementText;

    public StatementResult(String jobId, String accountId, Instant generatedAt, String statementText) {
        this.jobId = Objects.requireNonNull(jobId, "Job ID cannot be null");
        this.accountId = Objects.requireNonNull(accountId, "Account ID cannot be null");
        this.generatedAt = Objects.requireNonNull(generatedAt, "Generated at cannot be null");
        this.statementText = Objects.requireNonNull(statementText, "Statement text cannot be null");
    }

    public String getJobId() {
        return jobId;
    }

    public String getAccountId() {
        return accountId;
    }

    public Instant getGeneratedAt() {
        return generatedAt;
    }

    public String getStatementText() {
        return statementText;
    }
}
