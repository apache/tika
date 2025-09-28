package org.apache.tika.pipes.core.emitter;

import java.util.List;
import java.util.Map;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Builder
public class EmitOutput {
    private String fetchKey;
    private List<Map<String, List<Object>>> metadata;
}
