package org.example.banking;

final class StatementWorker implements Runnable {
    private final StatementQueueService service;

    StatementWorker(StatementQueueService service) {
        this.service = service;
    }

    @Override
    public void run() {
        service.runWorkerLoop();
    }
}
