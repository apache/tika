package org.apache.tika.parser.chm;

import java.io.IOException;

import junit.framework.Assert;
import junit.framework.TestCase;

import org.apache.tika.detect.TestContainerAwareDetector;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;

public class TestChmDocumentInformation extends TestCase {
	private CHMDocumentInformation chmDoc = null;

	public void setUp() throws Exception {
		TikaInputStream stream = TikaInputStream
				.get(TestContainerAwareDetector.class
						.getResource(TestParameters.chmFile));
		chmDoc = CHMDocumentInformation.load(stream);
	}

	public void testGetCHMDocInformation() throws TikaException, IOException {
		Metadata md = new Metadata();
		chmDoc.getCHMDocInformation(md);
		Assert.assertEquals(TestParameters.VP_CHM_MIME_TYPE, md.toString()
				.trim());
	}

	public void testGetText() throws TikaException {
		Assert.assertTrue(chmDoc.getText().contains(
				"The TCard method accepts only numeric arguments"));
	}

	public void tearDown() throws Exception {
	}
}
