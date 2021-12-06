package org.apache.tika.parser.microsoft.fsshttpb.util;

import java.util.concurrent.atomic.AtomicLong;

public class SequenceNumberGenerator {
    /**
     * Specify the max token value.
     */
    public static final double MAXTOKENVALUE = 4294967295.0;

    /**
     * Specify the max sub request ID.
     */
    public static final int MAXSUBREQUSTID = 0xFFFFFFFF;

    /**
     * Specify the current token value.
     */
    private static final AtomicLong token = new AtomicLong(0L);

    /**
     * Specify the current token value.
     */
    private static final AtomicLong subRequestID = new AtomicLong(0);

    /**
     * Specify the current serialNumber value.
     */
    private static final AtomicLong serialNumber = new AtomicLong(1);

    /**
     * This method is used to get the current token value and atomic adding the token by 1.
     *
     * @return Returns the current token value.
     */
    public static int getCurrentToken() {
        long currentToken = token.incrementAndGet();

        // The token value cannot be exceed the value 4294967295.
        if (currentToken > MAXTOKENVALUE) {
            throw new RuntimeException("The token exceeds the max value 4294967295");
        }

        return (int) currentToken;
    }

    /**
     * This method is used to get the current sub request ID and atomic adding the token by 1.
     *
     * @return Returns the current sub request ID.
     */
    public static int getCurrentFSSHTTPBSubRequestID() {
        // Thread safety increase the token by 1.
        long currentID = subRequestID.incrementAndGet();

        // The token value cannot be exceed the value 0xFFFFFFFF.
        if (currentID > MAXSUBREQUSTID) {
            throw new RuntimeException("The token exceeds the max value 4294967295");
        }

        return (int) currentID;
    }

    /**
     * This method is used to get the current serial number and atomic adding the token by 1.
     *
     * @return Return the current serial number.
     */
    public static int GetCurrentSerialNumber() {
        // Thread safety increase the token by 1.
        long currentID = serialNumber.incrementAndGet();
        return (int) currentID;
    }
}