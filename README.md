# Secure banking app
.\gradlew build
java -jar app\build\libs\app-all.jar

1. User accounts with sessions (session expiration, privilege escalation)
2. Password storage (weak hashing, weak encoding, insufficient entropy)
3. File or database access (permissions, public/private variables)
4. Concurrent transactions (TOCTOU race conditions, thread race conditions)
5. Transfers/withdrawals (mutable object passing, CLONE issues)