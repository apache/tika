package org.apache.tika.pipes.fetcher.http.config;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;

public class AdditionalHttpHeaders {
    @JsonIgnore
    private Multimap<String, String> headers = ArrayListMultimap.create();

    @JsonIgnore
    public Multimap<String, String> getHeaders() {
        return headers;
    }

    public Map<String, Collection<String>> getMap() {
        return headers.asMap();
    }

    public void setMap(Map<String, Collection<String>> map) {
        headers = ArrayListMultimap.create();
        map.forEach(headers::putAll);
    }

    public void setHeaders(Multimap<String, String> headers) {
        this.headers = headers;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AdditionalHttpHeaders that = (AdditionalHttpHeaders) o;
        return Objects.equals(headers, that.headers);
    }

    @Override
    public int hashCode() {
        return Objects.hash(headers);
    }
}
