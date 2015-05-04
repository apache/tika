/**
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.tika.example;

import java.io.InputStream;

import org.apache.tika.Tika;
import org.apache.tika.detect.CompositeDetector;
import org.apache.tika.detect.Detector;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypesFactory;

public class AdvancedTypeDetector {

	public static String detectWithCustomConfig(String name) throws Exception {
		String config = "/org/apache/tika/mime/tika-mimetypes.xml";
		Tika tika = new Tika(MimeTypesFactory.create(config));
		return tika.detect(name);
	}

	public static String detectWithCustomDetector(String name) throws Exception {
		String config = "/org/apache/tika/mime/tika-mimetypes.xml";
		Detector detector = MimeTypesFactory.create(config);

		Detector custom = new Detector() {
			private static final long serialVersionUID = -5420638839201540749L;

			public MediaType detect(InputStream input, Metadata metadata) {
				String type = metadata.get("my-custom-type-override");
				if (type != null) {
					return MediaType.parse(type);
				} else {
					return MediaType.OCTET_STREAM;
				}
			}
		};

		Tika tika = new Tika(new CompositeDetector(custom, detector));
		return tika.detect(name);
	}

}
