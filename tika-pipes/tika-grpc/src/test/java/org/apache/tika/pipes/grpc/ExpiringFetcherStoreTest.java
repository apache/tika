package org.apache.tika.pipes.grpc;

import java.io.InputStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.fetcher.AbstractFetcher;
import org.apache.tika.pipes.fetcher.config.AbstractConfig;

class ExpiringFetcherStoreTest {

    @Test
    void createFetcher() {
        try (ExpiringFetcherStore expiringFetcherStore = new ExpiringFetcherStore(1, 60)) {
            AbstractFetcher fetcher = new AbstractFetcher() {
                @Override
                public InputStream fetch(String fetchKey, Metadata metadata) {
                    return null;
                }
            };
            fetcher.setName("nick");
            AbstractConfig config = new AbstractConfig() {

            };
            expiringFetcherStore.createFetcher(fetcher, config);

            Assertions.assertNotNull(expiringFetcherStore.getFetchers().get(fetcher.getName()));

            try {
                Thread.sleep(2000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            Assertions.assertNull(expiringFetcherStore.getFetchers().get(fetcher.getName()));
            Assertions.assertNull(expiringFetcherStore.getFetcherConfigs().get(fetcher.getName()));
        }
    }
}
