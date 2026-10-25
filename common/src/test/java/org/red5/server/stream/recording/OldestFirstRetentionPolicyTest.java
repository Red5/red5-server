package org.red5.server.stream.recording;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;

/**
 * Verifies the built-in retention policy deletes only expired recordings inside the record directory (issue #490).
 */
public class OldestFirstRetentionPolicyTest {

    private static final long HOUR = 3600_000L;

    private static final Predicate<Path> NONE_IN_USE = p -> false;

    // holds the record directory and, next to it, files the policy must never reach
    private Path base;

    private Path dir;

    private Path outside;

    private OldestFirstRetentionPolicy policy;

    @Before
    public void setUp() throws Exception {
        base = Files.createTempDirectory("retention");
        dir = Files.createDirectory(base.resolve("streams"));
        outside = Files.createDirectory(base.resolve("outside"));
        policy = new OldestFirstRetentionPolicy();
    }

    @After
    public void tearDown() throws Exception {
        try (Stream<Path> files = Files.walk(base)) {
            files.sorted((a, b) -> b.compareTo(a)).forEach(p -> p.toFile().delete());
        }
    }

    @Test
    public void testDisabledByDefault() throws Exception {
        Path old = file(dir, "old.flv", 1000, 100 * HOUR);
        assertEquals(0L, policy.getMaxAgeMs());
        assertEquals(0L, policy.getMaxTotalBytes());
        assertEquals(List.of(".flv"), policy.getExtensions());
        assertTrue(policy.apply(dir, NONE_IN_USE).isEmpty());
        assertTrue(Files.exists(old));
    }

    @Test
    public void testDeletesRecordingsOlderThanMaxAge() throws Exception {
        Path oldest = file(dir, "a.flv", 1000, 5 * HOUR);
        Path old = file(dir, "b.FLV", 1000, 3 * HOUR);
        Path recent = file(dir, "c.flv", 1000, HOUR / 2);
        // a sub-scope directory
        Path nested = file(Files.createDirectory(dir.resolve("room1")), "d.flv", 1000, 4 * HOUR);
        policy.setMaxAgeMs(2 * HOUR);
        List<Path> deleted = policy.apply(dir, NONE_IN_USE);
        assertEquals(3, deleted.size());
        assertFalse(Files.exists(oldest));
        assertFalse("extensions match regardless of case", Files.exists(old));
        assertFalse("sub-scope directories are covered", Files.exists(nested));
        assertTrue(Files.exists(recent));
    }

    @Test
    public void testDeletesOldestUntilTotalIsBelowMax() throws Exception {
        Path f1 = file(dir, "1.flv", 1000, 5 * HOUR);
        Path f2 = file(dir, "2.flv", 1000, 4 * HOUR);
        Path f3 = file(dir, "3.flv", 1000, 3 * HOUR);
        Path f4 = file(dir, "4.flv", 1000, 2 * HOUR);
        Path f5 = file(dir, "5.flv", 1000, 1 * HOUR);
        policy.setMaxTotalBytes(2500);
        List<Path> deleted = policy.apply(dir, NONE_IN_USE);
        assertEquals(3, deleted.size());
        assertFalse(Files.exists(f1));
        assertFalse(Files.exists(f2));
        assertFalse(Files.exists(f3));
        assertTrue(Files.exists(f4));
        assertTrue(Files.exists(f5));
    }

    @Test
    public void testIgnoresOtherExtensions() throws Exception {
        Path vod = file(dir, "movie.mp4", 50_000, 100 * HOUR);
        Path ser = file(dir, "crashed.flv.ser", 50_000, 100 * HOUR);
        Path txt = file(dir, "notes.txt", 50_000, 100 * HOUR);
        Path bare = file(dir, "flv", 50_000, 100 * HOUR);
        Path rec = file(dir, "rec.flv", 1000, 100 * HOUR);
        Path recent = file(dir, "recent.flv", 1000, HOUR);
        policy.setMaxAgeMs(10 * HOUR);
        policy.setMaxTotalBytes(1500);
        policy.apply(dir, NONE_IN_USE);
        assertTrue(Files.exists(vod));
        assertTrue(Files.exists(ser));
        assertTrue(Files.exists(txt));
        assertTrue(Files.exists(bare));
        assertFalse(Files.exists(rec));
        assertTrue("other extensions do not count towards the total", Files.exists(recent));
        // configured extensions replace the default
        policy.setExtensions(List.of("MP4"));
        policy.apply(dir, NONE_IN_USE);
        assertFalse(Files.exists(vod));
        assertTrue(Files.exists(recent));
    }

    @Test
    public void testNeverFollowsSymbolicLinks() throws Exception {
        Path target = file(outside, "target.flv", 1000, 100 * HOUR);
        Path outsideDir = Files.createDirectory(outside.resolve("dir"));
        Path outsideNested = file(outsideDir, "nested.flv", 1000, 100 * HOUR);
        Path fileLink = dir.resolve("link.flv");
        Path dirLink = dir.resolve("linked");
        try {
            Files.createSymbolicLink(fileLink, target);
            Files.createSymbolicLink(dirLink, outsideDir);
        } catch (UnsupportedOperationException | IOException e) {
            Assume.assumeNoException("symbolic links are not supported here", e);
        }
        policy.setMaxAgeMs(HOUR);
        policy.setMaxTotalBytes(1);
        assertTrue(policy.apply(dir, NONE_IN_USE).isEmpty());
        assertTrue("a link is not deleted", Files.exists(fileLink, LinkOption.NOFOLLOW_LINKS));
        assertTrue("the file a link points to is not deleted", Files.exists(target));
        assertTrue(Files.exists(dirLink, LinkOption.NOFOLLOW_LINKS));
        assertTrue("a linked directory is not walked", Files.exists(outsideNested));
    }

    @Test
    public void testNeverLeavesTheDirectory() throws Exception {
        Path sibling = file(outside, "sibling.flv", 1000, 100 * HOUR);
        Path parent = file(base, "parent.flv", 1000, 100 * HOUR);
        Path inside = file(dir, "inside.flv", 1000, 100 * HOUR);
        policy.setMaxAgeMs(HOUR);
        assertEquals(List.of(inside.toRealPath()), policy.apply(dir, NONE_IN_USE));
        assertTrue(Files.exists(sibling));
        assertTrue(Files.exists(parent));
        // a record directory that does not exist is a no-op
        assertTrue(policy.apply(dir.resolve("missing"), NONE_IN_USE).isEmpty());
    }

    @Test
    public void testKeepsFilesInUseAndRecentlyModified() throws Exception {
        Path active = file(dir, "active.flv", 1000, 100 * HOUR);
        Path idle = file(dir, "idle.flv", 1000, 100 * HOUR);
        // modified within the idle time, as by a writer the budget does not know about
        Path writing = file(dir, "writing.flv", 1000, 10_000L);
        policy.setMaxTotalBytes(1);
        Path activeReal = active.toRealPath();
        List<Path> deleted = policy.apply(dir, p -> p.equals(activeReal));
        assertEquals(1, deleted.size());
        assertTrue("a file in use is never deleted", Files.exists(active));
        assertFalse(Files.exists(idle));
        assertTrue("a recently modified file is never deleted", Files.exists(writing));
    }

    @Test
    public void testActiveRecordingFilesAreInUse() throws Exception {
        // the working files the writer keeps next to a recording in progress belong to it
        Path rec = dir.resolve("rec.flv");
        assertTrue(RecordingBudget.belongsTo(rec, rec));
        assertTrue(RecordingBudget.belongsTo(dir.resolve("rec.flv.ser"), rec));
        assertTrue(RecordingBudget.belongsTo(dir.resolve("rec.flv.info"), rec));
        assertTrue(RecordingBudget.belongsTo(dir.resolve("rec.old"), rec));
        assertFalse(RecordingBudget.belongsTo(dir.resolve("rec2.flv"), rec));
        assertFalse(RecordingBudget.belongsTo(dir.resolve("room1").resolve("rec.flv"), rec));
    }

    private static Path file(Path parent, String name, int size, long ageMs) throws IOException {
        Path file = parent.resolve(name);
        Files.write(file, new byte[size]);
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() - ageMs));
        return file;
    }

}
