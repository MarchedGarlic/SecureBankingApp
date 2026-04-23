package org.example.banking;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.example.db.DatabaseManager;

public class AccountManager {
    /**
     * Uses DatabaseManager so every DB connection applies secure file permissions.
     */

    private static void initTables() throws BankingError {
        try (Connection conn = DatabaseManager.getConnection();
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

        BankAccount account = createAccount(UUID.randomUUID().toString(), ownerId, accountType, BigDecimal.ZERO);

        try (Connection conn = DatabaseManager.getConnection();
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

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT * FROM accounts WHERE id=?;")) {
            pstmt.setString(1, accountId);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) throw new BankingError("Account not found");
            return rowToAccount(rs);
        } catch (SQLException | BankingError e) {
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
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT * FROM accounts WHERE owner_id=?;")) {
            pstmt.setString(1, ownerId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) accounts.add(rowToAccount(rs));
        } catch (SQLException | BankingError e) {
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

        BankAccount currentAccount;
        try (Connection conn = DatabaseManager.getConnection()) {
            currentAccount = getAccountForUpdate(conn, accountId);
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BankingError("Error closing account");
        }

        BankAccount snapshot = currentAccount.clone();
        BigDecimal currentBalance = snapshot.getBalance();

        if (currentBalance.compareTo(BigDecimal.ZERO) != 0) {
            throw new BankingError("Cannot close account with non-zero balance");
        }

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "DELETE FROM accounts WHERE id=? AND balance=?;")) {
            pstmt.setString(1, accountId);
            pstmt.setString(2, currentBalance.toPlainString());
            int affectedRows = pstmt.executeUpdate();
            if (affectedRows == 0) {
                throw new BankingError("Account state changed concurrently; close aborted");
            }
        } catch (SQLException e) {
            e.printStackTrace();
            throw new BankingError("Error closing account");
        } catch (BankingError e) {
            throw e;
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

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BankAccount currentAccount = getAccountForUpdate(conn, accountId);
                BankAccount workingCopy = currentAccount.clone();
                BigDecimal currentBalance = workingCopy.getBalance();
                workingCopy.setBalance(currentBalance.add(amount));
                int affectedRows = updateBalanceIfUnchanged(conn, accountId, currentBalance, workingCopy.getBalance());
                if (affectedRows == 0) {
                    throw new BankingError("Account state changed concurrently; deposit aborted");
                }
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

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BankAccount currentAccount = getAccountForUpdate(conn, accountId);
                BankAccount workingCopy = currentAccount.clone();
                BigDecimal currentBalance = workingCopy.getBalance();
                if (currentBalance.compareTo(amount) < 0) throw new BankingError("Insufficient funds");
                workingCopy.setBalance(currentBalance.subtract(amount));
                int affectedRows = updateBalanceIfUnchanged(conn, accountId, currentBalance, workingCopy.getBalance());
                if (affectedRows == 0) {
                    throw new BankingError("Account state changed concurrently; withdrawal aborted");
                }
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

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                BankAccount fromAccount = getAccountForUpdate(conn, fromAccountId);
                BankAccount fromWorkingCopy = fromAccount.clone();
                BigDecimal fromBalance = fromWorkingCopy.getBalance();
                if (fromBalance.compareTo(amount) < 0) throw new BankingError("Insufficient funds");
                BankAccount toAccount = getAccountForUpdate(conn, toAccountId);
                BankAccount toWorkingCopy = toAccount.clone();
                BigDecimal toBalance = toWorkingCopy.getBalance();

                fromWorkingCopy.setBalance(fromBalance.subtract(amount));
                toWorkingCopy.setBalance(toBalance.add(amount));

                int fromRows = updateBalanceIfUnchanged(conn, fromAccountId, fromBalance, fromWorkingCopy.getBalance());
                if (fromRows == 0) {
                    throw new BankingError("Source account state changed concurrently; transfer aborted");
                }

                int toRows = updateBalanceIfUnchanged(conn, toAccountId, toBalance, toWorkingCopy.getBalance());
                if (toRows == 0) {
                    throw new BankingError("Destination account state changed concurrently; transfer aborted");
                }

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
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(
                     "SELECT * FROM transactions WHERE from_account_id=? OR to_account_id=? ORDER BY created_at DESC;")) {
            pstmt.setString(1, accountId);
            pstmt.setString(2, accountId);
            ResultSet rs = pstmt.executeQuery();
            while (rs.next()) transactions.add(rowToTransaction(rs));
        } catch (SQLException | BankingError e) {
            e.printStackTrace();
            throw new BankingError("Error retrieving transaction history");
        }

        return transactions;
    }

    // --- Private helpers ---

    private static BankAccount getAccountForUpdate(Connection conn, String accountId) throws SQLException, BankingError {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT * FROM accounts WHERE id=?;")) {
            pstmt.setString(1, accountId);
            ResultSet rs = pstmt.executeQuery();
            if (!rs.next()) throw new BankingError("Account not found: " + accountId);
            return rowToAccount(rs);
        }
    }

    private static int updateBalanceIfUnchanged(Connection conn, String accountId,
                                                BigDecimal expectedCurrentBalance,
                                                BigDecimal newBalance) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "UPDATE accounts SET balance=? WHERE id=? AND balance=?;")) {
            pstmt.setString(1, newBalance.toPlainString());
            pstmt.setString(2, accountId);
            pstmt.setString(3, expectedCurrentBalance.toPlainString());
            return pstmt.executeUpdate();
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

    private static BankAccount rowToAccount(ResultSet rs) throws SQLException, BankingError {
        String accountId = rs.getString("id");
        return createAccount(
                accountId,
                rs.getString("owner_id"),
                parseAccountType(rs.getString("account_type"), accountId),
                parseAmount(rs.getString("balance"), "accounts.balance", accountId)
        );
    }

    private static BankAccount createAccount(String id, String ownerId, BankAccount.AccountType accountType,
                                             BigDecimal balance) throws BankingError {
        return switch (accountType) {
            case CHECKING -> new CheckingAccount(id, ownerId, balance);
            case SAVINGS -> new SavingsAccount(id, ownerId, balance);
        };
    }

    private static Transaction rowToTransaction(ResultSet rs) throws SQLException, BankingError {
        return new Transaction(
                rs.getString("id"),
                rs.getString("from_account_id"),
                rs.getString("to_account_id"),
                parseAmount(rs.getString("amount"), "transactions.amount", rs.getString("id")),
                parseTransactionType(rs.getString("type"), rs.getString("id")),
                parseCreatedAt(rs, rs.getString("id"))
        );
    }

    /**
     * Parses account type safely from DB text.
     */
    private static BankAccount.AccountType parseAccountType(String rawType, String accountId) throws BankingError {
        if (rawType == null || rawType.isBlank()) {
            throw new BankingError("Invalid account type for account: " + accountId);
        }
        try {
            return BankAccount.AccountType.valueOf(rawType);
        } catch (IllegalArgumentException e) {
            throw new BankingError("Unsupported account type '" + rawType + "' for account: " + accountId);
        }
    }

    /**
     * Parses transaction type safely from DB text.
     */
    private static Transaction.TransactionType parseTransactionType(String rawType, String transactionId) throws BankingError {
        if (rawType == null || rawType.isBlank()) {
            throw new BankingError("Invalid transaction type for transaction: " + transactionId);
        }
        try {
            return Transaction.TransactionType.valueOf(rawType);
        } catch (IllegalArgumentException e) {
            throw new BankingError("Unsupported transaction type '" + rawType + "' for transaction: " + transactionId);
        }
    }

    /**
     * Parses money amount safely from DB text.
     */
    private static BigDecimal parseAmount(String rawAmount, String fieldName, String recordId) throws BankingError {
        if (rawAmount == null || rawAmount.isBlank()) {
            throw new BankingError("Invalid numeric value in " + fieldName + " for record: " + recordId);
        }
        try {
            return new BigDecimal(rawAmount);
        } catch (NumberFormatException e) {
            throw new BankingError("Malformed numeric value '" + rawAmount + "' in " + fieldName + " for record: " + recordId);
        }
    }

    /**
     * Parses created_at safely from DB timestamp.
     */
    private static Instant parseCreatedAt(ResultSet rs, String transactionId) throws SQLException, BankingError {
        java.sql.Timestamp timestamp = rs.getTimestamp("created_at");
        if (timestamp == null) {
            throw new BankingError("Missing created_at timestamp for transaction: " + transactionId);
        }
        try {
            return timestamp.toInstant();
        } catch (DateTimeException e) {
            throw new BankingError("Invalid created_at timestamp for transaction: " + transactionId);
        }
    }

    private AccountManager() {}
}
