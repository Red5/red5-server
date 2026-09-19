package org.red5.io;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.junit.Test;
import org.red5.io.flv.IKeyFrameDataAnalyzer.KeyFrameMeta;

/**
 * Keyframe sidecar parsing must not honour DOCTYPE / external entities (issue #458).
 */
public class FileKeyFrameMetaCacheXxeTest {

    private File mediaFile() throws Exception {
        File f = File.createTempFile("red5", "xxe.flv");
        f.deleteOnExit();
        return f;
    }

    private String sidecar(File f, String doctype, String duration) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>" + doctype + "<FrameMetadata audioOnly=\"false\" duration=\"" + duration + "\" modified=\"" + f.lastModified() + "\"><KeyFrame position=\"0\" timestamp=\"0\"/></FrameMetadata>";
    }

    @Test
    public void testSidecarWithDoctypeIsRejected() throws Exception {
        File f = mediaFile();
        // the entity resolves to a perfectly valid duration, so only a DOCTYPE ban can reject this document
        String xml = sidecar(f, "<!DOCTYPE FrameMetadata [<!ENTITY d \"1000\">]>", "&d;");
        Files.write(new File(f.getCanonicalPath() + ".meta").toPath(), xml.getBytes(StandardCharsets.UTF_8));
        KeyFrameMeta meta = new FileKeyFrameMetaCache().loadKeyFrameMeta(f);
        assertNull("sidecar with DOCTYPE must be rejected", meta);
    }

    @Test
    public void testPlainSidecarStillLoads() throws Exception {
        File f = mediaFile();
        String xml = sidecar(f, "", "1000");
        Files.write(new File(f.getCanonicalPath() + ".meta").toPath(), xml.getBytes(StandardCharsets.UTF_8));
        KeyFrameMeta meta = new FileKeyFrameMetaCache().loadKeyFrameMeta(f);
        assertNotNull(meta);
    }
}
