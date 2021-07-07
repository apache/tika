package org.apache.tika.pipes.pipesiterator.filelist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import org.apache.tika.config.InitializableProblemHandler;
import org.apache.tika.pipes.FetchEmitTuple;

public class FileListPipesIteratorTest {

    @Test
    public void testBasic() throws Exception {
        Path p = Paths.get(this.getClass().getResource("/test-documents/file-list.txt").toURI());
        FileListPipesIterator it = new FileListPipesIterator();
        it.setFetcherName("f");
        it.setEmitterName("e");
        it.setFileList(p.toAbsolutePath().toString());
        it.setHasHeader(false);
        it.checkInitialization(InitializableProblemHandler.DEFAULT);
        List<String> lines = new ArrayList<>();

        for (FetchEmitTuple t : it) {
            assertEquals(t.getFetchKey().getFetchKey(), t.getEmitKey().getEmitKey());
            assertEquals(t.getId(), t.getEmitKey().getEmitKey());
            assertEquals("f", t.getFetchKey().getFetcherName());
            assertEquals("e", t.getEmitKey().getEmitterName());
            lines.add(t.getId());
        }
        assertEquals("the", lines.get(0));
        assertEquals(8, lines.size());
        assertFalse(lines.contains("quick"));
    }

    @Test
    public void testHasHeader() throws Exception {
        Path p = Paths.get(this.getClass().getResource("/test-documents/file-list.txt").toURI());
        FileListPipesIterator it = new FileListPipesIterator();
        it.setFetcherName("f");
        it.setEmitterName("e");
        it.setFileList(p.toAbsolutePath().toString());
        it.setHasHeader(true);
        it.checkInitialization(InitializableProblemHandler.DEFAULT);
        List<String> lines = new ArrayList<>();

        for (FetchEmitTuple t : it) {
            assertEquals(t.getFetchKey().getFetchKey(), t.getEmitKey().getEmitKey());
            assertEquals(t.getId(), t.getEmitKey().getEmitKey());
            assertEquals("f", t.getFetchKey().getFetcherName());
            assertEquals("e", t.getEmitKey().getEmitterName());
            lines.add(t.getId());
        }
        assertEquals("brown", lines.get(0));
        assertFalse(lines.contains("quick"));
        assertEquals(7, lines.size());
    }
}
