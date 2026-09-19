package org.red5.server.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Before;
import org.junit.Test;

/**
 * WAR/zip extraction must not write outside the destination directory (issue #474).
 */
public class FileUtilZipSlipTest {

    private File root;

    private File dest;

    @Before
    public void setUp() throws Exception {
        root = Files.createTempDirectory("red5-zipslip").toFile();
        root.deleteOnExit();
        dest = new File(root, "dest");
    }

    private File zip(String... entries) throws Exception {
        File zip = new File(root, "test.zip");
        try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zip))) {
            for (String entry : entries) {
                out.putNextEntry(new ZipEntry(entry));
                if (!entry.endsWith("/")) {
                    out.write("payload".getBytes(StandardCharsets.UTF_8));
                }
                out.closeEntry();
            }
        }
        return zip;
    }

    @Test
    public void testParentTraversalEntryIsNotWritten() throws Exception {
        FileUtil.unzip(zip("../evil.txt", "ok/fine.txt").getAbsolutePath(), dest.getAbsolutePath());
        assertFalse("entry escaped the destination", new File(root, "evil.txt").exists());
    }

    @Test
    public void testNestedTraversalEntryIsNotWritten() throws Exception {
        FileUtil.unzip(zip("a/../../evil2.txt").getAbsolutePath(), dest.getAbsolutePath());
        assertFalse("entry escaped the destination", new File(root, "evil2.txt").exists());
    }

    @Test
    public void testBackslashTraversalEntryIsNotWritten() throws Exception {
        FileUtil.unzip(zip("..\\evil3.txt").getAbsolutePath(), dest.getAbsolutePath());
        assertFalse(new File(root, "evil3.txt").exists());
        assertFalse("backslash form must be rejected, not written literally", new File(dest, "..\\evil3.txt").exists());
    }

    @Test
    public void testAbsoluteEntryIsNotWritten() throws Exception {
        File abs = new File(root, "abs-evil.txt");
        FileUtil.unzip(zip(abs.getAbsolutePath()).getAbsolutePath(), dest.getAbsolutePath());
        assertFalse(abs.exists());
    }

    @Test
    public void testNormalEntriesAreExtracted() throws Exception {
        FileUtil.unzip(zip("META-INF/", "META-INF/MANIFEST.MF", "index.html").getAbsolutePath(), dest.getAbsolutePath());
        assertTrue(new File(dest, "META-INF/MANIFEST.MF").exists());
        assertTrue(new File(dest, "index.html").exists());
    }
}
