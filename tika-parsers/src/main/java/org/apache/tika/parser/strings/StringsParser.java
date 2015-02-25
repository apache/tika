/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.strings;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.tika.exception.TikaException;
import org.apache.tika.io.IOUtils;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.external.ExternalParser;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

/**
 * Parser that uses the "strings" (or strings-alternative) command to find the
 * printable strings in a object, or other binary, file
 * (application/octet-stream). Useful as "best-effort" parser for files detected
 * as application/octet-stream.
 * 
 * @author gtotaro
 *
 */
public class StringsParser extends AbstractParser {
	/**
	 * Serial version UID
	 */
	private static final long serialVersionUID = 802566634661575025L;

	private static final Set<MediaType> SUPPORTED_TYPES = Collections
			.singleton(MediaType.OCTET_STREAM);

	private static final StringsConfig DEFAULT_STRINGS_CONFIG = new StringsConfig();
	
	private static final FileConfig DEFAULT_FILE_CONFIG = new FileConfig();
	
	/*
	 * This map is organized as follows:
	 * command's pathname (String) -> is it present? (Boolean), does it support -e option? (Boolean)
	 * It stores check results for command and, if present, -e (encoding) option.
	 */
	private static Map<String,Boolean[]> STRINGS_PRESENT = new HashMap<String, Boolean[]>();

	@Override
	public Set<MediaType> getSupportedTypes(ParseContext context) {
		return SUPPORTED_TYPES;
	}

	@Override
	public void parse(InputStream stream, ContentHandler handler,
			Metadata metadata, ParseContext context) throws IOException,
			SAXException, TikaException {
		StringsConfig stringsConfig = context.get(StringsConfig.class, DEFAULT_STRINGS_CONFIG);
		FileConfig fileConfig = context.get(FileConfig.class, DEFAULT_FILE_CONFIG);

		if (!hasStrings(stringsConfig)) {
			return;
		}

		TikaInputStream tis = TikaInputStream.get(stream);
		File input = tis.getFile();

		// Metadata
		metadata.set("strings:min-len", "" + stringsConfig.getMinLength());
		metadata.set("strings:encoding", stringsConfig.toString());
		metadata.set("strings:file_output", doFile(input, fileConfig));

		int totalBytes = 0;

		// Content
		XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);

		xhtml.startDocument();

		totalBytes = doStrings(input, stringsConfig, xhtml);

		xhtml.endDocument();

		// Metadata
		metadata.set("strings:length", "" + totalBytes);
	}

	/**
	 * Checks if the "strings" command is supported.
	 * 
	 * @param config
	 *            {@see StringsConfig} object used for testing the strings
	 *            command.
	 * @return Returns returns {@code true} if the strings command is supported.
	 */
	private boolean hasStrings(StringsConfig config) {
		String stringsProg = config.getStringsPath() + getStringsProg();
		
		if (STRINGS_PRESENT.containsKey(stringsProg)) {
			return STRINGS_PRESENT.get(stringsProg)[0];
		}

		String[] checkCmd = { stringsProg, "--version" };
		try {
			boolean hasStrings = ExternalParser.check(checkCmd);

			boolean encodingOpt = false;

			// Check if the -e option (encoding) is supported
			if (!System.getProperty("os.name").startsWith("Windows")) {
				String[] checkOpt = {stringsProg, "-e", "" + config.getEncoding().get(), "/dev/null"};
				int[] errorValues = {1, 2}; // Exit status code: 1 = general error; 2 = incorrect usage.
				encodingOpt = ExternalParser.check(checkOpt, errorValues);
			}
		
			Boolean[] values = {hasStrings, encodingOpt};
			STRINGS_PRESENT.put(stringsProg, values);

			return hasStrings;
		} catch (NoClassDefFoundError ncdfe) {
			// This happens under OSGi + Fork Parser - see TIKA-1507
			// As a workaround for now, just say we can't use strings
			// TODO Resolve it so we don't need this try/catch block
			Boolean[] values = {false, false};
			STRINGS_PRESENT.put(stringsProg, values);
			return false;
		}
	}

	/**
	 * Checks if the "file" command is supported.
	 * 
	 * @param config
	 * @return
	 */
	private boolean hasFile(FileConfig config) {
		String fileProg = config.getFilePath() + getFileProg();

		String[] checkCmd = { fileProg, "--version" };

		boolean hasFile = ExternalParser.check(checkCmd);

		return hasFile;
	}

	/**
	 * Runs the "strings" command on the given file.
	 * 
	 * @param input
	 *            {@see File} object that represents the file to parse.
	 * @param config
	 *            {@see StringsConfig} object including the strings
	 *            configuration.
	 * @param xhtml
	 *            {@see XHTMLContentHandler} object.
	 * @return the total number of bytes read using the strings command.
	 * @throws IOException
	 *             if any I/O error occurs.
	 * @throws TikaException
	 *             if the parsing process has been interrupted.
	 * @throws SAXException
	 */
	private int doStrings(File input, StringsConfig config,
			XHTMLContentHandler xhtml) throws IOException, TikaException,
			SAXException {
		
		String stringsProg = config.getStringsPath() + getStringsProg();
		
		// Builds the command array
		ArrayList<String> cmdList = new ArrayList<String>(4);
		cmdList.add(stringsProg);
		cmdList.add("-n");
		cmdList.add("" + config.getMinLength());;
		// Currently, encoding option is not supported by Windows (and other) versions
		if (STRINGS_PRESENT.get(stringsProg)[1]) {
			cmdList.add("-e");
			cmdList.add("" + config.getEncoding().get());
		}
		cmdList.add(input.getPath());
		
		String[] cmd = cmdList.toArray(new String[cmdList.size()]);
		
		ProcessBuilder pb = new ProcessBuilder(cmd);
		final Process process = pb.start();

		InputStream out = process.getInputStream();

		FutureTask<Integer> waitTask = new FutureTask<Integer>(
				new Callable<Integer>() {
					public Integer call() throws Exception {
						return process.waitFor();
					}
				});

		Thread waitThread = new Thread(waitTask);
		waitThread.start();

		// Reads content printed out by "strings" command
		int totalBytes = 0;
		totalBytes = extractOutput(out, xhtml);		

		try {
			waitTask.get(config.getTimeout(), TimeUnit.SECONDS);

		} catch (InterruptedException ie) {
			waitThread.interrupt();
			process.destroy();
			Thread.currentThread().interrupt();
			throw new TikaException(StringsParser.class.getName()
					+ " interrupted", ie);

		} catch (ExecutionException ee) {
			// should not be thrown

		} catch (TimeoutException te) {
			waitThread.interrupt();
			process.destroy();
			throw new TikaException(StringsParser.class.getName() + " timeout",
					te);
		}

		return totalBytes;
	}

	/**
	 * Extracts ASCII strings using the "strings" command.
	 * 
	 * @param stream
	 *            {@see InputStream} object used for reading the binary file.
	 * @param xhtml
	 *            {@see XHTMLContentHandler} object.
	 * @return the total number of bytes read using the "strings" command.
	 * @throws SAXException
	 *             if the content element could not be written.
	 * @throws IOException
	 *             if any I/O error occurs.
	 */
	private int extractOutput(InputStream stream, XHTMLContentHandler xhtml)
			throws SAXException, IOException {

		char[] buffer = new char[1024];
		BufferedReader reader = null;
		int totalBytes = 0;

		try {
			reader = new BufferedReader(new InputStreamReader(stream, IOUtils.UTF_8));

			int n = 0;
			while ((n = reader.read(buffer)) != -1) {
				if (n > 0) {
					xhtml.characters(buffer, 0, n);
				}
				totalBytes += n;
			}

		} finally {
			reader.close();
		}

		return totalBytes;
	}

	/**
	 * Runs the "file" command on the given file that aims at providing an
	 * alternative way to determine the file type.
	 * 
	 * @param input
	 *            {@see File} object that represents the file to detect.
	 * @return the file type provided by the "file" command using the "-b"
	 *         option (it stands for "brief mode").
	 * @throws IOException
	 *             if any I/O error occurs.
	 */
	private String doFile(File input, FileConfig config) throws IOException {
		if (!hasFile(config)) {
			return null;
		}
		
		// Builds the command array
		ArrayList<String> cmdList = new ArrayList<String>(3);
		cmdList.add(config.getFilePath() + getFileProg());
		cmdList.add("-b");
		if (config.isMimetype()) {
			cmdList.add("-I");
		}
		cmdList.add(input.getPath());
		
		String[] cmd = cmdList.toArray(new String[cmdList.size()]);

		ProcessBuilder pb = new ProcessBuilder(cmd);
		final Process process = pb.start();

		InputStream out = process.getInputStream();

		BufferedReader reader = null;
		String fileOutput = null;

		try {
			reader = new BufferedReader(new InputStreamReader(out, IOUtils.UTF_8));
			fileOutput = reader.readLine();

		} catch (IOException ioe) {
			// file output not available!
			fileOutput = "";
		} finally {
			reader.close();
		}

		return fileOutput;
	}

	
	public static String getStringsProg() {
		return System.getProperty("os.name").startsWith("Windows") ? "strings.exe" : "strings";
	}
	
	public static String getFileProg() {
		return System.getProperty("os.name").startsWith("Windows") ? "file.exe" : "file";
	}
}
