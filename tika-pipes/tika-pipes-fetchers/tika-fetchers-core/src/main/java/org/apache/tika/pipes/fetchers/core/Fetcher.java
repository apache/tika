package org.apache.tika.pipes.fetchers.core;

import java.io.InputStream;
import java.util.Map;

import org.pf4j.ExtensionPoint;

public interface Fetcher extends ExtensionPoint {
    InputStream fetch(FetcherConfig fetcherConfig, String fetchKey, Map<String, Object> fetchMetadata, Map<String, Object> responseMetadata);
}
