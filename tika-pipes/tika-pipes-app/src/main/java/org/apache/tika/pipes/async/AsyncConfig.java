package org.apache.tika.pipes.async;

import org.apache.tika.utils.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class AsyncConfig {

    public static AsyncConfig load(Path p) throws IOException {
        AsyncConfig asyncConfig = new AsyncConfig();

        if (StringUtils.isBlank(asyncConfig.getJdbcString())) {
            asyncConfig.dbDir = Files.createTempDirectory("tika-async-");
            Path dbFile = asyncConfig.dbDir.resolve("tika-async");
            asyncConfig.setJdbcString("jdbc:h2:file:" + dbFile.toAbsolutePath().toString() +
                    ";AUTO_SERVER=TRUE");
        } else {
            asyncConfig.dbDir = null;
        }
        return asyncConfig;
    }

    private int queueSize = 1000;
    private int maxConsumers = 10;
    private String jdbcString;
    private Path dbDir;

    public int getQueueSize() {
        return queueSize;
    }

    public int getMaxConsumers() {
        return maxConsumers;
    }

    public String getJdbcString() {
        return jdbcString;
    }

    public void setJdbcString(String jdbcString) {
        this.jdbcString = jdbcString;
    }

    /**
     * If no jdbc connection was specified, this
     * dir contains the h2 database.  Otherwise, null.
     * @return
     */
    public Path getTempDBDir() {
        return dbDir;
    }
}
