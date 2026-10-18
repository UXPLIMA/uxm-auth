package com.uxplima.auth.application.protection;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Supplier;

import com.uxplima.auth.application.port.AccountRepository;
import com.uxplima.auth.domain.protection.AddressKey;
import com.uxplima.auth.domain.protection.AddressRanges;
import com.uxplima.auth.domain.protection.SharedAddresses;

/**
 * How many password accounts one address may make inside a window. Premium and Bedrock accounts are proved by
 * Mojang or Xbox and are neither counted nor refused. An exempt address, a shared-address filter and an address
 * the server could not read are never full, because each of them is many players at once.
 */
public final class AccountCap {

    /**
     * The limit.
     *
     * @param perAddress the password accounts one address may make inside the window
     * @param window how far back an account still counts
     * @param exempt the addresses and ranges never limited
     */
    public record Settings(int perAddress, Duration window, AddressRanges exempt) {
        public Settings {
            Objects.requireNonNull(window, "window");
            Objects.requireNonNull(exempt, "exempt");
            if (perAddress < 1) {
                throw new IllegalArgumentException("An address needs at least one account, got: " + perAddress);
            }
            if (window.isNegative() || window.isZero()) {
                throw new IllegalArgumentException("The window must be longer than nothing");
            }
        }
    }

    private final AccountRepository accounts;
    private final Supplier<Settings> settings;
    private final Supplier<SharedAddresses> shared;
    private final Supplier<Instant> clock;

    public AccountCap(
            AccountRepository accounts,
            Supplier<Settings> settings,
            Supplier<SharedAddresses> shared,
            Supplier<Instant> clock) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.shared = Objects.requireNonNull(shared, "shared");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Whether this address, which typed this host, has made all the password accounts it may for now. */
    public boolean full(String address, String host) {
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(host, "host");
        Settings now = settings.get();
        if (!AddressKey.isAddress(address)
                || now.exempt().contains(address)
                || shared.get().through(address, host)) {
            return false;
        }
        return accounts.countRegisteredFrom(address, clock.get().minus(now.window())) >= now.perAddress();
    }
}
