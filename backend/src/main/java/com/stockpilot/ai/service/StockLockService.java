package com.stockpilot.ai.service;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.sql.SQLException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class StockLockService {
    private static final int POSTGRES_LOCK_NAMESPACE = 0x53504B53;

    private final JdbcTemplate jdbcTemplate;
    private final ConcurrentMap<String, ReentrantLock> localLocks = new ConcurrentHashMap<>();
    private volatile Boolean postgres;

    public StockLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void lock(UUID tenantId, UUID productId, UUID warehouseId) {
        lockAll(java.util.List.of(new StockKey(tenantId, productId, warehouseId)));
    }

    public void lockAll(Collection<StockKey> keys) {
        requireActiveTransaction();
        keys.stream()
            .filter(Objects::nonNull)
            .distinct()
            .sorted(Comparator.comparing(StockKey::canonical))
            .forEach(this::lockOne);
    }

    private void lockOne(StockKey key) {
        if (isPostgres()) {
            advisoryTransactionLock(key);
        } else {
            localTransactionLock(key);
        }
    }

    private void advisoryTransactionLock(StockKey key) {
        jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
            try (var statement = connection.prepareStatement("select pg_advisory_xact_lock(?, ?)")) {
                statement.setInt(1, POSTGRES_LOCK_NAMESPACE);
                statement.setInt(2, key.canonical().hashCode());
                statement.execute();
            }
            return null;
        });
    }

    private void localTransactionLock(StockKey key) {
        var lock = localLocks.computeIfAbsent(key.canonical(), ignored -> new ReentrantLock());
        lock.lock();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                lock.unlock();
            }
        });
    }

    private void requireActiveTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
            || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Stock-changing operations require an active transaction before locking stock keys");
        }
    }

    private boolean isPostgres() {
        var cached = postgres;
        if (cached != null) {
            return cached;
        }
        synchronized (this) {
            if (postgres == null) {
                postgres = jdbcTemplate.execute((ConnectionCallback<Boolean>) connection -> {
                    try {
                        return connection.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT).contains("postgresql");
                    } catch (SQLException exception) {
                        return false;
                    }
                });
            }
            return postgres;
        }
    }

    public record StockKey(UUID tenantId, UUID productId, UUID warehouseId) {
        String canonical() {
            return tenantId + ":" + productId + ":" + warehouseId;
        }
    }
}
