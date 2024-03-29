package org.apache.tika.pipes.grpc;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.config.AbstractConfig;

public class ExpiringFetcherStore implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(ExpiringFetcherStore.class);
    public static final long EXPIRE_JOB_INITIAL_DELAY = 1L;
    private final Map<String, AbstractFetcher> fetchers = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, AbstractConfig> fetcherConfigs = Collections.synchronizedMap(new HashMap<>());
    private final Map<String, Instant> fetcherLastAccessed =
            Collections.synchronizedMap(new HashMap<>());

    private final ScheduledExecutorService executorService =
            Executors.newSingleThreadScheduledExecutor();

    public ExpiringFetcherStore(int expireAfterSeconds, int checkForExpiredFetchersDelaySeconds) {
        executorService.scheduleAtFixedRate(() -> {
            Set<String> expired = new HashSet<>();
            for (String fetcherName : fetchers.keySet()) {
                Instant lastAccessed = fetcherLastAccessed.get(fetcherName);
                if (lastAccessed == null) {
                    LOG.error("Detected a fetcher with no last access time. FetcherName={}",
                            fetcherName);
                    expired.add(fetcherName);
                } else if (Instant.now().isAfter(lastAccessed.plusSeconds(expireAfterSeconds))) {
                    LOG.info("Detected stale fetcher {} hasn't been access in {} seconds. " +
                                    "Deleting.",
                            fetcherName, Instant.now().getEpochSecond() - lastAccessed.getEpochSecond());
                    expired.add(fetcherName);
                }
            }
            for (String expiredFetcherId : expired) {
                deleteFetcher(expiredFetcherId);
            }
        }, EXPIRE_JOB_INITIAL_DELAY, checkForExpiredFetchersDelaySeconds, TimeUnit.SECONDS);
    }
    
    public void deleteFetcher(String fetcherName) {
        fetchers.remove(fetcherName);
        fetcherConfigs.remove(fetcherName);
        fetcherLastAccessed.remove(fetcherName);
    }

    public Map<String, AbstractFetcher> getFetchers() {
        return fetchers;
    }
    
    public Map<String, AbstractConfig> getFetcherConfigs() {
        return fetcherConfigs;
    }

    /**
     * This method will get the fetcher, but will also log the access the fetcher as having
     * been accessed. This prevents the scheduled job from removing the stale fetcher.
     */
    public <T extends AbstractFetcher> T getFetcherAndLogAccess(String fetcherName) {
        fetcherLastAccessed.put(fetcherName, Instant.now());
        return (T) fetchers.get(fetcherName);
    }
    
    public <T extends AbstractFetcher, C extends AbstractConfig> void createFetcher(T fetcher, C config) {
        fetchers.put(fetcher.getName(), fetcher);
        fetcherConfigs.put(fetcher.getName(), config);
        getFetcherAndLogAccess(fetcher.getName());
    }

    @Override
    public void close() {
        executorService.shutdownNow();
    }
}
