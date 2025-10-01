package org.apache.tika.pipes.repo;

import java.util.List;

import org.apache.ignite.springdata.repository.IgniteRepository;
import org.apache.ignite.springdata.repository.config.RepositoryConfig;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

import org.apache.tika.pipes.core.emitter.DefaultEmitterConfig;

@Repository
@RepositoryConfig(cacheName = "EmitterCache")
public interface EmitterRepository extends IgniteRepository<DefaultEmitterConfig, String> {
    DefaultEmitterConfig findByEmitterId(String emitterId);
    @NotNull
    List<DefaultEmitterConfig> findAll();
    void deleteByEmitterId(String emitterId);
}
