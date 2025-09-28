package org.apache.tika.pipes.repo;

import java.io.File;
import java.io.IOException;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.springdata.repository.config.EnableIgniteRepositories;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.apache.tika.pipes.core.emitter.DefaultEmitterConfig;
import org.apache.tika.pipes.core.iterators.DefaultPipeIteratorConfig;
import org.apache.tika.pipes.fetchers.core.DefaultFetcherConfig;
import org.apache.tika.pipes.job.JobStatus;

@Configuration
@EnableIgniteRepositories
@Slf4j
public class IgniteRepositoryConfiguration {
    @Value("${ignite.workDir:#{null}}")
    private String igniteWorkDir;

    @Bean
    public Ignite igniteInstance() throws IOException {
        try {
            IgniteConfiguration cfg = new IgniteConfiguration();
            cfg.setIgniteInstanceName("springDataNode");
            cfg.setPeerClassLoadingEnabled(true);
            if (StringUtils.isNotBlank(igniteWorkDir)) {
                File igniteWorkDirFile = new File(igniteWorkDir);
                if (!igniteWorkDirFile.exists() && !igniteWorkDirFile.mkdirs()) {
                    throw new IOException("Could not create the ignite work directory: " + igniteWorkDir);
                }
                cfg.setWorkDirectory(igniteWorkDirFile.getCanonicalPath());
                log.info("Initializing ignite instance with ignite work dir: {}", cfg.getWorkDirectory());
            }

            CacheConfiguration<String, DefaultFetcherConfig> fetcherCacheConf = new CacheConfiguration<>("FetcherCache");
            fetcherCacheConf.setIndexedTypes(String.class, DefaultFetcherConfig.class);

            CacheConfiguration<String, DefaultEmitterConfig> emitterCacheConf = new CacheConfiguration<>("EmitterCache");
            emitterCacheConf.setIndexedTypes(String.class, DefaultEmitterConfig.class);

            CacheConfiguration<String, DefaultPipeIteratorConfig> pipeIteratorCacheConf = new CacheConfiguration<>("PipeIteratorCache");
            pipeIteratorCacheConf.setIndexedTypes(String.class, DefaultPipeIteratorConfig.class);

            CacheConfiguration<String, JobStatus> jobStatusCacheConf = new CacheConfiguration<>("JobStatusCache");
            jobStatusCacheConf.setIndexedTypes(String.class, JobStatus.class);

            cfg.setCacheConfiguration(fetcherCacheConf, emitterCacheConf, pipeIteratorCacheConf, jobStatusCacheConf);

            Ignite ignite = Ignition.start(cfg);
            assert ignite.configuration() != null;
            log.info("Ignite started with config: {}", ignite.configuration());
            return ignite;
        } catch (Throwable e) {
            log.error("Ignite failed to start", e);
            throw new IOException(e);
        } finally {
            log.info("Leaving method ignite instance");
        }
    }
}
