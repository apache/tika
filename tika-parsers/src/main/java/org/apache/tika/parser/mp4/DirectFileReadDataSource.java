package org.apache.tika.parser.mp4;

import com.googlecode.mp4parser.DataSource;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.WritableByteChannel;

import static com.googlecode.mp4parser.util.CastUtils.l2i;

/**
 * A {@link DataSource} implementation that relies on direct reads from a {@link RandomAccessFile}.
 * It should be slower than {@link com.googlecode.mp4parser.FileDataSourceImpl} but does not incur the implicit file locks of
 * memory mapped I/O on some JVMs. This implementation allows for a more controlled deletion of files
 * and might be preferred when working with temporary files.
 * @see <a href="http://bugs.java.com/view_bug.do?bug_id=4724038">JDK-4724038 : (fs) Add unmap method to MappedByteBuffer</a>
 * @see <a href="http://bugs.java.com/view_bug.do?bug_id=6359560">JDK-6359560 : (fs) File.deleteOnExit() doesn't work when MappedByteBuffer exists (win)</a>
 */
public class DirectFileReadDataSource implements DataSource {

    private static final int TRANSFER_SIZE = 8192;

    private RandomAccessFile raf;

    public DirectFileReadDataSource(File f) throws IOException {
        this.raf = new RandomAccessFile(f, "r");
    }

    public int read(ByteBuffer byteBuffer) throws IOException {
        int len = byteBuffer.remaining();
        int totalRead = 0;
        int bytesRead = 0;
        byte[] buf = new byte[TRANSFER_SIZE];
        while (totalRead < len) {
            int bytesToRead = Math.min((len - totalRead), TRANSFER_SIZE);
            bytesRead = raf.read(buf, 0, bytesToRead);
            if (bytesRead < 0) {
                break;
            } else {
                totalRead += bytesRead;
            }
            byteBuffer.put(buf, 0, bytesRead);
        }
        return ((bytesRead < 0) && (totalRead == 0)) ? -1 : totalRead;
    }

    public int readAllInOnce(ByteBuffer byteBuffer) throws IOException {
        byte[] buf = new byte[byteBuffer.remaining()];
        int read = raf.read(buf);
        byteBuffer.put(buf, 0, read);
        return read;
    }

    public long size() throws IOException {
        return raf.length();
    }

    public long position() throws IOException {
        return raf.getFilePointer();
    }

    public void position(long nuPos) throws IOException {
        raf.seek(nuPos);
    }

    public long transferTo(long position, long count, WritableByteChannel target) throws IOException {
        return target.write(map(position, count));
    }

    public ByteBuffer map(long startPosition, long size) throws IOException {
        raf.seek(startPosition);
        byte[] payload = new byte[l2i(size)];
        raf.readFully(payload);
        return ByteBuffer.wrap(payload);
    }

    public void close() throws IOException {
        raf.close();
    }


}
