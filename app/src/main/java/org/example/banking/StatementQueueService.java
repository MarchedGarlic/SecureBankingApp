package org.example.banking;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class StatementQueueService {
    private static volatile StatementQueueService instance;

    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<>(200);
    private final ConcurrentHashMap<String, StatementJob> jobs = new ConcurrentHashMap<>();
    private final Object lifecycleLock = new Object();
    private final List<Thread> workers = new ArrayList<>();

    private volatile boolean running;

    private StatementQueueService() {}

    public static StatementQueueService getInstance() {
        StatementQueueService local = instance;
        if (local == null) {
            synchronized (StatementQueueService.class) {
                local = instance;
                if (local == null) {
                    local = new StatementQueueService();
                    instance = local;
                }
            }
        }
        return local;
    }

    public void startWorkers(int workerCount) {
        if (workerCount <= 0) {
            throw new IllegalArgumentException("Worker count must be positive");
        }

        synchronized (lifecycleLock) {
            if (running) {
                return;
            }
            running = true;
            workers.clear();
            for (int i = 0; i < workerCount; i++) {
                Thread workerThread = new Thread(new StatementWorker(this), "statement-worker-" + (i + 1));
                workerThread.setDaemon(true);
                workers.add(workerThread);
                workerThread.start();
            }
        }
    }

    public void shutdownWorkers() {
        synchronized (lifecycleLock) {
            if (!running) {
                return;
            }
            running = false;
            for (Thread worker : workers) {
                worker.interrupt();
            }
            for (Thread worker : workers) {
                try {
                    worker.join(1500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            workers.clear();
        }
    }

    public String submitJob(String userId, String accountId) throws BankingError {
        Objects.requireNonNull(userId, "User ID cannot be null");
        Objects.requireNonNull(accountId, "Account ID cannot be null");

        BankAccount account = AccountManager.getAccount(accountId);
        if (!account.getOwnerId().equals(userId)) {
            throw new BankingError("Cannot generate statement for another user's account");
        }

        String jobId = UUID.randomUUID().toString();
        StatementJob job = StatementJob.queued(jobId, userId, accountId);
        jobs.put(jobId, job);

        if (!queue.offer(jobId)) {
            jobs.remove(jobId);
            throw new BankingError("Statement queue is full. Try again shortly.");
        }

        return jobId;
    }

    public StatementJob getJobForUser(String jobId, String userId) {
        if (jobId == null || userId == null) {
            return null;
        }
        StatementJob job = jobs.get(jobId);
        if (job == null || !job.getUserId().equals(userId)) {
            return null;
        }
        return job;
    }

    public List<StatementJob> listJobsForUser(String userId) {
        Objects.requireNonNull(userId, "User ID cannot be null");

        List<StatementJob> userJobs = new ArrayList<>();
        for (StatementJob job : jobs.values()) {
            if (job.getUserId().equals(userId)) {
                userJobs.add(job);
            }
        }

        userJobs.sort((a, b) -> b.getRequestedAt().compareTo(a.getRequestedAt()));
        // CWE-375: return an immutable snapshot, not a mutable list reference.
        return List.copyOf(userJobs);
    }

    public boolean cancelJob(String jobId, String userId) {
        if (jobId == null || userId == null) {
            return false;
        }

        StatementJob updated = jobs.compute(jobId, (id, existing) -> {
            if (existing == null || !existing.getUserId().equals(userId)) {
                return existing;
            }
            return switch (existing.getStatus()) {
                case COMPLETED, FAILED, CANCELLED -> existing;
                case QUEUED, PROCESSING -> existing.withStatus(
                        StatementJob.Status.CANCELLED,
                        null,
                        "Cancelled by user");
            };
        });

        if (updated == null) {
            return false;
        }

        queue.remove(jobId);
        return updated.getStatus() == StatementJob.Status.CANCELLED;
    }

    void runWorkerLoop() {
        while (running || !queue.isEmpty()) {
            String jobId;
            try {
                jobId = queue.poll(500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                if (!running) {
                    return;
                }
                Thread.currentThread().interrupt();
                return;
            }

            if (jobId == null) {
                continue;
            }

            processJob(jobId);
        }
    }

    private void processJob(String jobId) {
        StatementJob processing = jobs.compute(jobId, (id, existing) -> {
            if (existing == null) {
                return null;
            }
            if (existing.getStatus() != StatementJob.Status.QUEUED) {
                return existing;
            }
            return existing.withStatus(StatementJob.Status.PROCESSING, null, null);
        });

        if (processing == null || processing.getStatus() != StatementJob.Status.PROCESSING) {
            return;
        }

        try {
            StatementResult result = generateStatement(processing);
            jobs.compute(jobId, (id, existing) -> {
                if (existing == null || existing.getStatus() == StatementJob.Status.CANCELLED) {
                    return existing;
                }
                if (existing.getStatus() != StatementJob.Status.PROCESSING) {
                    return existing;
                }
                return existing.withStatus(StatementJob.Status.COMPLETED, result, null);
            });
        } catch (Exception e) {
            jobs.compute(jobId, (id, existing) -> {
                if (existing == null || existing.getStatus() == StatementJob.Status.CANCELLED) {
                    return existing;
                }
                if (existing.getStatus() != StatementJob.Status.PROCESSING) {
                    return existing;
                }
                return existing.withStatus(StatementJob.Status.FAILED, null, e.getMessage());
            });
        }
    }

    private StatementResult generateStatement(StatementJob job) throws BankingError {
        BankAccount account = AccountManager.getAccount(job.getAccountId());
        if (!account.getOwnerId().equals(job.getUserId())) {
            throw new BankingError("Job account ownership mismatch");
        }

        List<Transaction> transactions = AccountManager.getTransactionHistory(job.getAccountId());
        // CWE-374: pass an immutable snapshot to downstream formatter code.
        List<Transaction> transactionsSnapshot = List.copyOf(transactions);
        Instant generatedAt = Instant.now();
        String statement = StatementFormatter.format(account, transactionsSnapshot, generatedAt);
        return new StatementResult(job.getId(), job.getAccountId(), generatedAt, statement);
    }
}
