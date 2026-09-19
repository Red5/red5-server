package org.red5.server.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Set;

import org.junit.Test;

/**
 * The shutdown token must be created owner-only and never printed (issue #473).
 */
public class ShutdownServerTokenTest {

    @Test
    public void testTokenIsNotPrintedToStdout() throws Exception {
        File dir = Files.createTempDirectory("red5-shutdown").toFile();
        File tokenFile = new File(dir, "shutdown.token");
        ShutdownServer server = new ShutdownServer();
        server.setShutdownTokenFileName(tokenFile.getAbsolutePath());
        PrintStream original = System.out;
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(captured, true, "UTF-8"));
            server.writeTokenFile();
        } finally {
            System.setOut(original);
        }
        String token = new String(Files.readAllBytes(tokenFile.toPath()), StandardCharsets.UTF_8).trim();
        assertEquals(36, token.length());
        assertFalse("token printed to stdout", captured.toString("UTF-8").contains(token));
    }

    @Test
    public void testTokenFileIsOwnerOnly() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        File dir = Files.createTempDirectory("red5-shutdown").toFile();
        Path tokenFile = new File(dir, "shutdown.token").toPath();
        ShutdownServer server = new ShutdownServer();
        server.setShutdownTokenFileName(tokenFile.toString());
        server.writeTokenFile();
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(tokenFile);
        assertTrue("token file must be owner-only but was " + perms, perms.contains(PosixFilePermission.OWNER_READ) && perms.stream().allMatch(p -> p.name().startsWith("OWNER_")));
    }

    @Test
    public void testExistingWorldReadableTokenFileIsReplaced() throws Exception {
        assumeTrue(FileSystems.getDefault().supportedFileAttributeViews().contains("posix"));
        File dir = Files.createTempDirectory("red5-shutdown").toFile();
        Path tokenFile = new File(dir, "shutdown.token").toPath();
        Files.write(tokenFile, "stale".getBytes(StandardCharsets.UTF_8));
        Files.setPosixFilePermissions(tokenFile, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.GROUP_READ, PosixFilePermission.OTHERS_READ));
        ShutdownServer server = new ShutdownServer();
        server.setShutdownTokenFileName(tokenFile.toString());
        server.writeTokenFile();
        Set<PosixFilePermission> perms = Files.getPosixFilePermissions(tokenFile);
        assertTrue("replaced token file must be owner-only but was " + perms, perms.stream().allMatch(p -> p.name().startsWith("OWNER_")));
    }
}
