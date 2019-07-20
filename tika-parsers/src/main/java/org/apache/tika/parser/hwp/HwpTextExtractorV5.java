/*
 * Licensed to the Apache Software Foundation (ASF) under one or more

 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser.hwp;

import java.io.EOFException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

import javax.crypto.Cipher;
import javax.crypto.CipherInputStream;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.SecretKeySpec;

import org.apache.poi.hpsf.NoPropertySetStreamException;
import org.apache.poi.hpsf.Property;
import org.apache.poi.hpsf.PropertySet;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DirectoryNode;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.Entry;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.util.IOUtils;
import org.apache.poi.util.LittleEndian;
import org.apache.tika.exception.EncryptedDocumentException;
import org.apache.tika.exception.TikaException;
import org.apache.tika.exception.UnsupportedFormatException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.Office;
import org.apache.tika.metadata.OfficeOpenXMLCore;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.sax.XHTMLContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class HwpTextExtractorV5 {
	protected static Logger log = LoggerFactory
			.getLogger(HwpTextExtractorV5.class);

	private static final byte[] HWP_V5_SIGNATURE = "HWP Document File"
			.getBytes();

	private static final int[] HWP_CONTROL_CHARS = new int[] { 0, 10, 13, 24,
			25, 26, 27, 28, 29, 30, 31 };
	private static final int[] HWP_INLINE_CHARS = new int[] { 4, 5, 6, 7, 8, 9,
			19, 20 };
	private static final int[] HWP_EXTENDED_CHARS = new int[] { 1, 2, 3, 11,
			12, 14, 15, 16, 17, 18, 21, 22, 23 };

	private static final int HWPTAG_BEGIN = 0x010;


	/**
	 * extract Text from HWP Stream.
	 * 
	 * @param source
	 * @param writer
	 * @return
	 * @throws FileNotFoundException
	 * @throws IOException
	 * @throws SAXException 
	 */
	public void extract(InputStream source, Metadata metadata, XHTMLContentHandler xhtml)
			throws FileNotFoundException, IOException,
			TikaException, SAXException {
		if (source == null || xhtml == null)
			throw new IllegalArgumentException();

		POIFSFileSystem fs = null;
		try {
			fs = new POIFSFileSystem(source);

			DirectoryNode root = fs.getRoot();

			extract0(root, metadata, xhtml);

		} catch (IOException e) {
			throw new TikaException(
					"error occurred when parsing HWP Format, It may not HWP Format.", e);
		} finally {
			IOUtils.closeQuietly(fs);
		}
	}

	private void extract0(DirectoryNode root, Metadata metadata, XHTMLContentHandler xhtml)
			throws IOException, SAXException, TikaException {

		Entry headerEntry = root.getEntry("FileHeader");
		if (!headerEntry.isDocumentEntry())
			throw new UnsupportedFormatException("cannot parse the File Header");

		FileHeader header = getHeader(headerEntry);

		if (header == null)
			throw new UnsupportedFormatException("cannot parse the File Header");
		if (header.encrypted)
			throw new EncryptedDocumentException("document is encrypted");

		parseSummaryInformation(root, metadata);
		
		if (header.viewtext) {
			parseViewText(header, root, xhtml);
		} else {
			parseBodyText(header, root, xhtml);
		}
		
	}

	private void parseSummaryInformation(DirectoryNode root, Metadata metadata) throws TikaException {

		try {
			Entry summaryEntry = root.getEntry("\u0005HwpSummaryInformation");
			
			parseSummaryInformation(summaryEntry, metadata);
//			setMetadataWithSummaryInformation(si, metadata);
		} catch (NoPropertySetStreamException | IOException e) {
			throw new UnsupportedFormatException(
					"cannot parse the Summary Information");
		}
		
	}
	
	private void parseSummaryInformation(Entry summaryEntry, Metadata metadata) throws IOException, NoPropertySetStreamException {
		
		// SummaryInformation si = new SummaryInformation();
		
		DocumentInputStream summaryStream = new DocumentInputStream(
				(DocumentEntry) summaryEntry);
		
		PropertySet ps = new PropertySet(summaryStream);
		
		Property[] props = ps.getProperties();
		
		for(Property prop : props) {
			int propID = (int)prop.getID();
			Object value = prop.getValue();
			
			switch(propID) {
			case 2:
//				 si.setTitle((String)value);
				metadata.set(TikaCoreProperties.TITLE, (String)value);
				break;
			case 3:
//				 si.setSubject((String)value);
				metadata.set(OfficeOpenXMLCore.SUBJECT, (String)value);
				break;
			case 4:
//				 si.setAuthor((String)value);
				metadata.set(TikaCoreProperties.CREATOR, (String)value);
				break;
			case 5:
//				si.setKeywords((String)value);
				metadata.set(Office.KEYWORDS, (String)value);
				break;
			case 6:
//				si.setComments((String)value);
				metadata.set(TikaCoreProperties.COMMENTS, (String)value);
				break;
			case 8:
//				si.setLastAuthor((String)value);
				metadata.set(TikaCoreProperties.MODIFIER, (String)value);
				break;
			case 12:
//				si.setCreateDateTime((Date)value);
				metadata.set(TikaCoreProperties.CREATED, (Date)value);
				break;
			case 13:
//				si.setLastSaveDateTime((Date)value);
				metadata.set(TikaCoreProperties.MODIFIED, (Date)value);
				break;
//			case 14:
//				si.setPageCount((int)value);
//				break;
			default:
			}
		}
		
//		return si;
	}
	
//	private void setMetadataWithSummaryInformation(SummaryInformation si, Metadata metadata) {
//		metadata.set(TikaCoreProperties.CREATOR, si.getAuthor());
//		metadata.set(TikaCoreProperties.COMMENTS, si.getComments());
//		metadata.set(TikaCoreProperties.MODIFIER, si.getLastAuthor());
//		metadata.set(TikaCoreProperties.CREATED, si.getCreateDateTime());
//		metadata.set(TikaCoreProperties.MODIFIED, si.getLastSaveDateTime());
//		metadata.set(Office.KEYWORDS, si.getKeywords());
//		metadata.set(OfficeOpenXMLCore.SUBJECT, si.getSubject());
//	}
	
	/**
	 * extract the HWP File Header
	 * 
	 * @param fs
	 * @return
	 * @throws IOException
	 */
	private FileHeader getHeader(Entry headerEntry) throws IOException {
		// confirm signature
		byte[] header = new byte[256]; // the length of File header is 256
		DocumentInputStream headerStream = new DocumentInputStream(
				(DocumentEntry) headerEntry);
		try {
			int read = headerStream.read(header);
			if (read != 256
					|| !Arrays.equals(HWP_V5_SIGNATURE, Arrays.copyOfRange(
							header, 0, HWP_V5_SIGNATURE.length)))
				return null;
		} finally {
			headerStream.close();
		}

		FileHeader fileHeader = new FileHeader();

		// version. debug
		fileHeader.version = HwpVersion.parseVersion(LittleEndian.getUInt(
				header, 32));
		long flags = LittleEndian.getUInt(header, 36);
		log.debug("Flags={}", Long.toBinaryString(flags).replace(' ', '0'));

		fileHeader.compressed = (flags & 0x01) == 0x01;
		fileHeader.encrypted = (flags & 0x02) == 0x02;
		fileHeader.viewtext = (flags & 0x04) == 0x04;

		return fileHeader;
	}

	/**
	 * extract Text
	 * 
	 * @param writer
	 * @param source
	 * 
	 * @return
	 * @throws IOException
	 * @throws SAXException 
	 */
	private void parseBodyText(FileHeader header, DirectoryNode root,
			XHTMLContentHandler xhtml) throws IOException, SAXException {
		// BodyText 읽기
		Entry bodyText = root.getEntry("BodyText");
		if (bodyText == null || !bodyText.isDirectoryEntry())
			throw new IOException("Invalid BodyText");

		Iterator<Entry> iterator = ((DirectoryEntry) bodyText).getEntries();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (entry.getName().startsWith("Section")
					&& entry instanceof DocumentEntry) {
				log.debug("extract {}", entry.getName());

				InputStream input = new DocumentInputStream(
						(DocumentEntry) entry);
				if (header.compressed)
					input = new InflaterInputStream(input, new Inflater(true));

				HwpStreamReader reader = new HwpStreamReader(input);

				parse(reader, xhtml);
				
			} else {
				log.warn("Unknown Entry '{}'({})", entry.getName(), entry);
			}
		}
	}

	/**
	 * 텍스트 추출
	 * 
	 * @param writer
	 * @param source
	 * 
	 * @return
	 * @throws IOException
	 */
	private void parseViewText(FileHeader header, DirectoryNode root,
			XHTMLContentHandler xhtml) throws IOException {
		// read BodyText
		Entry bodyText = root.getEntry("ViewText");
		if (bodyText == null || !bodyText.isDirectoryEntry())
			throw new IOException("Invalid ViewText");

		Iterator<Entry> iterator = ((DirectoryEntry) bodyText).getEntries();
		while (iterator.hasNext()) {
			Entry entry = iterator.next();
			if (entry.getName().startsWith("Section")
					&& entry instanceof DocumentEntry) {
				log.debug("extract {}", entry.getName());

				InputStream input = new DocumentInputStream(
						(DocumentEntry) entry);
	
				try {
					Key key = readKey(input);
					input = createDecryptStream(input, key);
					if (header.compressed)
						input = new InflaterInputStream(input, new Inflater(
								true));

					HwpStreamReader sectionStream = new HwpStreamReader(input);
					parse(sectionStream, xhtml);
				} catch (InvalidKeyException e) {
					throw new IOException(e);
				} catch (NoSuchAlgorithmException e) {
					throw new IOException(e);
				} catch (NoSuchPaddingException e) {
					throw new IOException(e);
				} catch (SAXException e) {
					throw new IOException(e);
				} finally {
					IOUtils.closeQuietly(input);
				}
			} else {
				log.warn("unknown Entry '{}'({})", entry.getName(), entry);
			}
		}
	}

	private Key readKey(InputStream input) throws IOException {
		byte[] data = new byte[260];

		if (IOUtils.readFully(input, data, 0, 4) != 4)// TAG,
			throw new EOFException(); 

		if (IOUtils.readFully(input, data, 0, 256) != 256)
			throw new EOFException();

		SRand srand = new SRand(LittleEndian.getInt(data));
		byte xor = 0;
		for (int i = 0, n = 0; i < 256; i++, n--) {
			if (n == 0) {
				xor = (byte) (srand.rand() & 0xFF);
				n = (int) ((srand.rand() & 0xF) + 1);
			}
			if (i >= 4) {
				data[i] = (byte) ((data[i]) ^ (xor));
			}
		}

		int offset = 4 + (data[0] & 0xF); // 4 + (0~15) ?
		byte[] key = Arrays.copyOfRange(data, offset, offset + 16);

		SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
		return secretKey;
	}

	public InputStream createDecryptStream(InputStream input, Key key)
			throws IOException, NoSuchAlgorithmException,
			NoSuchPaddingException, InvalidKeyException {
		Cipher cipher = null;

		cipher = Cipher.getInstance("AES/ECB/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, key);

		return new CipherInputStream(input, cipher);
	}

	/**
	 * extract characters from Section stream
	 * 
	 * @param reader
	 * @param writer
	 * @throws IOException
	 * @throws SAXException 
	 */
	private void parse(HwpStreamReader reader, XHTMLContentHandler xhtml)
			throws IOException, SAXException {
		StringBuffer buf = new StringBuffer(1024);
		TagInfo tag = new TagInfo();

		while (true) {
			if (!readTag(reader, tag))
				break;

			buf.setLength(0);
			if (HWPTAG_BEGIN + 50 == tag.id) {
				writeParaHeader(reader, tag.length, buf);
			} else if (HWPTAG_BEGIN + 51 == tag.id) {
				if (tag.length % 2 != 0)
					throw new IOException("Invalid block size");

				writeParaText(reader, tag.length, buf);

				if (buf.length() > 0) {
					buf.append('\n');
					
					xhtml.startElement("p");
					xhtml.characters(buf.toString());
					xhtml.endElement("p");
				}
			} else {
				reader.ensureSkip(tag.length);
			}

//			if (buf.length() > 0) {
//				log.debug("TAG[{}]({}):{} [{}]", new Object[] { tag.id,
//						tag.level, tag.length, buf });
//			}
		}
	}

	private void writeParaHeader(HwpStreamReader reader, long length,
			StringBuffer buf) throws IOException {
		reader.ensureSkip(length);
	}

	/**
	 * transfer character stream of HWPTAG_PARA_TEXT to STRING
	 * 
	 * @param reader
	 * @param datasize
	 * @param buf
	 * @throws IOException
	 */
	private void writeParaText(HwpStreamReader reader, long datasize,
			StringBuffer buf) throws IOException {
		int[] chars = reader.uint16((int) (datasize / 2));

		for (int index = 0; index < chars.length; index++) {
			int ch = chars[index];
			if (Arrays.binarySearch(HWP_INLINE_CHARS, ch) >= 0) {
				if (ch == 9) {
					buf.append('\t');
				}
				index += 7;
			} else if (Arrays.binarySearch(HWP_EXTENDED_CHARS, ch) >= 0) {
				index += 7;
			} else if (Arrays.binarySearch(HWP_CONTROL_CHARS, ch) >= 0) {
				buf.append(' ');
			} else {
				buf.append((char) ch);
			}
		}
	}

	private boolean readTag(HwpStreamReader reader, TagInfo tag)
			throws IOException {
		// see p.24 of hwp 5.0 format guide

		long recordHeader = reader.uint32();
		if (recordHeader == -1)
			return false;

		tag.id = recordHeader & 0x3FF;
		tag.level = (recordHeader >> 10) & 0x3FF;
		tag.length = (recordHeader >> 20) & 0xFFF;

		// see p.24 of hwp 5.0 format guide
		if (tag.length == 0xFFF)
			tag.length = reader.uint32();

		return true;
	}

	private static class SRand {
		private int random_seed;

		private SRand(int seed) {
			random_seed = seed;
		}

		private int rand() {
			random_seed = (random_seed * 214013 + 2531011) & 0xFFFFFFFF;
			return (random_seed >> 16) & 0x7FFF;
		}
	}
	
	static class FileHeader {
		HwpVersion version;
		boolean compressed; // bit 0
		boolean encrypted; // bit 1
		boolean viewtext; // bit 2
	}

	static class TagInfo {
		long id;
		long level;
		long length;
	}

	static class HwpVersion {
		int m;
		int n;
		int p;
		int r;

		public String toString() {
			return String.format("%d.%d.%d.%d", m, n, p, r);
		}

		public static HwpVersion parseVersion(long longVersion) {
			HwpVersion version = new HwpVersion();
			version.m = (int) ((longVersion & 0xFF000000L) >> 24);
			version.n = (int) ((longVersion & 0x00FF0000L) >> 16);
			version.p = (int) ((longVersion & 0x0000FF00L) >> 8);
			version.r = (int) ((longVersion & 0x000000FFL));
			return version;
		}
	}

}
