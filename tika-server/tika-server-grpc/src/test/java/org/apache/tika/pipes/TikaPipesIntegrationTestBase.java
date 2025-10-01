package org.apache.tika.pipes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ContextConfiguration(initializers = TikaPipesIntegrationTestBase.DynamicPortInitializer.class)
public abstract class TikaPipesIntegrationTestBase {
    @Autowired
    public ObjectMapper objectMapper;
    @Value("${grpc.server.port}")
    public Integer port;

    @DynamicPropertySource
    static void dynamicProperties(DynamicPropertyRegistry registry) {
        System.setProperty("pf4j.mode", "development");
    }

    static class DynamicPortInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            int dynamicPort = findAvailablePort();
            TestPropertyValues.of("grpc.server.port=" + dynamicPort)
                    .applyTo(applicationContext.getEnvironment());
            TestPropertyValues.of(
                            "ignite.workDir=" + new File("target/" + UUID.randomUUID()).getAbsolutePath())
                    .applyTo(applicationContext.getEnvironment());
            File tikaPipesParentFolder = getTikaPipesParentFolder();
            List<String> pluginDirs = new ArrayList<>();
            pluginDirs.add(
                    new File(tikaPipesParentFolder, "tika-pipes-fetchers").getAbsolutePath());
            pluginDirs.add(
                    new File(tikaPipesParentFolder, "tika-pipes-emitters").getAbsolutePath());
            pluginDirs.add(
                    new File(tikaPipesParentFolder, "tika-pipes-pipe-iterators").getAbsolutePath());
            TestPropertyValues.of("plugins.pluginDirs=" + StringUtils.join(pluginDirs, ","))
                    .applyTo(applicationContext.getEnvironment());
        }

        private File getTikaPipesParentFolder() {
            File currentDir = new File(System.getProperty("user.dir"));
            while (currentDir != null && currentDir.isDirectory() && currentDir.exists() &&
                    !currentDir.getName().equals("tika-pipes")) {
                currentDir = currentDir.getParentFile();
            }
            return currentDir;
        }

        private int findAvailablePort() {
            try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
                return socket.getLocalPort(); // Dynamically find an available port
            } catch (Exception e) {
                throw new RuntimeException("Failed to find an available port", e);
            }
        }
    }
}
