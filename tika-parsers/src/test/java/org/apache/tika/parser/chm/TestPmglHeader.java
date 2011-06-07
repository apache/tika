package org.apache.tika.parser.chm;

import java.util.Arrays;
import junit.framework.Assert;
import junit.framework.TestCase;
import org.apache.tika.detect.TestContainerAwareDetector;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.parser.chm.accessor.ChmPmglHeader;
import org.apache.tika.parser.chm.core.ChmCommons;
import org.apache.tika.parser.chm.core.ChmConstants;

public class TestPmglHeader extends TestCase {
	ChmPmglHeader chmPmglHeader = null;

	public void setUp() throws Exception {
		TikaInputStream stream = TikaInputStream
				.get(TestContainerAwareDetector.class
						.getResource(TestParameters.chmFile));
		byte[] data = ChmCommons.toByteArray(stream);
		chmPmglHeader = new ChmPmglHeader();
		chmPmglHeader.parse(Arrays.copyOfRange(data, ChmConstants.START_PMGL,
				ChmConstants.START_PMGL + ChmConstants.CHM_PMGL_LEN + 10),
				chmPmglHeader);
	}

	public void testToString() {
		Assert.assertTrue((chmPmglHeader != null)
				&& chmPmglHeader.toString().length() > 0);
	}

	public void testChmPmglHeaderGet() {
		Assert.assertEquals(TestParameters.VP_PMGL_SIGNATURE, new String(
				chmPmglHeader.getSignature()));
	}

	public void testGetBlockNext() {
		Assert.assertEquals(TestParameters.VP_PMGL_BLOCK_NEXT,
				chmPmglHeader.getBlockNext());
	}

	public void testGetBlockPrev() {
		Assert.assertEquals(TestParameters.VP_PMGL_BLOCK_PREV,
				chmPmglHeader.getBlockPrev());
	}

	public void testGetFreeSpace() {
		Assert.assertEquals(TestParameters.VP_PMGL_FREE_SPACE,
				chmPmglHeader.getFreeSpace());
	}

	public void testGetUnknown0008() {
		Assert.assertEquals(TestParameters.VP_PMGL_UNKNOWN_008,
				chmPmglHeader.getUnknown0008());
	}

	public void tearDown() throws Exception {
	}
}
