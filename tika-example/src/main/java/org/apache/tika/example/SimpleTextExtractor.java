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

import java.io.File;
import org.apache.tika.Tika;

public class SimpleTextExtractor {

	public static void main(String[] args) throws Exception {
		// Create a Tika instance with the default configuration
		Tika tika = new Tika();

		// Parse all given files and print out the extracted
		// text content
		for (String file : args) {
			String text = tika.parseToString(new File(file));
			System.out.print(text);
		}
	}

}
