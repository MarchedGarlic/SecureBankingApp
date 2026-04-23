package org.example.banking;

import java.time.Instant;
import java.util.List;

public final class StatementFormatter {

    public static String format(BankAccount account, List<Transaction> transactions, Instant generatedAt) {
        StringBuilder sb = new StringBuilder();
        sb.append("Statement generated at: ").append(generatedAt).append('\n');
        sb.append("Account ID: ").append(account.getId()).append('\n');
        sb.append("Account Type: ").append(account.getAccountType()).append('\n');
        sb.append("Current Balance: ").append(account.getBalance().toPlainString()).append("\n\n");

        sb.append("Transactions:\n");
        if (transactions.isEmpty()) {
            sb.append("  No transactions found.\n");
            return sb.toString();
        }

        for (Transaction tx : transactions) {
            String from = tx.getFromAccountId() == null ? "-" : tx.getFromAccountId();
            String to = tx.getToAccountId() == null ? "-" : tx.getToAccountId();
            sb.append("  ")
              .append(tx.getCreatedAt())
              .append(" | ")
              .append(tx.getType())
              .append(" | ")
              .append(tx.getAmount().toPlainString())
              .append(" | from=")
              .append(from)
              .append(" | to=")
              .append(to)
              .append('\n');
        }

        return sb.toString();
    }

    private StatementFormatter() {}
}
