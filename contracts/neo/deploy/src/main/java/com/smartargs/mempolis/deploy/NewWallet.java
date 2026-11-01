package com.smartargs.mempolis.deploy;

import io.neow3j.wallet.Account;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/** Creates a new Neo N3 wallet and writes its key to a private file, so the key never shows on screen. */
public final class NewWallet {

    private static final String KEYS_FOLDER = "mempolis-keys";
    private static final String NAME = "[a-z][a-z0-9-]*";

    private NewWallet() {}

    /** Creates the wallet named by the one argument in the user's keys folder and prints its address. */
    public static void main(String[] args) {
        try {
            if (args.length != 1 || !args[0].matches(NAME)) throw new IllegalArgumentException("Name the wallet: owner or deployer.");
            Path folder = Path.of(System.getProperty("user.home"), KEYS_FOLDER), file = folder.resolve(args[0] + ".wif");
            System.out.println("Address  " + create(file));
            System.out.println("Key      " + file + " (read only by you)");
        } catch (Exception failure) {
            System.err.println("Failed: " + failure.getMessage());
            System.exit(1);
        }
    }

    /** Writes a new wallet's key to {@code file}, readable by the user alone, and returns its address. */
    public static String create(Path file) throws IOException {
        if (Files.exists(file)) throw new IllegalStateException(file + " already exists. Keeping it.");
        Files.createDirectories(file.getParent(), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Account account = Account.create();
        Files.createFile(file, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
        Files.writeString(file, account.getECKeyPair().exportAsWIF() + "\n");
        String stored = Account.fromWIF(Files.readString(file).trim()).getAddress();
        if (!stored.equals(account.getAddress())) throw new IllegalStateException("The stored key does not match.");
        return stored;
    }
}
