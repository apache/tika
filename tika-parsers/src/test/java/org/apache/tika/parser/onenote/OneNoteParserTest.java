package org.apache.tika.parser.onenote;

import org.apache.commons.lang3.StringUtils;
import org.apache.tika.TikaTest;
import org.apache.tika.metadata.Metadata;
import org.junit.Assert;
import org.junit.Test;

public class OneNoteParserTest extends TikaTest {

    /**
     * This is the sample document that is automatically created from onenote 2013.
     */
    @Test
    public void testOneNote2013Doc1() throws Exception {
        Metadata metadata = new Metadata();
        String txtOut = getText("Sample1.one", metadata);

        Assert.assertFalse("Should not include font names in the text", StringUtils.contains(txtOut, "Calibri"));
        Assert.assertFalse("Should not include UTF-16 property values that are garbage", StringUtils.contains(txtOut, "夂菲䈿Ǡ�"));

        // No need to assert the routine garbage that shows up on all onenote files.
    }

    @Test
    public void testOneNote2013Doc2() throws Exception {
        Metadata metadata = new Metadata();
        String txtOut = getText("Section1SheetTitle.one", metadata);

        Assert.assertTrue(txtOut.contains("wow this is neat"));
        Assert.assertTrue(txtOut.contains("neat info about totally killin it bro"));
        Assert.assertTrue(txtOut.contains("Section1TextArea1"));
        Assert.assertTrue(txtOut.contains("Section1HeaderTitle"));
        Assert.assertTrue(txtOut.contains("Section1TextArea2"));

        Assert.assertFalse("Should not include font names in the text", txtOut.contains("Calibri"));
        Assert.assertFalse("Should not include UTF-16 property values that are garbage", txtOut.contains("夂菲䈿Ǡ�"));
    }

    @Test
    public void testOneNote2013Doc3() throws Exception {
        Metadata metadata = new Metadata();
        String txtOut = getText("Section2SheetTitle.one", metadata);

        Assert.assertTrue(txtOut.contains("awesome information about sports or some crap like that."));
        Assert.assertTrue(txtOut.contains("Quit doing horrible things to me. Dang you. "));
        Assert.assertTrue(txtOut.contains("Section2TextArea1"));
        Assert.assertTrue(txtOut.contains("Section2HeaderTitle"));
        Assert.assertTrue(txtOut.contains("Section2TextArea2"));

        Assert.assertFalse("Should not include font names in the text", txtOut.contains("Calibri"));
        Assert.assertFalse("Should not include UTF-16 property values that are garbage", txtOut.contains("夂菲䈿Ǡ�"));
    }

    @Test
    public void testOneNote2013Doc4() throws Exception {
        Metadata metadata = new Metadata();
        String txtOut = getText("Section3SheetTitle.one", metadata);

        Assert.assertTrue(txtOut.contains("way too much information about poptarts to handle."));
        Assert.assertTrue(txtOut.contains("Section3TextArea1"));
        Assert.assertTrue(txtOut.contains("Section3HeaderTitle"));
        Assert.assertTrue(txtOut.contains("Section3TextArea2"));

        Assert.assertFalse("Should not include font names in the text", txtOut.contains("Calibri"));
        Assert.assertFalse("Should not include UTF-16 property values that are garbage", txtOut.contains("夂菲䈿Ǡ�"));
    }
}
