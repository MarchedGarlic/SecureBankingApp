package org.example.banking;

import java.time.Instant;
import java.util.Objects;

public final class StatementJob {
    public enum Status {
        QUEUED,
        PROCESSING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    private final String id;
    private final String userId;
    private final String accountId;
    private final Instant requestedAt;
    private final Status status;
    private final StatementResult result;
    private final String errorMessage;

    private StatementJob(String id, String userId, String accountId, Instant requestedAt,
                         Status status, StatementResult result, String errorMessage) {
        this.id = Objects.requireNonNull(id, "Job ID cannot be null");
        this.userId = Objects.requireNonNull(userId, "User ID cannot be null");
        this.accountId = Objects.requireNonNull(accountId, "Account ID cannot be null");
        this.requestedAt = Objects.requireNonNull(requestedAt, "Requested at cannot be null");
        this.status = Objects.requireNonNull(status, "Status cannot be null");
        this.result = result;
        this.errorMessage = errorMessage;
    }

    public static StatementJob queued(String id, String userId, String accountId) {
        return new StatementJob(id, userId, accountId, Instant.now(), Status.QUEUED, null, null);
    }

    public StatementJob withStatus(Status newStatus, StatementResult newResult, String newErrorMessage) {
        return new StatementJob(id, userId, accountId, requestedAt, newStatus, newResult, newErrorMessage);
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getAccountId() {
        return accountId;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public Status getStatus() {
        return status;
    }

    public StatementResult getResult() {
        return result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
