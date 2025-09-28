package org.apache.tika.pipes.repo;

import java.util.List;

import org.apache.ignite.springdata.repository.IgniteRepository;
import org.apache.ignite.springdata.repository.config.RepositoryConfig;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

import org.apache.tika.pipes.fetchers.core.DefaultFetcherConfig;

@Repository
@RepositoryConfig(cacheName = "FetcherCache")
public interface FetcherRepository extends IgniteRepository<DefaultFetcherConfig, String> {
    DefaultFetcherConfig findByFetcherId(String fetcherId);
    @NotNull
    List<DefaultFetcherConfig> findAll();
    void deleteByFetcherId(String fetcherId);
}
