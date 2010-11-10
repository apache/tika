package org.apache.tika.parser.image;

import org.apache.tika.metadata.DublinCore;
import org.apache.tika.metadata.TIFF;

import junit.framework.TestCase;

public class MetadataFieldsTest extends TestCase {

    public void testIsMetadataField() {
        assertFalse(MetadataFields.isMetadataField("random string that is not a field"));
        assertFalse(MetadataFields.isMetadataField("xyz"));
        assertTrue(MetadataFields.isMetadataField(DublinCore.SUBJECT));
        assertTrue(MetadataFields.isMetadataField(TIFF.F_NUMBER.getName()));
    }

}
