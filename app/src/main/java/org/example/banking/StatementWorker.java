package org.example.banking;

final class StatementWorker implements Runnable {
    private final StatementQueueService service;

    StatementWorker(StatementQueueService service) {
        this.service = service;
    }

    @Override
    public void run() {
        // CWE-431: ensure worker failures are handled instead of terminating silently.
        try {
            service.runWorkerLoop();
        } catch (RuntimeException e) {
            System.err.println("Statement worker terminated unexpectedly: " + e.getMessage());
        }
    }
}
