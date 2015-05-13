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

import java.io.IOException;
import java.io.InputStream;
import java.security.GeneralSecurityException;
import java.security.Key;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.xml.namespace.QName;

import org.apache.tika.detect.Detector;
import org.apache.tika.detect.XmlRootExtractor;
import org.apache.tika.io.LookaheadInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;

public class EncryptedPrescriptionDetector implements Detector {

	private static final long serialVersionUID = -1709652690773421147L;

	public MediaType detect(InputStream stream, Metadata metadata)
			throws IOException {
		Key key = Pharmacy.getKey();
		MediaType type = MediaType.OCTET_STREAM;

		InputStream lookahead = new LookaheadInputStream(stream, 1024);
		try {
			Cipher cipher = Cipher.getInstance("RSA");

			cipher.init(Cipher.DECRYPT_MODE, key);
			InputStream decrypted = new CipherInputStream(lookahead, cipher);

			QName name = new XmlRootExtractor().extractRootElement(decrypted);
			if (name != null
					&& "http://example.com/xpd".equals(name.getNamespaceURI())
					&& "prescription".equals(name.getLocalPart())) {
				type = MediaType.application("x-prescription");
			}
		} catch (GeneralSecurityException e) {
			// unable to decrypt, fall through
		} finally {
			lookahead.close();
		}
		return type;
	}

}
