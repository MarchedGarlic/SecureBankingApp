package org.example;

import org.example.authentication.AuthenticationError;
import org.example.authentication.LoginService;
import org.example.authentication.LoginService.SessionLength;
import org.example.authentication.Session;
import org.example.banking.AccountManager;
import org.example.banking.BankAccount;
import org.example.banking.BankingError;
import org.example.banking.StatementJob;
import org.example.banking.StatementQueueService;
import org.example.banking.Transaction;
import org.example.cryptography.EncryptionError;
import org.example.cryptography.UserManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Scanner;

/**
 * Entry point for the Secure Banking App.
 *
 * On startup the console shows a main menu:
 *   [1] Create account
 *   [2] Login
 *   [3] Exit
 *
 * After a successful login a second menu is shown with placeholder options
 * that can be implemented in future sprints.
 */
public class App {

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        StatementQueueService statementQueue = StatementQueueService.getInstance();
        statementQueue.startWorkers(3);

        try {
            printBanner();

            while (running) {
                printMainMenu();
                String choice = readTrimmedLine(scanner);
                if (choice == null) {
                    System.out.println("\nInput closed. Exiting.");
                    break;
                }

                switch (choice) {
                    case "1" -> handleCreateAccount(scanner);
                    case "2" -> handleLogin(scanner);
                    case "3" -> {
                        System.out.println("\nGoodbye.");
                        running = false;
                    }
                    default  -> System.out.println("  Invalid option. Please enter 1, 2, or 3.\n");
                }
            }
        } finally {
            statementQueue.shutdownWorkers();
            scanner.close();
        }
    }

    private static void printBanner() {
        System.out.println("╔══════════════════════════════════════╗");
        System.out.println("║       SECURE BANKING APP             ║");
        System.out.println("╚══════════════════════════════════════╝");
        System.out.println();
    }

    private static void printMainMenu() {
        System.out.println("──────────────────────────────────────");
        System.out.println("  Main Menu");
        System.out.println("──────────────────────────────────────");
        System.out.println("  [1] Create account");
        System.out.println("  [2] Login");
        System.out.println("  [3] Exit");
        System.out.print("\n  Choose an option: ");
    }

    private static void printAccountMenu(Session session) {
        System.out.println("\n──────────────────────────────────────");
        System.out.println("  Welcome, " + session.getUsername() + "!");
        System.out.println("  Session expires: " + session.getExpiresAt());
        System.out.println("──────────────────────────────────────");
        System.out.println("  [1] View balances");
        System.out.println("  [2] Deposit funds");
        System.out.println("  [3] Withdraw funds");
        System.out.println("  [4] Transfer funds");
        System.out.println("  [5] Transaction history");
        System.out.println("  [6] Open account");
        System.out.println("  [7] Close account");
        System.out.println("  [8] Generate statement");
        System.out.println("  [9] View statement job");
        System.out.println("  [10] Cancel statement job");
        System.out.println("  [11] Logout all sessions");
        System.out.println("  [12] Logout");
        System.out.print("\n  Choose an option: ");
    }

    private static void handleCreateAccount(Scanner scanner) {
        System.out.println("\n── Create Account ─────────────────────");

        System.out.print("  Username: ");
        String username = readTrimmedLine(scanner);
        if (username == null) return;

        System.out.print("  Password: ");
        String password = readTrimmedLine(scanner);
        if (password == null) return;

        System.out.print("  Confirm password: ");
        String confirm = readTrimmedLine(scanner);
        if (confirm == null) return;

        if (!password.equals(confirm)) {
            System.out.println("  Error: passwords do not match.\n");
            return;
        }

        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("  Error: username and password cannot be empty.\n");
            return;
        }

        try {
            UserManager.newUser(username, password);
            System.out.println("  Account created successfully! You can now log in.\n");
        } catch (EncryptionError e) {
            System.out.println("  Error creating account: " + e.getMessage() + "\n");
        }
    }

    private static void handleLogin(Scanner scanner) {
        System.out.println("\n── Login ───────────────────────────────");

        System.out.print("  Username: ");
        String username = readTrimmedLine(scanner);
        if (username == null) return;

        System.out.print("  Password: ");
        String password = readTrimmedLine(scanner);
        if (password == null) return;

        try {
            Session session = LoginService.login(username, password, SessionLength.STANDARD);
            System.out.println("  Login successful!\n");
            handleAccountMenu(scanner, session);
        } catch (AuthenticationError e) {
            System.out.println("  Login failed: " + e.getMessage() + "\n");
        }
    }

    private static void handleAccountMenu(Scanner scanner, Session session) {
        boolean loggedIn = true;

        while (loggedIn) {
            try {
                session = LoginService.validateAndRefresh(session.getToken(), SessionLength.STANDARD);
            } catch (AuthenticationError e) {
                System.out.println("\n  Session expired. Please log in again.\n");
                return;
            }

            printAccountMenu(session);
            String choice = readTrimmedLine(scanner);
            if (choice == null) {
                System.out.println("\nInput closed. Logging out.\n");
                loggedIn = false;
                continue;
            }

            switch (choice) {
                case "1" -> handleViewBalances(session);
                case "2" -> handleDeposit(scanner, session);
                case "3" -> handleWithdraw(scanner, session);
                case "4" -> handleTransfer(scanner, session);
                case "5" -> handleTransactionHistory(scanner, session);
                case "6" -> handleOpenAccount(scanner, session);
                case "7" -> handleCloseAccount(scanner, session);
                case "8" -> handleGenerateStatement(scanner, session);
                case "9" -> handleViewStatementJob(scanner, session);
                case "10" -> handleCancelStatementJob(scanner, session);
                case "11" -> {
                    try {
                        LoginService.logoutAll(session.getUserId());
                        System.out.println("\n  Logged out from all sessions successfully.\n");
                    } catch (AuthenticationError e) {
                        System.out.println("\n  Error logging out all sessions: " + e.getMessage() + "\n");
                    }
                    loggedIn = false;
                }
                case "12" -> {
                    try {
                        LoginService.logout(session.getToken());
                    } catch (AuthenticationError e) {
                        System.out.println("  Warning: logout encountered an error: " + e.getMessage());
                    }
                    System.out.println("\n  Logged out successfully.\n");
                    loggedIn = false;
                }
                default -> System.out.println("  Invalid option. Please enter 1-12.\n");
            }
        }
    }

    private static void handleGenerateStatement(Scanner scanner, Session session) {
        try {
            BankAccount account = promptForOwnedAccount(scanner, session, "Select account for statement generation");
            if (account == null) return;

            String jobId = StatementQueueService.getInstance().submitJob(session.getUserId(), account.getId());
            System.out.println("\n  Statement job submitted. Job ID: " + jobId + "\n");
        } catch (BankingError e) {
            System.out.println("\n  Could not submit statement job: " + e.getMessage() + "\n");
        }
    }

    private static void handleViewStatementJob(Scanner scanner, Session session) {
        StatementJob job = promptForStatementJob(scanner, session, "Select statement job to view");
        if (job == null) {
            return;
        }

        System.out.println("\n  Job status: " + job.getStatus());
        if (job.getErrorMessage() != null && !job.getErrorMessage().isBlank()) {
            System.out.println("  Error: " + job.getErrorMessage());
        }

        if (job.getStatus() == StatementJob.Status.COMPLETED && job.getResult() != null) {
            System.out.println("\n── Statement Result ──────────────────");
            System.out.println(job.getResult().getStatementText());
        } else {
            System.out.println("  Statement is not ready yet.\n");
        }
    }

    private static void handleCancelStatementJob(Scanner scanner, Session session) {
        StatementJob job = promptForStatementJob(scanner, session, "Select statement job to cancel");
        if (job == null) {
            return;
        }

        boolean cancelled = StatementQueueService.getInstance().cancelJob(job.getId(), session.getUserId());
        if (cancelled) {
            System.out.println("  Statement job cancelled.\n");
        } else {
            System.out.println("  Could not cancel job (not found or already finished).\n");
        }
    }

    private static void handleViewBalances(Session session) {
        try {
            List<BankAccount> accounts = AccountManager.getAccountsForUser(session.getUserId());
            if (accounts.isEmpty()) {
                System.out.println("\n  No accounts found. Open an account first.\n");
                return;
            }

            System.out.println("\n── Your Accounts ─────────────────────");
            for (BankAccount account : accounts) {
                System.out.println("  ID: " + account.getId() +
                        " | Type: " + account.getAccountType() +
                        " | Balance: " + account.getBalance().toPlainString());
            }
            System.out.println();
        } catch (BankingError e) {
            System.out.println("\n  Error retrieving balances: " + e.getMessage() + "\n");
        }
    }

    private static void handleDeposit(Scanner scanner, Session session) {
        try {
            BankAccount account = promptForOwnedAccount(scanner, session, "Select account for deposit");
            if (account == null) return;

            BigDecimal amount = promptForPositiveAmount(scanner, "Amount to deposit: ");
            if (amount == null) return;

            Transaction tx = AccountManager.deposit(account.getId(), amount);
            System.out.println("\n  Deposit successful. Transaction ID: " + tx.getId() + "\n");
        } catch (BankingError e) {
            System.out.println("\n  Deposit failed: " + e.getMessage() + "\n");
        }
    }

    private static void handleWithdraw(Scanner scanner, Session session) {
        try {
            BankAccount account = promptForOwnedAccount(scanner, session, "Select account for withdrawal");
            if (account == null) return;

            BigDecimal amount = promptForPositiveAmount(scanner, "Amount to withdraw: ");
            if (amount == null) return;

            Transaction tx = AccountManager.withdraw(account.getId(), amount);
            System.out.println("\n  Withdrawal successful. Transaction ID: " + tx.getId() + "\n");
        } catch (BankingError e) {
            System.out.println("\n  Withdrawal failed: " + e.getMessage() + "\n");
        }
    }

    private static void handleTransfer(Scanner scanner, Session session) {
        try {
            BankAccount from = promptForOwnedAccount(scanner, session, "Select source account");
            if (from == null) return;

            BankAccount to = promptForOwnedAccount(scanner, session, "Select destination account");
            if (to == null) return;

            if (from.getId().equals(to.getId())) {
                System.out.println("\n  Cannot transfer to the same account.\n");
                return;
            }

            BigDecimal amount = promptForPositiveAmount(scanner, "Amount to transfer: ");
            if (amount == null) return;

            Transaction tx = AccountManager.transfer(from.getId(), to.getId(), amount);
            System.out.println("\n  Transfer successful. Transaction ID: " + tx.getId() + "\n");
        } catch (BankingError e) {
            System.out.println("\n  Transfer failed: " + e.getMessage() + "\n");
        }
    }

    private static void handleTransactionHistory(Scanner scanner, Session session) {
        try {
            BankAccount account = promptForOwnedAccount(scanner, session, "Select account for transaction history");
            if (account == null) return;

            List<Transaction> history = AccountManager.getTransactionHistory(account.getId());
            if (history.isEmpty()) {
                System.out.println("\n  No transactions found for this account.\n");
                return;
            }

            System.out.println("\n── Transaction History ───────────────");
            for (Transaction tx : history) {
                String from = tx.getFromAccountId() == null ? "-" : tx.getFromAccountId();
                String to = tx.getToAccountId() == null ? "-" : tx.getToAccountId();
                System.out.println("  " + tx.getCreatedAt() + " | " + tx.getType() + " | " +
                        tx.getAmount().toPlainString() + " | from=" + from + " | to=" + to);
            }
            System.out.println();
        } catch (BankingError e) {
            System.out.println("\n  Could not load transaction history: " + e.getMessage() + "\n");
        }
    }

    private static void handleOpenAccount(Scanner scanner, Session session) {
        System.out.println("\n── Open Account ─────────────────────");
        BankAccount.AccountType accountType = promptForAccountType(scanner);
        if (accountType == null) {
            return;
        }

        try {
            BankAccount account = AccountManager.openAccount(session.getUserId(), accountType);
            System.out.println("  Account opened successfully. New account ID: " + account.getId() + "\n");
        } catch (BankingError e) {
            System.out.println("  Could not open account: " + e.getMessage() + "\n");
        }
    }

    private static StatementJob promptForStatementJob(Scanner scanner, Session session, String prompt) {
        List<StatementJob> jobs = StatementQueueService.getInstance().listJobsForUser(session.getUserId());
        if (jobs.isEmpty()) {
            System.out.println("\n  No statement jobs found yet.\n");
            return null;
        }

        System.out.println("\n  " + prompt + ":");
        for (int i = 0; i < jobs.size(); i++) {
            StatementJob job = jobs.get(i);
            System.out.println("  [" + (i + 1) + "] " +
                    "status=" + job.getStatus() +
                    " | requested=" + job.getRequestedAt() +
                    " | account=" + job.getAccountId());
        }

        System.out.print("  Choose job number: ");
        String selection = readTrimmedLine(scanner);
        if (selection == null) return null;
        if (selection.isEmpty()) {
            System.out.println("  Selection cannot be empty.\n");
            return null;
        }

        try {
            int index = Integer.parseInt(selection);
            if (index < 1 || index > jobs.size()) {
                System.out.println("  Invalid selection. Choose a number from 1 to " + jobs.size() + ".\n");
                return null;
            }
            return jobs.get(index - 1);
        } catch (NumberFormatException e) {
            System.out.println("  Invalid selection format. Enter the number shown in the list.\n");
            return null;
        }
    }

    private static BankAccount.AccountType promptForAccountType(Scanner scanner) {
        BankAccount.AccountType[] types = BankAccount.AccountType.values();

        System.out.println("  Select account type:");
        for (int i = 0; i < types.length; i++) {
            System.out.println("  [" + (i + 1) + "] " + types[i]);
        }

        System.out.print("  Choose account type number: ");
        String selection = readTrimmedLine(scanner);
        if (selection == null) return null;
        if (selection.isEmpty()) {
            System.out.println("  Selection cannot be empty.\n");
            return null;
        }

        try {
            int index = Integer.parseInt(selection);
            if (index < 1 || index > types.length) {
                System.out.println("  Invalid selection. Choose a number from 1 to " + types.length + ".\n");
                return null;
            }
            return types[index - 1];
        } catch (NumberFormatException e) {
            System.out.println("  Invalid selection format. Enter the number shown in the list.\n");
            return null;
        }
    }

    private static void handleCloseAccount(Scanner scanner, Session session) {
        try {
            BankAccount account = promptForOwnedAccount(scanner, session, "Select account to close");
            if (account == null) return;

            AccountManager.closeAccount(account.getId());
            System.out.println("\n  Account closed successfully.\n");
        } catch (BankingError e) {
            System.out.println("\n  Could not close account: " + e.getMessage() + "\n");
        }
    }

    private static BankAccount promptForOwnedAccount(Scanner scanner, Session session, String prompt) throws BankingError {
        List<BankAccount> accounts = AccountManager.getAccountsForUser(session.getUserId());
        if (accounts.isEmpty()) {
            System.out.println("\n  No accounts found. Open an account first.\n");
            return null;
        }

        System.out.println("\n  " + prompt + ":");
        for (int i = 0; i < accounts.size(); i++) {
            BankAccount account = accounts.get(i);
            System.out.println("  [" + (i + 1) + "] " + account.getAccountType() +
                    " | balance=" + account.getBalance().toPlainString() +
                    " | id=" + account.getId());
        }

        System.out.print("  Choose account number: ");
        String selection = readTrimmedLine(scanner);
        if (selection == null) return null;
        if (selection.isEmpty()) {
            System.out.println("  Selection cannot be empty.\n");
            return null;
        }

        try {
            int index = Integer.parseInt(selection);
            if (index < 1 || index > accounts.size()) {
                System.out.println("  Invalid selection. Choose a number from 1 to " + accounts.size() + ".\n");
                return null;
            }
            return accounts.get(index - 1);
        } catch (NumberFormatException e) {
            System.out.println("  Invalid selection format. Enter the number shown in the list.\n");
            return null;
        }
    }

    private static BigDecimal promptForPositiveAmount(Scanner scanner, String prompt) {
        System.out.print("  " + prompt);
        String rawAmount = readTrimmedLine(scanner);
        if (rawAmount == null) return null;

        try {
            BigDecimal amount = new BigDecimal(rawAmount);
            if (amount.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("  Amount must be positive.\n");
                return null;
            }
            return amount;
        } catch (NumberFormatException e) {
            System.out.println("  Invalid amount format.\n");
            return null;
        }
    }

    private static String readTrimmedLine(Scanner scanner) {
        if (!scanner.hasNextLine()) {
            return null;
        }
        return scanner.nextLine().trim();
    }
}