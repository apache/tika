/*
Copyright (c) 2013 James Ahlborn

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
*/

package org.apache.tika.parser.microsoft;

import java.io.ByteArrayInputStream;
import java.io.Closeable;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.sql.Blob;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.text.Normalizer;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

import com.healthmarketscience.jackcess.DataType;
import com.healthmarketscience.jackcess.util.OleBlob;
import static com.healthmarketscience.jackcess.util.OleBlob.*;

import com.healthmarketscience.jackcess.impl.ByteUtil;
import com.healthmarketscience.jackcess.impl.PageChannel;

/**
 * Utility code for working with OLE data.
 * Temporary workaround until POI 4.0.0 is released and Jackcess is updated
 *
 *
 * @author James Ahlborn
 * @usage _advanced_class_
 * @deprecated this class will be removed in Tika >= 1.20
 */
@Deprecated
class JackcessOleUtil {


        /**
         * Interface used to allow optional inclusion of the poi library for working
         * with compound ole data.
         */
        interface CompoundPackageFactory
        {
            public ContentImpl createCompoundPackageContent(
                    OleBlobImpl blob, String prettyName, String className, String typeName,
                    ByteBuffer blobBb, int dataBlockLen);
        }

        private static final int PACKAGE_SIGNATURE = 0x1C15;
        private static final Charset OLE_CHARSET = Charset.forName("US-ASCII");
        private static final Charset OLE_UTF_CHARSET = Charset.forName("UTF-16LE");
        private static final byte[] COMPOUND_STORAGE_SIGNATURE =
                {(byte)0xd0,(byte)0xcf,(byte)0x11,(byte)0xe0,
                        (byte)0xa1,(byte)0xb1,(byte)0x1a,(byte)0xe1};
        private static final String SIMPLE_PACKAGE_TYPE = "Package";
        private static final int PACKAGE_OBJECT_TYPE = 0x02;
        private static final int OLE_VERSION = 0x0501;
        private static final int OLE_FORMAT = 0x02;
        private static final int PACKAGE_STREAM_SIGNATURE = 0x02;
        private static final int PS_EMBEDDED_FILE = 0x030000;
        private static final int PS_LINKED_FILE = 0x010000;
        private static final Set<ContentType> WRITEABLE_TYPES = EnumSet.of(
                ContentType.LINK, ContentType.SIMPLE_PACKAGE, ContentType.OTHER);
        private static final byte[] NO_DATA = new byte[0];
        private static final int LINK_HEADER = 0x01;
        private static final byte[] PACKAGE_FOOTER = {
                0x01, 0x05, 0x00, 0x00, 0x00, 0x00,
                0x00, 0x00, 0x01, (byte)0xAD, 0x05, (byte)0xFE
        };

        // regex pattern which matches all the crazy extra stuff in unicode
        private static final Pattern UNICODE_ACCENT_PATTERN =
                Pattern.compile("[\\p{InCombiningDiacriticalMarks}\\p{IsLm}\\p{IsSk}]+");

        private static final CompoundPackageFactory COMPOUND_FACTORY;

        static {
            CompoundPackageFactory compoundFactory = null;
            try {
                compoundFactory = (CompoundPackageFactory)
                        Class.forName("org.apache.tika.parser.microsoft.JackcessCompoundOleUtil")
                                .newInstance();
            } catch(Throwable t) {
                // must not have poi, will load compound ole data as "other"
            }
            COMPOUND_FACTORY = compoundFactory;
        }

        /**
         * Parses an access database blob structure and returns an appropriate
         * OleBlob instance.
         */
        public static OleBlob parseBlob(byte[] bytes) {
            return new OleBlobImpl(bytes);
        }

        /**
         * Creates a new OlBlob instance using the given information.
         */
        public static OleBlob createBlob(Builder oleBuilder)
                throws IOException
        {
            try {

                if(!WRITEABLE_TYPES.contains(oleBuilder.getType())) {
                    throw new IllegalArgumentException(
                            "Cannot currently create ole values of type " +
                                    oleBuilder.getType());
                }

                long contentLen = oleBuilder.getContentLength();
                byte[] contentBytes = oleBuilder.getBytes();
                InputStream contentStream = oleBuilder.getStream();
                byte[] packageStreamHeader = NO_DATA;
                byte[] packageStreamFooter = NO_DATA;

                switch(oleBuilder.getType()) {
                    case LINK:
                        packageStreamHeader = writePackageStreamHeader(oleBuilder);

                        // link "content" is file path
                        contentBytes = getZeroTermStrBytes(oleBuilder.getFilePath());
                        contentLen = contentBytes.length;
                        break;

                    case SIMPLE_PACKAGE:
                        packageStreamHeader = writePackageStreamHeader(oleBuilder);
                        packageStreamFooter = writePackageStreamFooter(oleBuilder);
                        break;

                    case OTHER:
                        // nothing more to do
                        break;
                    default:
                        throw new RuntimeException("unexpected type " + oleBuilder.getType());
                }

                long payloadLen = packageStreamHeader.length + packageStreamFooter.length +
                        contentLen;
                byte[] packageHeader = writePackageHeader(oleBuilder, payloadLen);

                long totalOleLen = packageHeader.length + PACKAGE_FOOTER.length +
                        payloadLen;
                if(totalOleLen > DataType.OLE.getMaxSize()) {
                    throw new IllegalArgumentException("Content size of " + totalOleLen +
                            " is too large for ole column");
                }

                byte[] oleBytes = new byte[(int)totalOleLen];
                ByteBuffer bb = PageChannel.wrap(oleBytes);
                bb.put(packageHeader);
                bb.put(packageStreamHeader);

                if(contentLen > 0L) {
                    if(contentBytes != null) {
                        bb.put(contentBytes);
                    } else {
                        byte[] buf = new byte[8192];
                        int numBytes = 0;
                        while((numBytes = contentStream.read(buf)) >= 0) {
                            bb.put(buf, 0, numBytes);
                        }
                    }
                }

                bb.put(packageStreamFooter);
                bb.put(PACKAGE_FOOTER);

                return parseBlob(oleBytes);

            } finally {
                ByteUtil.closeQuietly(oleBuilder.getStream());
            }
        }

        private static byte[] writePackageHeader(Builder oleBuilder,
                                                 long contentLen) {

            byte[] prettyNameBytes = getZeroTermStrBytes(oleBuilder.getPrettyName());
            String className = oleBuilder.getClassName();
            String typeName = oleBuilder.getTypeName();
            if(className == null) {
                className = typeName;
            } else if(typeName == null) {
                typeName = className;
            }
            byte[] classNameBytes = getZeroTermStrBytes(className);
            byte[] typeNameBytes = getZeroTermStrBytes(typeName);

            int packageHeaderLen = 20 + prettyNameBytes.length + classNameBytes.length;

            int oleHeaderLen = 24 + typeNameBytes.length;

            byte[] headerBytes = new byte[packageHeaderLen + oleHeaderLen];

            ByteBuffer bb = PageChannel.wrap(headerBytes);

            // write outer package header
            bb.putShort((short)PACKAGE_SIGNATURE);
            bb.putShort((short)packageHeaderLen);
            bb.putInt(PACKAGE_OBJECT_TYPE);
            bb.putShort((short)prettyNameBytes.length);
            bb.putShort((short)classNameBytes.length);
            int prettyNameOff = bb.position() + 8;
            bb.putShort((short)prettyNameOff);
            bb.putShort((short)(prettyNameOff + prettyNameBytes.length));
            bb.putInt(-1);
            bb.put(prettyNameBytes);
            bb.put(classNameBytes);

            // put ole header
            bb.putInt(OLE_VERSION);
            bb.putInt(OLE_FORMAT);
            bb.putInt(typeNameBytes.length);
            bb.put(typeNameBytes);
            bb.putLong(0L);
            bb.putInt((int)contentLen);

            return headerBytes;
        }

        private static byte[] writePackageStreamHeader(Builder oleBuilder) {

            byte[] fileNameBytes = getZeroTermStrBytes(oleBuilder.getFileName());
            byte[] filePathBytes = getZeroTermStrBytes(oleBuilder.getFilePath());

            int headerLen = 6 + fileNameBytes.length + filePathBytes.length;

            if(oleBuilder.getType() == ContentType.SIMPLE_PACKAGE) {

                headerLen += 8 + filePathBytes.length;

            } else {

                headerLen += 2;
            }

            byte[] headerBytes = new byte[headerLen];
            ByteBuffer bb = PageChannel.wrap(headerBytes);
            bb.putShort((short)PACKAGE_STREAM_SIGNATURE);
            bb.put(fileNameBytes);
            bb.put(filePathBytes);

            if(oleBuilder.getType() == ContentType.SIMPLE_PACKAGE) {
                bb.putInt(PS_EMBEDDED_FILE);
                bb.putInt(filePathBytes.length);
                bb.put(filePathBytes, 0, filePathBytes.length);
                bb.putInt((int) oleBuilder.getContentLength());
            } else {
                bb.putInt(PS_LINKED_FILE);
                bb.putShort((short)LINK_HEADER);
            }

            return headerBytes;
        }

        private static byte[] writePackageStreamFooter(Builder oleBuilder) {

            // note, these are _not_ zero terminated
            byte[] fileNameBytes = oleBuilder.getFileName().getBytes(OLE_UTF_CHARSET);
            byte[] filePathBytes = oleBuilder.getFilePath().getBytes(OLE_UTF_CHARSET);

            int footerLen = 12 + (filePathBytes.length * 2) + fileNameBytes.length;

            byte[] footerBytes = new byte[footerLen];
            ByteBuffer bb = PageChannel.wrap(footerBytes);

            bb.putInt(filePathBytes.length/2);
            bb.put(filePathBytes);
            bb.putInt(fileNameBytes.length/2);
            bb.put(fileNameBytes);
            bb.putInt(filePathBytes.length/2);
            bb.put(filePathBytes);

            return footerBytes;
        }

        /**
         * creates the appropriate ContentImpl for the given blob.
         */
        private static ContentImpl parseContent(OleBlobImpl blob)
                throws IOException
        {
            ByteBuffer bb = PageChannel.wrap(blob.getBytes());

            if((bb.remaining() < 2) || (bb.getShort() != PACKAGE_SIGNATURE)) {
                return new UnknownContentImpl(blob);
            }

            // read outer package header
            int headerSize = bb.getShort();
            /* int objType = */ bb.getInt();
            int prettyNameLen = bb.getShort();
            int classNameLen = bb.getShort();
            int prettyNameOff = bb.getShort();
            int classNameOff = bb.getShort();
            /* int objSize = */ bb.getInt();
            String prettyName = readStr(bb, prettyNameOff, prettyNameLen);
            String className = readStr(bb, classNameOff, classNameLen);
            bb.position(headerSize);

            // read ole header
            int oleVer = bb.getInt();
            /* int format = */ bb.getInt();

            if(oleVer != OLE_VERSION) {
                return new UnknownContentImpl(blob);
            }

            int typeNameLen = bb.getInt();
            String typeName = readStr(bb, bb.position(), typeNameLen);
            bb.getLong(); // unused
            int dataBlockLen = bb.getInt();
            int dataBlockPos = bb.position();


            if(SIMPLE_PACKAGE_TYPE.equalsIgnoreCase(typeName)) {
                return createSimplePackageContent(
                        blob, prettyName, className, typeName, bb, dataBlockLen);
            }

            // if COMPOUND_FACTORY is null, the poi library isn't available, so just
            // load compound data as "other"
            if((COMPOUND_FACTORY != null) &&
                    (bb.remaining() >= COMPOUND_STORAGE_SIGNATURE.length) &&
                    ByteUtil.matchesRange(bb, bb.position(), COMPOUND_STORAGE_SIGNATURE)) {
                return COMPOUND_FACTORY.createCompoundPackageContent(
                        blob, prettyName, className, typeName, bb, dataBlockLen);
            }

            // this is either some other "special" (as yet unhandled) format, or it is
            // simply an embedded file (or it is compound data and poi isn't available)
            return new OtherContentImpl(blob, prettyName, className,
                    typeName, dataBlockPos, dataBlockLen);
        }

        private static ContentImpl createSimplePackageContent(
                OleBlobImpl blob, String prettyName, String className, String typeName,
                ByteBuffer blobBb, int dataBlockLen) {

            int dataBlockPos = blobBb.position();
            ByteBuffer bb = PageChannel.narrowBuffer(blobBb, dataBlockPos,
                    dataBlockPos + dataBlockLen);

            int packageSig = bb.getShort();
            if(packageSig != PACKAGE_STREAM_SIGNATURE) {
                return new OtherContentImpl(blob, prettyName, className,
                        typeName, dataBlockPos, dataBlockLen);
            }

            String fileName = readZeroTermStr(bb);
            String filePath = readZeroTermStr(bb);
            int packageType = bb.getInt();

            if(packageType == PS_EMBEDDED_FILE) {

                int localFilePathLen = bb.getInt();
                String localFilePath = readStr(bb, bb.position(), localFilePathLen);
                int dataLen = bb.getInt();
                int dataPos = bb.position();
                bb.position(dataLen + dataPos);

                // remaining strings are in "reverse" order (local file path, file name,
                // file path).  these string usee a real utf charset, and therefore can
                // "fix" problems with ascii based names (so we prefer these strings to
                // the original strings we found)
                int strNum = 0;
                while(true) {

                    int rem = bb.remaining();
                    if(rem < 4) {
                        break;
                    }

                    int strLen = bb.getInt();
                    String remStr = readStr(bb, bb.position(), strLen * 2, OLE_UTF_CHARSET);

                    switch(strNum) {
                        case 0:
                            localFilePath = remStr;
                            break;
                        case 1:
                            fileName = remStr;
                            break;
                        case 2:
                            filePath = remStr;
                            break;
                        default:
                            // ignore
                    }

                    ++strNum;
                }

                return new SimplePackageContentImpl(
                        blob, prettyName, className, typeName, dataPos, dataLen,
                        fileName, filePath, localFilePath);
            }

            if(packageType == PS_LINKED_FILE) {

                bb.getShort(); //unknown
                String linkStr = readZeroTermStr(bb);

                return new LinkContentImpl(blob, prettyName, className, typeName,
                        fileName, linkStr, filePath);
            }

            return new OtherContentImpl(blob, prettyName, className,
                    typeName, dataBlockPos, dataBlockLen);
        }

        private static String readStr(ByteBuffer bb, int off, int len) {
            return readStr(bb, off, len, OLE_CHARSET);
        }

        private static String readZeroTermStr(ByteBuffer bb) {
            int off = bb.position();
            while(bb.hasRemaining()) {
                byte b = bb.get();
                if(b == 0) {
                    break;
                }
            }
            int len = bb.position() - off;
            return readStr(bb, off, len);
        }

        private static String readStr(ByteBuffer bb, int off, int len,
                                      Charset charset) {
            String str = new String(bb.array(), off, len, charset);
            bb.position(off + len);
            if(str.charAt(str.length() - 1) == '\0') {
                str = str.substring(0, str.length() - 1);
            }
            return str;
        }

        private static byte[] getZeroTermStrBytes(String str) {
            // since we are converting to ascii, try to make "nicer" versions of crazy
            // chars (e.g. convert "u with an umlaut" to just "u").  this may not
            // ultimately help anything but it is what ms access does.

            // decompose complex chars into combos of char and accent
            str = Normalizer.normalize(str, Normalizer.Form.NFD);
            // strip the accents
            str = UNICODE_ACCENT_PATTERN.matcher(str).replaceAll("");
            // (re)normalize what is left
            str = Normalizer.normalize(str, Normalizer.Form.NFC);

            return (str + '\0').getBytes(OLE_CHARSET);
        }


        static final class OleBlobImpl implements OleBlob
        {
            private byte[] _bytes;
            private ContentImpl _content;

            private OleBlobImpl(byte[] bytes) {
                _bytes = bytes;
            }

            public void writeTo(OutputStream out) throws IOException {
                out.write(_bytes);
            }

            public Content getContent() throws IOException {
                if(_content == null) {
                    _content = parseContent(this);
                }
                return _content;
            }

            public InputStream getBinaryStream() throws SQLException {
                return new ByteArrayInputStream(_bytes);
            }

            public InputStream getBinaryStream(long pos, long len)
                    throws SQLException
            {
                return new ByteArrayInputStream(_bytes, fromJdbcOffset(pos), (int)len);
            }

            public long length() throws SQLException {
                return _bytes.length;
            }

            public byte[] getBytes() throws IOException {
                if(_bytes == null) {
                    throw new IOException("blob is closed");
                }
                return _bytes;
            }

            public byte[] getBytes(long pos, int len) throws SQLException {
                return ByteUtil.copyOf(_bytes, fromJdbcOffset(pos), len);
            }

            public long position(byte[] pattern, long start) throws SQLException {
                int pos = ByteUtil.findRange(PageChannel.wrap(_bytes),
                        fromJdbcOffset(start), pattern);
                return((pos >= 0) ? toJdbcOffset(pos) : pos);
            }

            public long position(Blob pattern, long start) throws SQLException {
                return position(pattern.getBytes(1L, (int)pattern.length()), start);
            }

            public OutputStream setBinaryStream(long position) throws SQLException {
                throw new SQLFeatureNotSupportedException();
            }

            public void truncate(long len) throws SQLException {
                throw new SQLFeatureNotSupportedException();
            }

            public int setBytes(long pos, byte[] bytes) throws SQLException {
                throw new SQLFeatureNotSupportedException();
            }

            public int setBytes(long pos, byte[] bytes, int offset, int lesn)
                    throws SQLException {
                throw new SQLFeatureNotSupportedException();
            }

            public void free() {
                close();
            }

            public void close() {
                _bytes = null;
                ByteUtil.closeQuietly(_content);
                _content = null;
            }

            private static int toJdbcOffset(int off) {
                return off + 1;
            }

            private static int fromJdbcOffset(long off) {
                return (int)off - 1;
            }

        }

        static abstract class ContentImpl implements Content, Closeable
        {
            protected final OleBlobImpl _blob;

            protected ContentImpl(OleBlobImpl blob) {
                _blob = blob;
            }

            public OleBlobImpl getBlob() {
                return _blob;
            }

            protected byte[] getBytes() throws IOException {
                return getBlob().getBytes();
            }

            public void close() {
                // base does nothing
            }

        }

        static abstract class EmbeddedContentImpl extends ContentImpl
                implements EmbeddedContent
        {
            private final int _position;
            private final int _length;

            protected EmbeddedContentImpl(OleBlobImpl blob, int position, int length)
            {
                super(blob);
                _position = position;
                _length = length;
            }

            public long length() {
                return _length;
            }

            public InputStream getStream() throws IOException {
                return new ByteArrayInputStream(getBytes(), _position, _length);
            }

            public void writeTo(OutputStream out) throws IOException {
                out.write(getBytes(), _position, _length);
            }
        }

        static abstract class EmbeddedPackageContentImpl
                extends EmbeddedContentImpl
                implements PackageContent
        {
            private final String _prettyName;
            private final String _className;
            private final String _typeName;

            protected EmbeddedPackageContentImpl(
                    OleBlobImpl blob, String prettyName, String className,
                    String typeName, int position, int length)
            {
                super(blob, position, length);
                _prettyName = prettyName;
                _className = className;
                _typeName = typeName;
            }

            public String getPrettyName() {
                return _prettyName;
            }

            public String getClassName() {
                return _className;
            }

            public String getTypeName() {
                return _typeName;
            }

        }

        private static final class LinkContentImpl
                extends EmbeddedPackageContentImpl
                implements LinkContent
        {
            private final String _fileName;
            private final String _linkPath;
            private final String _filePath;

            private LinkContentImpl(OleBlobImpl blob, String prettyName,
                                    String className, String typeName,
                                    String fileName, String linkPath,
                                    String filePath)
            {
                super(blob, prettyName, className, typeName, -1, -1);
                _fileName = fileName;
                _linkPath = linkPath;
                _filePath = filePath;
            }

            public ContentType getType() {
                return ContentType.LINK;
            }

            public String getFileName() {
                return _fileName;
            }

            public String getLinkPath() {
                return _linkPath;
            }

            public String getFilePath() {
                return _filePath;
            }

            public InputStream getLinkStream() throws IOException {
                return new FileInputStream(getLinkPath());
            }
        }

        private static final class SimplePackageContentImpl
                extends EmbeddedPackageContentImpl
                implements SimplePackageContent
        {
            private final String _fileName;
            private final String _filePath;
            private final String _localFilePath;

            private SimplePackageContentImpl(OleBlobImpl blob, String prettyName,
                                             String className, String typeName,
                                             int position, int length,
                                             String fileName, String filePath,
                                             String localFilePath)
            {
                super(blob, prettyName, className, typeName, position, length);
                _fileName = fileName;
                _filePath = filePath;
                _localFilePath = localFilePath;
            }

            public ContentType getType() {
                return ContentType.SIMPLE_PACKAGE;
            }

            public String getFileName() {
                return _fileName;
            }

            public String getFilePath() {
                return _filePath;
            }

            public String getLocalFilePath() {
                return _localFilePath;
            }

        }

        private static final class OtherContentImpl
                extends EmbeddedPackageContentImpl
                implements OtherContent
        {
            private OtherContentImpl(
                    OleBlobImpl blob, String prettyName, String className,
                    String typeName, int position, int length)
            {
                super(blob, prettyName, className, typeName, position, length);
            }

            public ContentType getType() {
                return ContentType.OTHER;
            }

        }

        private static final class UnknownContentImpl extends ContentImpl
        {
            private UnknownContentImpl(OleBlobImpl blob) {
                super(blob);
            }

            public ContentType getType() {
                return ContentType.UNKNOWN;
            }

        }

    }
