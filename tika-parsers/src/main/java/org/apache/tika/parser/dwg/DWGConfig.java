package org.apache.tika.parser.dwg;

import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.util.Properties;

public class DWGConfig implements Serializable {
    private static final long serialVersionUID = -7623524257255755725L;

    /**
     * The GNU dwgread executable to use to parse DWG contents, if applicable.
     * If not specified, will just parse headers.
     */
    private String dwgReadExecutable = "";

    /**
     * The dwgread sometimes creates strings with invalid utf-8 bytes, etc. Results in
     * invalid UTF-8 middle byte errors during json parse. If true,
     * take a pass through the resulting json from dwgread and clear out invalid stuff.
     */
    private boolean cleanDwgReadOutput = true;

    /**
     * Batch size when cleaning result of dwgread JSON output.
     */
    private int cleanDwgReadOutputBatchSize = 10000000;

    /**
     * The regex used to clean dwgread output. By default, just clears out any non-invalid UTF-8 sequence.
     */
    private String cleanDwgReadRegexToReplace = "[^\\x20-\\x7e]";

    /**
     * When cleaning dwgread output, what to replace matches with.
     */
    private String cleanDwgReadReplaceWith = "";

    /**
     * Default contructor.
     */
    public DWGConfig() {
        init(this.getClass().getResourceAsStream("DWGConfig.properties"));
    }

    private void init(InputStream is) {
        if (is == null) {
            return;
        }
        Properties props = new Properties();
        try {
            props.load(is);
        } catch (IOException e) {
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    //swallow
                }
            }
        }

        // set parameters for DWG parser
        if (props.get("dwgReadExecutable") != null) {
            dwgReadExecutable = (String) props.get("dwgReadExecutable");
        }
        if (props.get("cleanDwgReadOutput") != null) {
            cleanDwgReadOutput = (Boolean) props.get("cleanDwgReadOutput");
        }
        if (props.get("cleanDwgReadOutputBatchSize") != null) {
            cleanDwgReadOutputBatchSize = (Integer) props.get("cleanDwgReadOutputBatchSize");
        }
        if (props.get("cleanDwgReadRegexToReplace") != null) {
            cleanDwgReadRegexToReplace = (String) props.get("cleanDwgReadRegexToReplace");
        }
        if (props.get("cleanDwgReadReplaceWith") != null) {
            cleanDwgReadReplaceWith = (String) props.get("cleanDwgReadReplaceWith");
        }
    }

    public String getDwgReadExecutable() {
        return dwgReadExecutable;
    }

    public DWGConfig setDwgReadExecutable(String dwgReadExecutable) {
        this.dwgReadExecutable = dwgReadExecutable;
        return this;
    }

    public boolean isCleanDwgReadOutput() {
        return cleanDwgReadOutput;
    }

    public DWGConfig setCleanDwgReadOutput(boolean cleanDwgReadOutput) {
        this.cleanDwgReadOutput = cleanDwgReadOutput;
        return this;
    }

    public int getCleanDwgReadOutputBatchSize() {
        return cleanDwgReadOutputBatchSize;
    }

    public DWGConfig setCleanDwgReadOutputBatchSize(int cleanDwgReadOutputBatchSize) {
        this.cleanDwgReadOutputBatchSize = cleanDwgReadOutputBatchSize;
        return this;
    }

    public String getCleanDwgReadRegexToReplace() {
        return cleanDwgReadRegexToReplace;
    }

    public DWGConfig setCleanDwgReadRegexToReplace(String cleanDwgReadRegexToReplace) {
        this.cleanDwgReadRegexToReplace = cleanDwgReadRegexToReplace;
        return this;
    }

    public String getCleanDwgReadReplaceWith() {
        return cleanDwgReadReplaceWith;
    }

    public DWGConfig setCleanDwgReadReplaceWith(String cleanDwgReadReplaceWith) {
        this.cleanDwgReadReplaceWith = cleanDwgReadReplaceWith;
        return this;
    }
}
