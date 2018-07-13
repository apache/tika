package org.apache.tika.parser.html.charsetdetector;

import org.apache.tika.detect.CompositeEncodingDetector;

import static java.util.Arrays.asList;

/**
 * A composite encoding detector chaining a {@link StandardHtmlEncodingDetector}
 * (that may return null) and a {@link StandardIcu4JEncodingDetector} (that always returns a value)
 * This full detector thus always returns an encoding, and still works very well with data coming
 * from the web.
 */
public class FullStandardEncodingDetector extends CompositeEncodingDetector {
    public FullStandardEncodingDetector() {
        super(asList(
                new StandardHtmlEncodingDetector(),
                StandardIcu4JEncodingDetector.STANDARD_ICU4J_ENCODING_DETECTOR
        ));
    }
}
