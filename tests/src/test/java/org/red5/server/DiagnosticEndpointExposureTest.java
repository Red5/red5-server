package org.red5.server;

import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.Test;

/**
 * Packaging check: no anonymously reachable page in the shipped root webapp may trigger diagnostic actions such as a
 * forced garbage collection or a request-thread sleep (issue #464).
 */
public class DiagnosticEndpointExposureTest {

    @Test
    public void testRootWebappHasNoDiagnosticActions() throws IOException {
        File webapps = new File(System.getProperty("basedir", "."), "../server/src/main/server/webapps/root");
        assumeTrue("root webapp sources not present", webapps.isDirectory());
        List<String> offenders = new ArrayList<>();
        try (Stream<java.nio.file.Path> paths = Files.walk(webapps.toPath())) {
            paths.filter(p -> p.toString().endsWith(".jsp") || p.toString().endsWith(".jspx")).forEach(p -> {
                try {
                    String src = new String(Files.readAllBytes(p), StandardCharsets.UTF_8);
                    if (src.contains("System.gc(") || src.contains("Thread.sleep(")) {
                        offenders.add(webapps.toPath().relativize(p).toString());
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        assertTrue("diagnostic actions exposed in root webapp: " + offenders, offenders.isEmpty());
    }
}
