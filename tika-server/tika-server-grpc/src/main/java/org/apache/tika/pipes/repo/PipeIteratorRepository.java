package org.apache.tika.pipes.repo;

import java.util.List;

import org.apache.ignite.springdata.repository.IgniteRepository;
import org.apache.ignite.springdata.repository.config.RepositoryConfig;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

import org.apache.tika.pipes.core.iterators.DefaultPipeIteratorConfig;

@Repository
@RepositoryConfig(cacheName = "PipeIteratorCache")
public interface PipeIteratorRepository extends IgniteRepository<DefaultPipeIteratorConfig, String> {
    DefaultPipeIteratorConfig findByPipeIteratorId(String pipeIteratorId);
    @NotNull
    List<DefaultPipeIteratorConfig> findAll();
    void deleteByPipeIteratorId(String pipeIteratorId);
}
