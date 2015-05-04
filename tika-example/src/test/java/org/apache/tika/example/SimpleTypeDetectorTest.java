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

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintStream;

import junit.framework.Assert;

import org.junit.Test;

import com.google.common.base.Charsets;

@SuppressWarnings("deprecation")
public class SimpleTypeDetectorTest {

	@Test
	public void testSimpleTypeDetector() throws Exception {
		ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		PrintStream out = System.out;
		System.setOut(new PrintStream(buffer, true, Charsets.UTF_8.name()));

		SimpleTypeDetector.main(new String[] { "pom.xml" });

		System.setOut(out);

		Assert.assertEquals("pom.xml: application/xml",
				buffer.toString(Charsets.UTF_8.name()).trim());
	}

}
