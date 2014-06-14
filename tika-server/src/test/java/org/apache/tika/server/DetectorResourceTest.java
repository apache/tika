package org.apache.tika.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

import org.apache.cxf.jaxrs.JAXRSServerFactoryBean;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.lifecycle.SingletonResourceProvider;
import org.junit.Test;

public class DetectorResourceTest extends CXFTestBase {

	private static final String DETECT_PATH = "/detect";
	private static final String DETECT_STREAM_PATH = DETECT_PATH + "/stream";
	private static final String FOO_CSV = "foo.csv";
	private static final String CDEC_CSV_NO_EXT = "CDEC_WEATHER_2010_03_02";

	@Override
	protected void setUpResources(JAXRSServerFactoryBean sf) {
		  sf.setResourceClasses(DetectorResource.class);
			sf.setResourceProvider(DetectorResource.class,
						new SingletonResourceProvider(new DetectorResource(tika)));

	}

	@Override
	protected void setUpProviders(JAXRSServerFactoryBean sf) {
		  List<Object> providers = new ArrayList<Object>();
		  	       providers.add(new TarWriter());
			       			 providers.add(new ZipWriter());
						 		   providers.add(new TikaExceptionMapper());
								   		     sf.setProviders(providers);

	}

	@Test
	public void testDetectCsvWithExt() throws IllegalStateException, Exception {
	       String url = endPoint + DETECT_STREAM_PATH;
	       	      Response response = WebClient
		      	       		  .create(endPoint + DETECT_STREAM_PATH)
								.type("text/csv")
											.accept("*/*")
														.header("Content-Disposition",
															    "attachment; filename=" + FOO_CSV)
															    		 	      .put(ClassLoader.getSystemResourceAsStream(FOO_CSV));
																		       assertNotNull(response);
																		        String readMime = getStringFromInputStream((InputStream) response
																			       		  .getEntity());
																					   assertEquals("text/csv", readMime);

	}

	@Test
	public void testDetectCsvNoExt() throws IllegalStateException, Exception {
	       String url = endPoint + DETECT_STREAM_PATH;
	       	      Response response = WebClient
		      	       		  .create(endPoint + DETECT_STREAM_PATH)
								.type("text/csv")
											.accept("*/*")
														.header("Content-Disposition",
															    "attachment; filename=" + CDEC_CSV_NO_EXT)
															    		 	      .put(ClassLoader.getSystemResourceAsStream(CDEC_CSV_NO_EXT));
																		       assertNotNull(response);
																		        String readMime = getStringFromInputStream((InputStream) response
																			       		  .getEntity());
																					   assertEquals("text/plain", readMime);

		// now trick it by adding .csv to the end
		   response = WebClient
				.create(endPoint + DETECT_STREAM_PATH)
							.type("text/csv")
										.accept("*/*")
													.header("Content-Disposition",
															   "attachment; filename=" + CDEC_CSV_NO_EXT + ".csv")
															   			     .put(ClassLoader.getSystemResourceAsStream(CDEC_CSV_NO_EXT));
																		      assertNotNull(response);
																		       readMime = getStringFromInputStream((InputStream) response.getEntity());
																		       		assertEquals("text/csv", readMime);

	}
}
