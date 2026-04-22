package org.example;

import java.math.BigDecimal;
import java.util.List;

import org.example.authentication.AuthKey;
import org.example.authentication.AuthService;
import org.example.banking.AccountManager;
import org.example.banking.BankAccount;
import org.example.banking.Transaction;
import org.example.cryptography.User;
import org.example.cryptography.UserManager;

public class App {
    public static void main(String[] args) throws Exception {
        // --- User registration ---
        System.out.println("=== Registering users ===");
        User alice = UserManager.newUser("alice", "s3cur3P@ss!");
        User bob   = UserManager.newUser("bob",   "hunter2!");
        System.out.println("Registered: " + alice.getUsername() + ", " + bob.getUsername());

        // --- Authentication ---
        System.out.println("\n=== Authentication ===");
        User aliceLogin = UserManager.loadUser("alice", "s3cur3P@ss!");
        AuthKey aliceKey = AuthService.generateKey();
        System.out.println("Alice logged in. Token valid: " + AuthService.validateKey(aliceKey));

        // --- Open accounts ---
        System.out.println("\n=== Opening accounts ===");
        BankAccount aliceChecking = AccountManager.openAccount(aliceLogin.getId(), BankAccount.AccountType.CHECKING);
        BankAccount aliceSavings  = AccountManager.openAccount(aliceLogin.getId(), BankAccount.AccountType.SAVINGS);
        BankAccount bobChecking   = AccountManager.openAccount(bob.getId(), BankAccount.AccountType.CHECKING);
        System.out.println("Alice: " + aliceChecking);
        System.out.println("Alice: " + aliceSavings);
        System.out.println("Bob:   " + bobChecking);

        // --- Deposit ---
        System.out.println("\n=== Deposits ===");
        AccountManager.deposit(aliceChecking.getId(), new BigDecimal("1000.00"));
        AccountManager.deposit(bobChecking.getId(),   new BigDecimal("500.00"));
        System.out.println("Alice checking after deposit: " + AccountManager.getAccount(aliceChecking.getId()));
        System.out.println("Bob checking after deposit:   " + AccountManager.getAccount(bobChecking.getId()));

        // --- Withdraw ---
        System.out.println("\n=== Withdrawal ===");
        AccountManager.withdraw(aliceChecking.getId(), new BigDecimal("200.00"));
        System.out.println("Alice checking after withdrawal: " + AccountManager.getAccount(aliceChecking.getId()));

        // --- Transfer ---
        System.out.println("\n=== Transfer ===");
        AccountManager.transfer(aliceChecking.getId(), bobChecking.getId(), new BigDecimal("150.00"));
        System.out.println("Alice checking after transfer: " + AccountManager.getAccount(aliceChecking.getId()));
        System.out.println("Bob checking after transfer:   " + AccountManager.getAccount(bobChecking.getId()));

        // --- Transaction history ---
        System.out.println("\n=== Alice's transaction history ===");
        List<Transaction> history = AccountManager.getTransactionHistory(aliceChecking.getId());
        history.forEach(System.out::println);

        // --- Insufficient funds ---
        System.out.println("\n=== Insufficient funds test ===");
        try {
            AccountManager.withdraw(bobChecking.getId(), new BigDecimal("99999.00"));
        } catch (Exception e) {
            System.out.println("Caught expected error: " + e.getMessage());
        }

        // --- Session invalidation ---
        System.out.println("\n=== Session invalidation ===");
        AuthService.invalidateKey(aliceKey);
        System.out.println("Token valid after invalidation: " + AuthService.validateKey(aliceKey));

        // --- Cleanup ---
        UserManager.deleteUser("alice");
        UserManager.deleteUser("bob");
    }
}
