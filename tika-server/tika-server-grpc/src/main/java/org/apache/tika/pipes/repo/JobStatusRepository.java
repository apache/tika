package org.apache.tika.pipes.repo;

import java.util.List;

import org.apache.ignite.springdata.repository.IgniteRepository;
import org.apache.ignite.springdata.repository.config.RepositoryConfig;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Repository;

import org.apache.tika.pipes.job.JobStatus;

@Repository
@RepositoryConfig(cacheName = "JobStatusCache")
public interface JobStatusRepository extends IgniteRepository<JobStatus, String> {
    JobStatus findByJobId(String jobId);
    @NotNull
    List<JobStatus> findAll();
}
