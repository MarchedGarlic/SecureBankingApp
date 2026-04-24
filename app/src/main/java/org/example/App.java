package org.example;

import java.math.BigDecimal;
import java.util.List;
import java.util.Scanner;

import org.example.authentication.AuthenticationError;
import org.example.authentication.LoginService;
import org.example.authentication.LoginService.SessionLength;
import org.example.authentication.Session;
import org.example.banking.AccountManager;
import org.example.banking.BankAccount;
import org.example.banking.BankingError;
import org.example.banking.Transaction;
import org.example.cryptography.EncryptionError;
import org.example.cryptography.UserManager;

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

        printBanner();

        while (running) {
            printMainMenu();
            String choice = scanner.nextLine().trim();

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

        scanner.close();
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
        System.out.println("  [1] View balance");
        System.out.println("  [2] Deposit");
        System.out.println("  [3] Withdraw");
        System.out.println("  [4] Transfer funds");
        System.out.println("  [5] Transaction history");
        System.out.println("  [6] Logout");
        System.out.print("\n  Choose an option: ");
    }

    private static void handleCreateAccount(Scanner scanner) {
        System.out.println("\n── Create Account ─────────────────────");

        System.out.print("  Username: ");
        String username = scanner.nextLine().trim();

        System.out.print("  Password: ");
        String password = scanner.nextLine().trim();

        System.out.print("  Confirm password: ");
        String confirm = scanner.nextLine().trim();

        if (!password.equals(confirm)) {
            System.out.println("  Error: passwords do not match.\n");
            return;
        }

        if (username.isEmpty() || password.isEmpty()) {
            System.out.println("  Error: username and password cannot be empty.\n");
            return;
        }

        try {
            // Create the user
            org.example.cryptography.User user = UserManager.newUser(username, password);
            System.out.println("  Account created successfully!");
            
            // Auto-create checking and savings accounts
            System.out.println("  Creating checking account...");
            AccountManager.openAccount(user.getId(), BankAccount.AccountType.CHECKING);
            
            System.out.println("  Creating savings account...");
            AccountManager.openAccount(user.getId(), BankAccount.AccountType.SAVINGS);
            
            System.out.println("  You can now log in with your new accounts.\n");
        } catch (EncryptionError e) {
            System.out.println("  Error creating account: " + e.getMessage() + "\n");
        } catch (BankingError e) {
            System.out.println("  Account created but error setting up banking: " + e.getMessage() + "\n");
        }
    }

    private static void handleLogin(Scanner scanner) {
        System.out.println("\n── Login ───────────────────────────────");

        System.out.print("  Username: ");
        String username = scanner.nextLine().trim();

        System.out.print("  Password: ");
        String password = scanner.nextLine().trim();

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
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> handleViewBalance(session);
                case "2" -> {
                    try {
                        List<BankAccount> accounts = AccountManager.getAccountsForUser(session.getUserId());
                        handleDeposit(scanner, accounts);
                    } catch (BankingError e) {
                        System.out.println("\n  Error: " + e.getMessage() + "\n");
                    }
                }
                case "3" -> {
                    try {
                        List<BankAccount> accounts = AccountManager.getAccountsForUser(session.getUserId());
                        handleWithdraw(scanner, accounts);
                    } catch (BankingError e) {
                        System.out.println("\n  Error: " + e.getMessage() + "\n");
                    }
                }
                case "4" -> {
                    try {
                        List<BankAccount> accounts = AccountManager.getAccountsForUser(session.getUserId());
                        if (accounts.size() >= 2) {
                            handleTransfer(scanner, accounts);
                        } else {
                            System.out.println("\n  You need at least 2 accounts to transfer between them.\n");
                        }
                    } catch (BankingError e) {
                        System.out.println("\n  Error: " + e.getMessage() + "\n");
                    }
                }
                case "5" -> handleTransactionHistory(session);
                case "6" -> {
                    try {
                        LoginService.logout(session.getToken());
                    } catch (AuthenticationError e) {
                        System.out.println("  Warning: logout encountered an error: " + e.getMessage());
                    }
                    System.out.println("\n  Logged out successfully.\n");
                    loggedIn = false;
                }
                default -> System.out.println("  Invalid option. Please enter 1–6.\n");
            }
        }
    }

    private static void handleViewBalance(Session session) {
        try {
            List<BankAccount> accounts = AccountManager.getAccountsForUser(session.getUserId());
            System.out.println("\n── Your Accounts ───────────────────────");
            BigDecimal totalBalance = BigDecimal.ZERO;
            for (BankAccount account : accounts) {
                System.out.println("  " + account.getAccountType() + ": $" + account.getBalance().toPlainString());
                totalBalance = totalBalance.add(account.getBalance());
            }
            System.out.println("  ─────────────────────────────────────");
            System.out.println("  Total: $" + totalBalance.toPlainString());
            System.out.println();
        } catch (BankingError e) {
            System.out.println("\n  Error retrieving balance: " + e.getMessage() + "\n");
        }
    }

    private static void handleDeposit(Scanner scanner, List<BankAccount> accounts) throws BankingError {
        System.out.println("\n── Deposit ──────────────────────────────");
        System.out.println("  Your accounts:");
        for (int i = 0; i < accounts.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + accounts.get(i).getAccountType());
        }
        System.out.print("\n  Select account (1-" + accounts.size() + "): ");
        int index = Integer.parseInt(scanner.nextLine().trim()) - 1;
        if (index < 0 || index >= accounts.size()) {
            System.out.println("  Invalid selection.\n");
            return;
        }
        System.out.print("  Amount: $");
        BigDecimal amount = new BigDecimal(scanner.nextLine().trim());
        Transaction tx = AccountManager.deposit(accounts.get(index).getId(), amount);
        System.out.println("\n  Deposit successful! New balance: $" + AccountManager.getAccount(accounts.get(index).getId()).getBalance().toPlainString() + "\n");
    }

    private static void handleWithdraw(Scanner scanner, List<BankAccount> accounts) throws BankingError {
        System.out.println("\n── Withdraw ─────────────────────────────");
        System.out.println("  Your accounts:");
        for (int i = 0; i < accounts.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + accounts.get(i).getAccountType() + " - $" + accounts.get(i).getBalance().toPlainString());
        }
        System.out.print("\n  Select account (1-" + accounts.size() + "): ");
        int index = Integer.parseInt(scanner.nextLine().trim()) - 1;
        if (index < 0 || index >= accounts.size()) {
            System.out.println("  Invalid selection.\n");
            return;
        }
        System.out.print("  Amount: $");
        BigDecimal amount = new BigDecimal(scanner.nextLine().trim());
        Transaction tx = AccountManager.withdraw(accounts.get(index).getId(), amount);
        System.out.println("\n  Withdrawal successful! New balance: $" + AccountManager.getAccount(accounts.get(index).getId()).getBalance().toPlainString() + "\n");
    }

    private static void handleTransfer(Scanner scanner, List<BankAccount> accounts) throws BankingError {
        System.out.println("\n── Transfer ─────────────────────────────");
        System.out.println("  Your accounts:");
        for (int i = 0; i < accounts.size(); i++) {
            System.out.println("  [" + (i + 1) + "] " + accounts.get(i).getAccountType() + " - $" + accounts.get(i).getBalance().toPlainString());
        }

        System.out.print("\n  From account (1-" + accounts.size() + "): ");
        int fromIndex = Integer.parseInt(scanner.nextLine().trim()) - 1;
        if (fromIndex < 0 || fromIndex >= accounts.size()) {
            System.out.println("  Invalid selection.\n");
            return;
        }

        System.out.print("  To account (1-" + accounts.size() + "): ");
        int toIndex = Integer.parseInt(scanner.nextLine().trim()) - 1;
        if (toIndex < 0 || toIndex >= accounts.size()) {
            System.out.println("  Invalid selection.\n");
            return;
        }

        if (fromIndex == toIndex) {
            System.out.println("  Cannot transfer to the same account.\n");
            return;
        }

        System.out.print("  Amount: $");
        BigDecimal amount = new BigDecimal(scanner.nextLine().trim());

        Transaction tx = AccountManager.transfer(accounts.get(fromIndex).getId(), accounts.get(toIndex).getId(), amount);
        System.out.println("\n  Transfer successful!\n");
    }

    private static void handleTransactionHistory(Session session) {
        try {
            List<BankAccount> accounts = AccountManager.getAccountsForUser(session.getUserId());
            if (accounts.isEmpty()) {
                System.out.println("\n  You have no accounts.\n");
                return;
            }

            System.out.println("\n── Transaction History ────────────────");
            for (BankAccount account : accounts) {
                List<Transaction> transactions = AccountManager.getTransactionHistory(account.getId());
                System.out.println("  " + account.getAccountType() + " (" + account.getId().substring(0, 8) + "...):");
                if (transactions.isEmpty()) {
                    System.out.println("    No transactions.");
                } else {
                    for (Transaction tx : transactions) {
                        System.out.println("    [" + tx.getType() + "] $" + tx.getAmount().toPlainString() +
                                " at " + tx.getCreatedAt());
                    }
                }
            }
            System.out.println();
        } catch (BankingError e) {
            System.out.println("\n  Error retrieving transaction history: " + e.getMessage() + "\n");
        }
    }

}