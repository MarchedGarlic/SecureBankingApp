package org.example.banking;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class AccountManager {
    private static final String DB_ADAPTER = "jdbc:sqlite:bank.db";

    private static void initTables() throws BankingError {
        try (Connection conn = DriverManager.getConnection(DB_ADAPTER);
             Statement stat = conn.createStatement()) {
            stat.executeUpdate("""
                CREATE TABLE IF NOT EXISTS accounts (
                    id STRING PRIMARY KEY,
                    owner_id STRING NOT NULL,
                    account_type STRING NOT NULL,
                    balance TEXT NOT NULL
                );
            """);
            stat.executeUpdate("""
                CREATE TABLE IF NOT EXISTS transactions (
                    id STRING PRIMARY KEY,
                    from_account_id STRING,
                    to_account_id STRING,
                    amount TEXT NOT NULL,
                    type STRING NOT NULL,
                    created_at DATETIME NOT NULL
                );
            """);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BankingError("Error initializing banking tables");
        }
    }

    /**
     * Opens a new bank account for the given user.
     */
    public static BankAccount openAccount(String ownerId, BankAccount.AccountType accountType) throws BankingError {
        Objects.requireNonNull(ownerId, "Owner ID cannot be null");
        Objects.requireNonNull(accountType, "Account type cannot be null");

        initTables();

        BankAccount account = new BankAccount(
                UUID.randomUUID().toString(),
                ownerId,
                accountType,
                BigDecimal.ZERO
        );

        try (Connection conn = DriverManager.getConnection(DB_ADAPTER);
             PreparedStatement pstmt = conn.prepareStatement(
                     "INSERT INTO accounts (id, owner_id, account_type, balance) VALUES (?, ?, ?, ?);")) {
            pstmt.setString(1, account.getId());
            pstmt.setString(2, account.getOwnerId());
            pstmt.setString(3, account.getAccountType().name());
            pstmt.setString(4, account.getBalance().toPlainString());
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BankingError("Error opening account");
        }

        return account;
    }

    /**
     * Retrieves a bank account by its ID.
     */
    public static BankAccount getAccount(String accountId) throws BankingError {
        Objects.requireNonNull(accountId, "Account ID cannot be null");

        initTables();

        try (Connection conn = DriverManager.getConnection(DB_ADAPTER);
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT * FROM accounts WHERE id=?;")) {
            pstmt.setString(1, accountId);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) throw new BankingError("Account not found");
            return rowToAccount(rs);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BankingError("Error retrieving account");
        }
    }

    /**
     * Lists all accounts belonging to a user.
     */
    public static List<BankAccount> getAccountsForUser(String ownerId) throws BankingError {
        Objects.requireNonNull(ownerId, "Owner ID cannot be null");

        initTables();

        List<BankAccount> accounts = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_ADAPTER);
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT * FROM accounts WHERE owner_id=?;")) {
            pstmt.setString(1, ownerId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) accounts.add(rowToAccount(rs));
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BankingError("Error retrieving accounts");
        }

        return accounts;
    }

    /**
     * Closes (deletes) a bank account. The balance must be zero.
     */
    public static void closeAccount(String accountId) throws BankingError {
        Objects.requireNonNull(accountId, "Account ID cannot be null");

        BankAccount account = getAccount(accountId);
        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new BankingError("Cannot close account with non-zero balance");
        }

        try (Connection conn = DriverManager.getConnection(DB_ADAPTER);
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM accounts WHERE id=?;")) {
            pstmt.setString(1, accountId);
            pstmt.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BankingError("Error closing account");
        }
    }

    /**
     * Deposits a positive amount into the given account.
     */
    public static Transaction deposit(String accountId, BigDecimal amount) throws BankingError {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new BankingError("Deposit amount must be positive");

        initTables();

        try (Connection conn = DriverManager.getConnection(DB_ADAPTER)) {
            conn.setAutoCommit(false);
            try {
                BigDecimal newBalance = getBalanceForUpdate(conn, accountId).add(amount);
                updateBalance(conn, accountId, newBalance);
                Transaction tx = recordTransaction(conn, null, accountId, amount, Transaction.TransactionType.DEPOSIT);
                conn.commit();
                return tx;
            } catch (SQLException | BankingError e) {
                conn.rollback();
                throw e instanceof BankingError be ? be : new BankingError("Error processing deposit");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BankingError("Error processing deposit");
        }
    }

    /**
     * Withdraws a positive amount from the given account. Throws if insufficient funds.
     */
    public static Transaction withdraw(String accountId, BigDecimal amount) throws BankingError {
        Objects.requireNonNull(accountId, "Account ID cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new BankingError("Withdrawal amount must be positive");

        initTables();

        try (Connection conn = DriverManager.getConnection(DB_ADAPTER)) {
            conn.setAutoCommit(false);
            try {
                BigDecimal currentBalance = getBalanceForUpdate(conn, accountId);
                if (currentBalance.compareTo(amount) < 0) throw new BankingError("Insufficient funds");
                BigDecimal newBalance = currentBalance.subtract(amount);
                updateBalance(conn, accountId, newBalance);
                Transaction tx = recordTransaction(conn, accountId, null, amount, Transaction.TransactionType.WITHDRAWAL);
                conn.commit();
                return tx;
            } catch (SQLException | BankingError e) {
                conn.rollback();
                throw e instanceof BankingError be ? be : new BankingError("Error processing withdrawal");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BankingError("Error processing withdrawal");
        }
    }

    /**
     * Transfers a positive amount from one account to another atomically.
     */
    public static Transaction transfer(String fromAccountId, String toAccountId, BigDecimal amount) throws BankingError {
        Objects.requireNonNull(fromAccountId, "From account ID cannot be null");
        Objects.requireNonNull(toAccountId, "To account ID cannot be null");
        Objects.requireNonNull(amount, "Amount cannot be null");
        if (fromAccountId.equals(toAccountId)) throw new BankingError("Cannot transfer to the same account");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) throw new BankingError("Transfer amount must be positive");

        initTables();

        try (Connection conn = DriverManager.getConnection(DB_ADAPTER)) {
            conn.setAutoCommit(false);
            try {
                BigDecimal fromBalance = getBalanceForUpdate(conn, fromAccountId);
                if (fromBalance.compareTo(amount) < 0) throw new BankingError("Insufficient funds");
                BigDecimal toBalance = getBalanceForUpdate(conn, toAccountId);
                updateBalance(conn, fromAccountId, fromBalance.subtract(amount));
                updateBalance(conn, toAccountId, toBalance.add(amount));
                Transaction tx = recordTransaction(conn, fromAccountId, toAccountId, amount, Transaction.TransactionType.TRANSFER);
                conn.commit();
                return tx;
            } catch (SQLException | BankingError e) {
                conn.rollback();
                throw e instanceof BankingError be ? be : new BankingError("Error processing transfer");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BankingError("Error processing transfer");
        }
    }

    /**
     * Returns the transaction history for a given account.
     */
    public static List<Transaction> getTransactionHistory(String accountId) throws BankingError {
        Objects.requireNonNull(accountId, "Account ID cannot be null");

        initTables();

        List<Transaction> transactions = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(DB_ADAPTER);
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT * FROM transactions WHERE from_account_id=? OR to_account_id=? ORDER BY created_at DESC;")) {
            pstmt.setString(1, accountId);
            pstmt.setString(2, accountId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) transactions.add(rowToTransaction(rs));
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BankingError("Error retrieving transaction history");
        }

        return transactions;
    }

    // --- Private helpers ---

    private static BigDecimal getBalanceForUpdate(Connection conn, String accountId) throws SQLException, BankingError {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT balance FROM accounts WHERE id=?;")) {
            pstmt.setString(1, accountId);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) throw new BankingError("Account not found: " + accountId);
            return new BigDecimal(rs.getString("balance"));
        }
    }

    private static void updateBalance(Connection conn, String accountId, BigDecimal newBalance) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE accounts SET balance=? WHERE id=?;")) {
            pstmt.setString(1, newBalance.toPlainString());
            pstmt.setString(2, accountId);
            pstmt.executeUpdate();
        }
    }

    private static Transaction recordTransaction(Connection conn, String fromAccountId, String toAccountId,
                                                  BigDecimal amount, Transaction.TransactionType type) throws SQLException {
        String id = UUID.randomUUID().toString();
        Instant now = Instant.now();
        try (PreparedStatement pstmt = conn.prepareStatement(
                "INSERT INTO transactions (id, from_account_id, to_account_id, amount, type, created_at) VALUES (?, ?, ?, ?, ?, ?);")) {
            pstmt.setString(1, id);
            pstmt.setString(2, fromAccountId);
            pstmt.setString(3, toAccountId);
            pstmt.setString(4, amount.toPlainString());
            pstmt.setString(5, type.name());
            pstmt.setTimestamp(6, java.sql.Timestamp.from(now));
            pstmt.executeUpdate();
        }
        return new Transaction(id, fromAccountId, toAccountId, amount, type, now);
    }

    private static BankAccount rowToAccount(ResultSet rs) throws SQLException {
        return new BankAccount(
                rs.getString("id"),
                rs.getString("owner_id"),
                BankAccount.AccountType.valueOf(rs.getString("account_type")),
                new BigDecimal(rs.getString("balance"))
        );
    }

    private static Transaction rowToTransaction(ResultSet rs) throws SQLException {
        return new Transaction(
                rs.getString("id"),
                rs.getString("from_account_id"),
                rs.getString("to_account_id"),
                new BigDecimal(rs.getString("amount")),
                Transaction.TransactionType.valueOf(rs.getString("type")),
                rs.getTimestamp("created_at").toInstant()
        );
    }

    private AccountManager() {}
}
