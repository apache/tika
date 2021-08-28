package org.apache.tika.parser.microsoft.fsshttpb;

import java.util.concurrent.atomic.AtomicLong;

public class SequenceNumberGenerator {
    /// <summary>
    /// Specify the max token value.
    /// </summary>
    public static final double MAXTOKENVALUE = 4294967295.0;

    /// <summary>
    /// Specify the max sub request ID.
    /// </summary>
    public static final int MAXSUBREQUSTID = 0xFFFFFFFF;

    /// <summary>
    /// Specify the current token value.
    /// </summary>
    private static final AtomicLong token = new AtomicLong(0L);

    /// <summary>
    /// Specify the current token value.
    /// </summary>
    private static final AtomicLong subRequestID = new AtomicLong(0);

    /// <summary>
    /// Specify the current serialNumber value.
    /// </summary>
    private static final AtomicLong serialNumber = new AtomicLong(1);

    /// <summary>
    /// This method is used to get the current token value and atomic adding the token by 1.
    /// </summary>
    /// <returns>Returns the current token value.</returns>
    public static int getCurrentToken() {
        long currentToken = token.incrementAndGet();

        // The token value cannot be exceed the value 4294967295.
        if (currentToken > MAXTOKENVALUE) {
            throw new RuntimeException("The token exceeds the max value 4294967295");
        }

        return (int) currentToken;
    }

    /// <summary>
    /// This method is used to get the current sub request ID and atomic adding the token by 1.
    /// </summary>
    /// <returns>Returns the current sub request ID.</returns>
    public static int getCurrentFSSHTTPBSubRequestID() {
        // Thread safety increase the token by 1.
        long currentID = subRequestID.incrementAndGet();

        // The token value cannot be exceed the value 0xFFFFFFFF.
        if (currentID > MAXSUBREQUSTID) {
            throw new RuntimeException("The token exceeds the max value 4294967295");
        }

        return (int) currentID;
    }

    /// <summary>
    /// This method is used to get the current serial number and atomic adding the token by 1.
    /// </summary>
    /// <returns>Return the current serial number.</returns>
    public static int GetCurrentSerialNumber() {
        // Thread safety increase the token by 1.
        long currentID = serialNumber.incrementAndGet();
        return (int) currentID;
    }
}