package org.apache.tika.pipes.job;

import java.io.Serializable;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.apache.ignite.cache.query.annotations.QuerySqlField;

@Builder
@Getter
@Setter
public class JobStatus implements Serializable {
    @QuerySqlField(index = true)
    private String jobId;
    private Boolean running;
    private Boolean completed;
    private Boolean hasError;
}
