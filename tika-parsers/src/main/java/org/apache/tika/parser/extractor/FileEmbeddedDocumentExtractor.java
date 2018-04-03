package org.apache.tika.parser.extractor;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.apache.poi.poifs.filesystem.DirectoryEntry;
import org.apache.poi.poifs.filesystem.DocumentEntry;
import org.apache.poi.poifs.filesystem.DocumentInputStream;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.detect.Detector;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.mime.MimeTypeException;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

public class FileEmbeddedDocumentExtractor implements EmbeddedDocumentExtractor {

	private int count = 0;
	private final TikaConfig config = TikaConfig.getDefaultConfig();
	private File extractDir;

	public FileEmbeddedDocumentExtractor(File extractDir) {
		this.extractDir = extractDir;
	}
	
	public boolean shouldParseEmbedded(Metadata metadata) {
		return true;
	}

	public void parseEmbedded(InputStream inputStream, ContentHandler contentHandler, Metadata metadata, boolean outputHtml) throws SAXException, IOException {
		String name = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);

		if (name == null) {
			name = "file" + count++;
		}
		if (! inputStream.markSupported()) {
			inputStream = TikaInputStream.get(inputStream);
		}
		
		Detector detector = config.getDetector();
		MediaType contentType = detector .detect(inputStream, metadata);

		if (name.indexOf('.')==-1 && contentType!=null) {
			try {
				name += config.getMimeRepository().forName(
						contentType.toString()).getExtension();
			} catch (MimeTypeException e) {
				e.printStackTrace();
			}
		}

		String relID = metadata.get(TikaCoreProperties.EMBEDDED_RELATIONSHIP_ID);
		if (relID != null && !name.startsWith(relID)) {
			name = relID + "_" + name;
		}

		File outputFile = new File(extractDir, FilenameUtils.normalize(name));
		File parent = outputFile.getParentFile();
		if (!parent.exists()) {
			if (!parent.mkdirs()) {
				throw new IOException("unable to create directory \"" + parent + "\"");
			}
		}
		System.out.println("Extracting '"+name+"' ("+contentType+") to " + outputFile);

		try (FileOutputStream os = new FileOutputStream(outputFile)) {
			if (inputStream instanceof TikaInputStream) {
				TikaInputStream tin = (TikaInputStream) inputStream;

				if (tin.getOpenContainer() != null && tin.getOpenContainer() instanceof DirectoryEntry) {
					POIFSFileSystem fs = new POIFSFileSystem();
					copy((DirectoryEntry) tin.getOpenContainer(), fs.getRoot());
					fs.writeFilesystem(os);
				} else {
					IOUtils.copy(inputStream, os);
				}
			} else {
				IOUtils.copy(inputStream, os);
			}
		} catch (Exception e) {
			//
			// being a CLI program messages should go to the stderr too
			//
			String msg = String.format(
					Locale.ROOT,
					"Ignoring unexpected exception trying to save embedded file %s (%s)",
					name,
					e.getMessage()
					);
//			LOG.warn(msg, e);
		}
	}

	protected void copy(DirectoryEntry sourceDir, DirectoryEntry destDir)
			throws IOException {
		for (org.apache.poi.poifs.filesystem.Entry entry : sourceDir) {
			if (entry instanceof DirectoryEntry) {
				// Need to recurse
				DirectoryEntry newDir = destDir.createDirectory(entry.getName());
				copy((DirectoryEntry) entry, newDir);
			} else {
				// Copy entry
				try (InputStream contents =
						new DocumentInputStream((DocumentEntry) entry)) {
					destDir.createDocument(entry.getName(), contents);
				}
			}
		}
	}
}
