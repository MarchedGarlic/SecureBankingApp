package org.example.db;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.AclEntry;
import java.nio.file.attribute.AclEntryPermission;
import java.nio.file.attribute.AclEntryType;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.nio.file.attribute.UserPrincipal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Set;

/**
 * Opens shared database connections and keeps DB file permissions locked down.
 */
public class DatabaseManager {
    static final String DB_PATH = "bank.db";
    private static final String DB_URL = "jdbc:sqlite:" + DB_PATH;

    /**
     * Returns a database connection after enforcing secure file permissions.
     */
    public static Connection getConnection() throws SQLException {
        enforceSecurePermissions();
        return DriverManager.getConnection(DB_URL);
    }

    /**
     * Makes sure the DB file exists and is readable/writable by the owner only.
     */
    private static void enforceSecurePermissions() {
        Path path = Paths.get(DB_PATH);
        try {
            FileSystem fs = path.getFileSystem();
            boolean isPosix = fs.supportedFileAttributeViews().contains("posix");

            if (!Files.exists(path)) {
                // Create the file with restrictive permissions from the start
                if (isPosix) {
                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                    Files.createFile(path, PosixFilePermissions.asFileAttribute(perms));
                } else {
                    Files.createFile(path);
                    applyWindowsOwnerOnlyPermissions(path);
                }
            } else {
                // Re-enforce permissions on an already-existing file
                if (isPosix) {
                    Set<PosixFilePermission> perms = PosixFilePermissions.fromString("rw-------");
                    Files.setPosixFilePermissions(path, perms);
                } else {
                    applyWindowsOwnerOnlyPermissions(path);
                }
            }
        } catch (IOException e) {
            // Fail closed if permissions cannot be enforced.
            throw new SecurityException("Failed to enforce secure permissions on database file: " + DB_PATH, e);
        }
    }

    /**
     * On Windows, replaces the ACL so only the file owner keeps access.
     */
    private static void applyWindowsOwnerOnlyPermissions(Path path) throws IOException {
        AclFileAttributeView aclView = Files.getFileAttributeView(path, AclFileAttributeView.class);
        if (aclView == null) {
            throw new IOException("Cannot obtain ACL attribute view for: " + path);
        }

        UserPrincipal owner = Files.getOwner(path);

        // Keep a single allow entry for the owner.
        AclEntry ownerEntry = AclEntry.newBuilder()
                .setType(AclEntryType.ALLOW)
                .setPrincipal(owner)
                .setPermissions(
                        AclEntryPermission.READ_DATA,
                        AclEntryPermission.WRITE_DATA,
                        AclEntryPermission.APPEND_DATA,
                        AclEntryPermission.READ_ATTRIBUTES,
                        AclEntryPermission.WRITE_ATTRIBUTES,
                        AclEntryPermission.READ_NAMED_ATTRS,
                        AclEntryPermission.WRITE_NAMED_ATTRS,
                        AclEntryPermission.READ_ACL,
                        AclEntryPermission.SYNCHRONIZE)
                .build();

        // If this fails, caller will stop startup.
        aclView.setAcl(List.of(ownerEntry));
    }

    private DatabaseManager() {}
}
