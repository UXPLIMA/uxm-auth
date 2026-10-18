package com.uxplima.auth.testing;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import com.uxplima.auth.application.port.Factors;
import com.uxplima.auth.domain.factor.TotpSecret;
import com.uxplima.auth.domain.hash.PasswordHash;

/** The factor table in memory, for the use cases' tests. */
public class InMemoryFactors implements Factors {

    private final Map<UUID, App> apps = new ConcurrentHashMap<>();
    private final Map<UUID, List<PasswordHash>> codes = new ConcurrentHashMap<>();

    /** Whether a read answers the app as it was before any step, as another server's read does mid-race. */
    public boolean readsStale;

    @Override
    public Optional<App> app(UUID account) {
        return Optional.ofNullable(apps.get(account)).map(app -> readsStale ? new App(app.secret(), -1) : app);
    }

    @Override
    public void setApp(UUID account, TotpSecret secret) {
        apps.compute(account, (id, before) -> new App(secret, -1));
    }

    @Override
    public void removeApp(UUID account) {
        apps.compute(account, (id, before) -> null);
    }

    @Override
    public boolean useStep(UUID account, long step) {
        AtomicBoolean taken = new AtomicBoolean();
        apps.computeIfPresent(account, (id, app) -> {
            if (app.lastStep() >= step) {
                return app;
            }
            taken.set(true);
            return new App(app.secret(), step);
        });
        return taken.get();
    }

    @Override
    public List<PasswordHash> backupCodes(UUID account) {
        return List.copyOf(codes.getOrDefault(account, List.of()));
    }

    @Override
    public void setBackupCodes(UUID account, List<PasswordHash> kept) {
        codes.compute(account, (id, before) -> kept.isEmpty() ? null : List.copyOf(kept));
    }

    @Override
    public boolean useBackupCode(UUID account, PasswordHash code) {
        AtomicBoolean spent = new AtomicBoolean();
        codes.computeIfPresent(account, (id, before) -> {
            List<PasswordHash> left = new ArrayList<>(before);
            spent.set(left.remove(code));
            return left.isEmpty() ? null : List.copyOf(left);
        });
        return spent.get();
    }
}
