package org.apache.tika.parser.ntfs;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import org.apache.tika.exception.TikaException;
import org.apache.tika.extractor.EmbeddedDocumentExtractor;
import org.apache.tika.extractor.ParsingEmbeddedDocumentExtractor;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.mime.MediaType;
import org.apache.tika.parser.AbstractParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

// Sleuth Kit imports - will need to be adjusted based on actual class names and availability
import org.sleuthkit.datamodel.AbstractFile;
import org.sleuthkit.datamodel.Content;
import org.sleuthkit.datamodel.FileSystem;
import org.sleuthkit.datamodel.Image;
import org.sleuthkit.datamodel.ReadContentInputStream;
import org.sleuthkit.datamodel.SleuthkitCase;
import org.sleuthkit.datamodel.TskCoreException;
import org.sleuthkit.datamodel.TskDataException;


public class NTFSParser extends AbstractParser {

    private static final long serialVersionUID = 1L;
    private static final Set<MediaType> SUPPORTED_TYPES =
            Collections.singleton(MediaType.application("x-ntfs-image"));

    @Override
    public Set<MediaType> getSupportedTypes(ParseContext context) {
        return SUPPORTED_TYPES;
    }

    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata, ParseContext context)
            throws IOException, SAXException, TikaException {

        TemporaryResources tmp = new TemporaryResources();
        Path imagePath;
        TikaInputStream tis = TikaInputStream.cast(stream);

        if (tis != null && tis.hasFile()) {
            imagePath = tis.getPath();
        } else {
            // Stream to a temporary file as Sleuth Kit likely needs file access
            Path tmpFile = tmp.createTemporaryFile();
            Files.copy(stream, tmpFile, StandardCopyOption.REPLACE_EXISTING);
            imagePath = tmpFile;
        }

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();

        SleuthkitCase skCase = null;
        try {
            // TODO: Check if a "dummy" case is sufficient or if more setup is needed.
            // A database is usually created by SleuthkitCase.newCase(), which might be heavyweight.
            // For single image parsing, a "no-database" approach might be preferable if available.
            // For now, let's assume a unique case ID is needed.
            String caseDbPath = tmp.createTemporaryFile().toAbsolutePath().toString() + ".db";
            skCase = SleuthkitCase.newCase(caseDbPath);


            // Add the image to the case
            // The image path needs to be a string.
            // Timezone and other parameters might need to be configured.
            String imageName = imagePath.toAbsolutePath().toString();
            // TODO: Determine the correct image type. For now, using AUTO_DETECT.
            // Consider TSK_IMG_TYPE_ENUM.TSK_IMG_TYPE_RAW for raw images if applicable.
            Image image = skCase.addImage(imageName, org.sleuthkit.datamodel.TskData.TSK_IMG_TYPE_DETECT, 0, "");


            // Get file systems from the image
            List<FileSystem> fileSystems = image.getFileSystems();
            if (fileSystems.isEmpty()) {
                throw new TikaException("No file systems found in the image: " + imageName);
            }

            for (FileSystem fs : fileSystems) {
                metadata.set(Metadata.FS_NAME, fs.getFsType().getName()); // Example: "NTFS"
                // You can add more file system level metadata here if needed

                // Iterate over files and directories.
                // The root directory in Sleuth Kit usually has a specific ID.
                // fs.getRootDirectory() might be the starting point.
                // Need to handle this recursively or iteratively.
                List<AbstractFile> rootObjects = fs.getRootDirectory().getChildren();
                for (AbstractFile rootObject : rootObjects) {
                    processFileOrDirectory(rootObject, xhtml, metadata, context, tmp);
                }
            }

        } catch (TskCoreException | TskDataException e) {
            throw new TikaException("Sleuth Kit processing error: " + e.getMessage(), e);
        } finally {
            if (skCase != null) {
                try {
                    skCase.close();
                } catch (TskCoreException e) {
                    // Log or handle the exception on close if necessary
                    // For example, metadata.add(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING, "Error closing SleuthkitCase: " + e.getMessage());
                }
            }
            tmp.dispose(); // Deletes temporary files
        }

        xhtml.endDocument();
    }

    private void processFileOrDirectory(AbstractFile fileOrDir, XHTMLContentHandler xhtml,
                                        Metadata parentMetadata, ParseContext context, TemporaryResources tmp)
            throws IOException, SAXException, TikaException, TskCoreException {

        Metadata entryMetadata = new Metadata();
        entryMetadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, fileOrDir.getName());
        entryMetadata.set(Metadata.CONTENT_LENGTH, String.valueOf(fileOrDir.getSize()));
        entryMetadata.set(TikaCoreProperties.CREATED, String.valueOf(fileOrDir.getCrtime()));
        entryMetadata.set(TikaCoreProperties.MODIFIED, String.valueOf(fileOrDir.getMtime()));
        entryMetadata.set(TikaCoreProperties.DESCRIPTION, fileOrDir.getMetaType().toString()); // e.g., TSK_FS_META_TYPE_DIR or TSK_FS_META_TYPE_REG

        if (fileOrDir.isDir()) {
            entryMetadata.set(TikaCoreProperties.CONTENT_TYPE, MediaType.DIRECTORY.toString());
            // Optionally add an entry for the directory itself if desired
            // For now, we'll just recurse.

            List<AbstractFile> children = fileOrDir.getChildren();
            for (AbstractFile child : children) {
                processFileOrDirectory(child, xhtml, entryMetadata, context, tmp);
            }
        } else if (fileOrDir.isFile()) {
            // For files, extract content and potentially pass to an embedded document extractor
            EmbeddedDocumentExtractor extractor = context.get(EmbeddedDocumentExtractor.class);
            if (extractor == null) {
                extractor = new ParsingEmbeddedDocumentExtractor(context);
            }

            if (extractor.shouldParseEmbedded(entryMetadata)) {
                try (InputStream embeddedStream = new ReadContentInputStream(fileOrDir)) {
                    extractor.parseEmbedded(embeddedStream, xhtml.getSAXHandler(), entryMetadata, true);
                } catch (TskDataException e) {
                    // Handle cases where content might not be readable (e.g., sparse, encrypted, resident in MFT)
                    // For now, just log or add a metadata field indicating an issue.
                    entryMetadata.set(TikaCoreProperties.TIKA_META_EXCEPTION_WARNING,
                                      "Could not read content for file: " + fileOrDir.getName() + " - " + e.getMessage());
                }
            }
        }
        // What about symbolic links, etc.? Sleuth Kit should handle these.
        // fileOrDir.getMetaType() can give more info.
    }
}
