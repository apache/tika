package org.apache.tika.batch.fs;

import org.apache.tika.metadata.Property;

public class FSProperties {
    private final static String TIKA_BATCH_FS_NAMESPACE = "tika_batch_fs";

    /**
     * File's relative path (including file name) from a given source root
     */
    public final static Property FS_REL_PATH = Property.internalText(TIKA_BATCH_FS_NAMESPACE+":relative_path");
}
