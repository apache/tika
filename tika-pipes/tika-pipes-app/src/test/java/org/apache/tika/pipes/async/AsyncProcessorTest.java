package org.apache.tika.pipes.async;

import org.apache.commons.io.FileUtils;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.pipes.emitter.EmitKey;
import org.apache.tika.pipes.fetcher.FetchKey;
import org.apache.tika.pipes.fetchiterator.FetchEmitTuple;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class AsyncProcessorTest {

    private Path dbDir;
    private Path dbFile;
    private Connection connection;
    private Path tikaConfigPath;

    @Before
    public void setUp() throws SQLException, IOException {
        dbDir = Files.createTempDirectory("async-db");
        dbFile = dbDir.resolve("emitted-db");
        String jdbc = "jdbc:h2:file:"+dbFile.toAbsolutePath().toString()+";AUTO_SERVER=TRUE";
        String sql = "create table emitted (id int auto_increment primary key, json varchar(20000))";

        connection = DriverManager.getConnection(jdbc);
        connection.createStatement().execute(sql);
        tikaConfigPath = dbDir.resolve("tika-config.xml");
        String xml = "" +
                "<?xml version=\"1.0\" encoding=\"UTF-8\" ?>" +
                "<properties>" +
                "  <emitters>"+
                "  <emitter class=\"org.apache.tika.pipes.async.MockEmitter\">\n" +
                "    <params>\n" +
                "      <param name=\"name\" type=\"string\">mock</param>\n"+
                "      <param name=\"jdbc\" type=\"string\">"+jdbc+"</param>\n"+
                "    </params>" +
                "  </emitter>" +
                "  </emitters>"+
                "  <fetchers>" +
                "    <fetcher class=\"org.apache.tika.pipes.async.MockFetcher\">" +
                "      <param name=\"name\" type=\"string\">mock</param>\n"+
                "    </fetcher>" +
                "  </fetchers>"+
                "</properties>";
        Files.write(tikaConfigPath, xml.getBytes(StandardCharsets.UTF_8));
    }

    @After
    public void tearDown() throws SQLException, IOException {
        connection.createStatement().execute("drop table emitted");
        connection.close();
        FileUtils.deleteDirectory(dbDir.toFile());
    }

    @Test
    public void testBasic() throws Exception {


        AsyncProcessor processor = AsyncProcessor.build(tikaConfigPath);
        for (int i = 0 ; i < 100; i++) {
            FetchEmitTuple t = new FetchEmitTuple(
                    new FetchKey("mock", "key-"+i),
                    new EmitKey("mock", "emit-"+i),
                    new Metadata()
            );
            processor.offer(t, 1000);
        }
        processor.close();
        String sql = "select * from emitted";
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                System.out.println(rs.getInt(1) + " : "+rs.getString(2));
            }
        }
    }
}
