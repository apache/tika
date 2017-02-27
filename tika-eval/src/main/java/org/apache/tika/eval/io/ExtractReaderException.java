package org.apache.tika.eval.io;

import java.io.IOException;

/**
 * Exception when trying to read extract
 */
public class ExtractReaderException extends IOException {

    public enum TYPE {
        //what do you see when you look at the extract file
        NO_EXTRACT_FILE,
        ZERO_BYTE_EXTRACT_FILE,
        IO_EXCEPTION,
        EXTRACT_PARSE_EXCEPTION,
        EXTRACT_FILE_TOO_SHORT,
        EXTRACT_FILE_TOO_LONG,
        INCORRECT_EXTRACT_FILE_SUFFIX;//extract file must have suffix of .json or .txt,
        // optionally followed by gzip, zip or bz2
    }

    private final TYPE type;

    public ExtractReaderException(TYPE exceptionType) {
        this.type = exceptionType;
    }

    public TYPE getType() {
        return type;
    }

}
