package org.apache.tika.parser.chm;

import junit.framework.Assert;
import junit.framework.TestCase;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.parser.chm.accessor.ChmPmgiHeader;
import org.apache.tika.parser.chm.core.ChmCommons;

public class TestPmgiHeader extends TestCase {
    ChmPmgiHeader chmPmgiHeader = null;

    public void setUp() throws Exception {
        byte[] data = ChmCommons
                .toByteArray(TikaInputStream.get(TestChmBlockInfo.class
                        .getResource(TestParameters.chmFile)));
        chmPmgiHeader = new ChmPmgiHeader();
        chmPmgiHeader.parse(data, chmPmgiHeader);
    }

    public void testToString() {
        Assert.assertTrue((chmPmgiHeader != null) && (chmPmgiHeader.toString().length() > 0));
    }

    public void testGetFreeSpace() {
        Assert.assertEquals(TestParameters.VP_PMGI_FREE_SPACE, chmPmgiHeader.getFreeSpace());
    }

    public void tearDown() throws Exception {
    }
}
