package org.example;

import org.example.authentication.AuthenticationError;
import org.example.authentication.LoginService;
import org.example.authentication.LoginService.SessionLength;
import org.example.authentication.Session;
import org.example.cryptography.EncryptionError;
import org.example.cryptography.UserManager;

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

    // -------------------------------------------------------------------------
    // Menu rendering
    // -------------------------------------------------------------------------

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
        System.out.println("  [1] View balance          (coming soon)");
        System.out.println("  [2] Transfer funds        (coming soon)");
        System.out.println("  [3] Transaction history   (coming soon)");
        System.out.println("  [4] Account settings      (coming soon)");
        System.out.println("  [5] Logout");
        System.out.print("\n  Choose an option: ");
    }

    // -------------------------------------------------------------------------
    // Create account flow
    // -------------------------------------------------------------------------

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
            // Accounts are persisted to bank.db via UserManager (SQLite)
            UserManager.newUser(username, password);
            System.out.println("  Account created successfully! You can now log in.\n");
        } catch (EncryptionError e) {
            System.out.println("  Error creating account: " + e.getMessage() + "\n");
        }
    }

    // -------------------------------------------------------------------------
    // Login flow
    // -------------------------------------------------------------------------

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

    // -------------------------------------------------------------------------
    // Post-login account menu
    // -------------------------------------------------------------------------

    private static void handleAccountMenu(Scanner scanner, Session session) {
        boolean loggedIn = true;

        while (loggedIn) {
            // Validate the session is still active before showing the menu
            try {
                session = LoginService.validateAndRefresh(session.getToken(), SessionLength.STANDARD);
            } catch (AuthenticationError e) {
                System.out.println("\n  Session expired. Please log in again.\n");
                return;
            }

            printAccountMenu(session);
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> System.out.println("\n  View balance — coming soon.\n");
                case "2" -> System.out.println("\n  Transfer funds — coming soon.\n");
                case "3" -> System.out.println("\n  Transaction history — coming soon.\n");
                case "4" -> System.out.println("\n  Account settings — coming soon.\n");
                case "5" -> {
                    try {
                        LoginService.logout(session.getToken());
                    } catch (AuthenticationError e) {
                        System.out.println("  Warning: logout encountered an error: " + e.getMessage());
                    }
                    System.out.println("\n  Logged out successfully.\n");
                    loggedIn = false;
                }
                default -> System.out.println("  Invalid option. Please enter 1–5.\n");
            }
        }
    }
}
