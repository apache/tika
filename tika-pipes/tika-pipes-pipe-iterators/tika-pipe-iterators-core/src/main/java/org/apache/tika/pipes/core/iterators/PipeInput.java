package org.apache.tika.pipes.core.iterators;

import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class PipeInput {
    private String fetchKey;
    private Map<String, Object> metadata;
}
