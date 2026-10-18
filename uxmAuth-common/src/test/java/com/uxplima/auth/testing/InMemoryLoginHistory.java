package com.uxplima.auth.testing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import com.uxplima.auth.application.port.LoginHistory;
import com.uxplima.auth.domain.protection.AddressKey;

/** The login history in memory, as the table keeps it. */
public final class InMemoryLoginHistory implements LoginHistory {

    private final List<Map.Entry<UUID, Entry>> rows = new CopyOnWriteArrayList<>();

    @Override
    public void record(UUID account, Entry entry) {
        rows.add(Map.entry(account, entry));
    }

    @Override
    public List<Entry> recent(UUID account, int limit) {
        return rows.stream()
                .filter(row -> row.getKey().equals(account))
                .map(Map.Entry::getValue)
                .sorted(Comparator.comparing(Entry::at).reversed())
                .limit(Math.max(0, limit))
                .toList();
    }

    @Override
    public int count(UUID account) {
        return (int) rows.stream().filter(row -> row.getKey().equals(account)).count();
    }

    @Override
    public List<UUID> accountsOn(String address, int limit) {
        String key = AddressKey.of(address);
        LinkedHashSet<UUID> found = new LinkedHashSet<>();
        for (Map.Entry<UUID, Entry> row : rows) {
            if (AddressKey.of(row.getValue().address()).equals(key)) {
                found.add(row.getKey());
            }
        }
        return new ArrayList<>(found).subList(0, Math.min(found.size(), Math.max(0, limit)));
    }

    @Override
    public int prune(Instant before) {
        int size = rows.size();
        rows.removeIf(row -> row.getValue().at().isBefore(before));
        return size - rows.size();
    }
}
