package org.apache.tika.pipes.fetchers.filesystem;

import java.nio.charset.StandardCharsets;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.csv.CSVFormat;
import org.pf4j.Extension;

import org.apache.tika.pipes.core.iterators.DefaultPipeIteratorConfig;

@Extension
@Getter
@Setter
public class CsvPipeIteratorConfig extends DefaultPipeIteratorConfig {
    private String charset = StandardCharsets.UTF_8.name();
    private String csvPath;
    private String fetchKeyColumn;
    private Integer fetchKeyColumnIndex;
    private String csvFormat = CSVFormat.Predefined.Excel.name();
}
