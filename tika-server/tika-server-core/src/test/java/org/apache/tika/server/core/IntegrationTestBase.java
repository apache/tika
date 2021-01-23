package org.apache.tika.server.core;

import org.apache.cxf.common.logging.LogUtils;
import org.apache.tika.TikaTest;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.Permission;

public class IntegrationTestBase extends TikaTest {

    static final String TEST_HELLO_WORLD = "test-documents/mock/hello_world.xml";
    static final String TEST_OOM = "test-documents/mock/fake_oom.xml";
    static final String TEST_SYSTEM_EXIT = "test-documents/mock/system_exit.xml";
    static final String TEST_HEAVY_HANG = "test-documents/mock/heavy_hang_30000.xml";
    static final String TEST_HEAVY_HANG_SHORT = "test-documents/mock/heavy_hang_100.xml";
    static final String TEST_STDOUT_STDERR = "test-documents/mock/testStdOutErr.xml";
    static final String TEST_STATIC_STDOUT_STDERR = "test-documents/mock/testStaticStdOutErr.xml";
    static final String META_PATH = "/rmeta";
    static final String STATUS_PATH = "/status";

    static final long MAX_WAIT_MS = 60000;

    //running into conflicts on 9998 with the CXFTestBase tests
    //TODO: figure out why?!
    static final String INTEGRATION_TEST_PORT = "9999";

    protected static final String endPoint =
            "http://localhost:" + INTEGRATION_TEST_PORT;

    private SecurityManager existingSecurityManager = null;
    static Path LOG_FILE;


    @BeforeClass
    public static void staticSetup() throws Exception {
        LogUtils.setLoggerClass(NullWebClientLogger.class);
        LOG_FILE = Files.createTempFile("tika-server-integration", ".xml");
        Files.copy(TikaServerIntegrationTest.class.getResourceAsStream("/logging/log4j_forked.xml"),
                LOG_FILE, StandardCopyOption.REPLACE_EXISTING);
    }

    @Before
    public void setUp() throws Exception {
        existingSecurityManager = System.getSecurityManager();
        System.setSecurityManager(new SecurityManager() {
            @Override
            public void checkExit(int status) {
                super.checkExit(status);
                throw new MyExitException(status);
            }
            @Override
            public void checkPermission(Permission perm) {
                // all ok
            }
            @Override
            public void checkPermission(Permission perm, Object context) {
                // all ok
            }
        });
    }

    @AfterClass
    public static void staticTearDown() throws Exception {
        Files.delete(LOG_FILE);
    }

    @After
    public void tearDown() throws Exception {
        System.setSecurityManager(existingSecurityManager);
    }

    static class MyExitException extends RuntimeException {
        private final int status;
        MyExitException(int status) {
            this.status = status;
        }

        public int getStatus() {
            return status;
        }
    }
}
