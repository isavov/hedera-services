/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.swirlds.common.io.utility;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyDoesNotThrow;
import static com.swirlds.common.threading.manager.AdHocThreadManager.getStaticThreadManager;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.base.test.fixtures.time.FakeTime;
import com.swirlds.base.time.Time;
import com.swirlds.common.config.StateConfig_;
import com.swirlds.common.io.config.RecycleBinConfig;
import com.swirlds.common.io.config.RecycleBinConfig_;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.common.platform.NodeId;
import com.swirlds.config.api.Configuration;
import com.swirlds.test.framework.config.TestConfigBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("RecycleBin Tests")
class RecycleBinTests {

    /**
     * Temporary directory provided by JUnit
     */
    @TempDir
    Path testDirectory;

    private Configuration configuration;

    @BeforeEach
    void beforeEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
        configuration = new TestConfigBuilder()
                .withValue(StateConfig_.SAVED_STATE_DIRECTORY, testDirectory.toString())
                .getOrCreateConfig();
    }

    @AfterEach
    void afterEach() throws IOException {
        FileUtils.deleteDirectory(testDirectory);
    }

    /**
     * Create a file and write a string to it.
     */
    private void writeFile(@NonNull final Path path, @NonNull final String contents) throws IOException {
        final Path parent = path.getParent();
        Files.createDirectories(parent);

        final BufferedWriter writer = Files.newBufferedWriter(path);
        writer.write(contents);
        writer.close();
    }

    /**
     * Validate that a file exists and contains the expected contents.
     */
    private void validateFile(@NonNull final Path path, @NonNull final String expectedContents) throws IOException {
        assertTrue(Files.exists(path));
        final String contents = Files.readString(path);
        assertTrue(contents.equals(expectedContents));
    }

    @Test
    @DisplayName("Recycle File Test")
    void recycleFileTest() throws IOException {
        final RecycleBin recycleBin = new RecycleBinImpl(
                configuration, new NoOpMetrics(), getStaticThreadManager(), Time.getCurrent(), new NodeId(0));

        final Path path1 = testDirectory.resolve("file1.txt");
        writeFile(path1, "file1");

        final Path path2 = testDirectory.resolve("file2.txt");
        writeFile(path2, "file2");

        final Path path3 = testDirectory.resolve("file3.txt");
        writeFile(path3, "file3");

        recycleBin.recycle(path1);
        recycleBin.recycle(path2);
        recycleBin.recycle(path3);

        assertFalse(Files.exists(path1));
        final Path recycledPath1 =
                testDirectory.resolve("swirlds-recycle-bin").resolve("0").resolve("file1.txt");
        validateFile(recycledPath1, "file1");

        assertFalse(Files.exists(path2));
        final Path recycledPath2 =
                testDirectory.resolve("swirlds-recycle-bin").resolve("0").resolve("file2.txt");
        validateFile(recycledPath2, "file2");

        assertFalse(Files.exists(path3));
        final Path recycledPath3 =
                testDirectory.resolve("swirlds-recycle-bin").resolve("0").resolve("file3.txt");
        validateFile(recycledPath3, "file3");
    }

    @Test
    @DisplayName("Recycle Directory Test")
    void recycleDirectoryTest() throws IOException {
        final RecycleBin recycleBin = new RecycleBinImpl(
                configuration, new NoOpMetrics(), getStaticThreadManager(), Time.getCurrent(), new NodeId(0));

        final Path directory = testDirectory.resolve("foo/bar/baz");
        Files.createDirectories(directory);

        final Path path1 = directory.resolve("file1.txt");
        writeFile(path1, "file1");

        final Path path2 = directory.resolve("file2.txt");
        writeFile(path2, "file2");

        final Path path3 = directory.resolve("file3.txt");
        writeFile(path3, "file3");

        recycleBin.recycle(testDirectory.resolve("foo"));

        assertFalse(Files.exists(path1));
        final Path recycledPath1 = testDirectory
                .resolve("swirlds-recycle-bin")
                .resolve("0")
                .resolve("foo/bar/baz")
                .resolve("file1.txt");
        validateFile(recycledPath1, "file1");

        assertFalse(Files.exists(path2));
        final Path recycledPath2 = testDirectory
                .resolve("swirlds-recycle-bin")
                .resolve("0")
                .resolve("foo/bar/baz")
                .resolve("file2.txt");
        validateFile(recycledPath2, "file2");

        assertFalse(Files.exists(path3));
        final Path recycledPath3 = testDirectory
                .resolve("swirlds-recycle-bin")
                .resolve("0")
                .resolve("foo/bar/baz")
                .resolve("file3.txt");
        validateFile(recycledPath3, "file3");
    }

    @Test
    @DisplayName("Recycle Non-Existent File Test")
    void recycleNonExistentFileTest() throws IOException {
        final RecycleBin recycleBin = new RecycleBinImpl(
                configuration, new NoOpMetrics(), getStaticThreadManager(), Time.getCurrent(), new NodeId(0));

        final Path path = testDirectory.resolve("file.txt");
        recycleBin.recycle(path);

        final Path recycledPath =
                testDirectory.resolve("swirlds-recycle-bin").resolve("0").resolve("file.txt");
        assertFalse(Files.exists(recycledPath));
    }

    @Test
    @DisplayName("Recycle Duplicate File Test")
    void recycleDuplicateFileTest() throws IOException {
        final RecycleBin recycleBin = new RecycleBinImpl(
                configuration, new NoOpMetrics(), getStaticThreadManager(), Time.getCurrent(), new NodeId(0));

        final Path path = testDirectory.resolve("file.txt");
        final Path recycledPath =
                testDirectory.resolve("swirlds-recycle-bin").resolve("0").resolve("file.txt");

        writeFile(path, "foo");
        recycleBin.recycle(path);
        assertFalse(Files.exists(path));
        validateFile(recycledPath, "foo");

        writeFile(path, "bar");
        recycleBin.recycle(path);
        assertFalse(Files.exists(path));
        validateFile(recycledPath, "bar");

        writeFile(path, "baz");
        recycleBin.recycle(path);
        assertFalse(Files.exists(path));
        validateFile(recycledPath, "baz");
    }

    @Test
    @DisplayName("Files Deleted After Time Passes")
    void filesDeletedAfterTimePasses() throws IOException, InterruptedException {
        final FakeTime time = new FakeTime(Instant.now(), Duration.ZERO);

        final Configuration customConfiguration = new TestConfigBuilder()
                .withValue(StateConfig_.SAVED_STATE_DIRECTORY, testDirectory.toString())
                .withValue(RecycleBinConfig_.COLLECTION_PERIOD, "1ns")
                .getOrCreateConfig();

        final Duration maximumFileAge =
                customConfiguration.getConfigData(RecycleBinConfig.class).maximumFileAge();

        final RecycleBinImpl recycleBin = new RecycleBinImpl(
                customConfiguration, new NoOpMetrics(), getStaticThreadManager(), time, new NodeId(0));
        recycleBin.start();

        // Recycle some files.
        final Path path1 = testDirectory.resolve("file1.txt");
        writeFile(path1, "file1");

        final Path path2 = testDirectory.resolve("file2.txt");
        writeFile(path2, "file2");

        final Path path3 = testDirectory.resolve("foo/bar/baz/file3.txt");
        writeFile(path3, "file3");

        recycleBin.recycle(path1);
        recycleBin.recycle(path2);
        recycleBin.recycle(testDirectory.resolve("foo"));

        assertFalse(Files.exists(path1));
        assertFalse(Files.exists(path2));
        assertFalse(Files.exists(path3));

        final Path recycleBinPath = testDirectory.resolve("swirlds-recycle-bin").resolve("0");
        final Path recycledPath1 = recycleBinPath.resolve("file1.txt");
        final Path recycledPath2 = recycleBinPath.resolve("file2.txt");
        final Path recycledPath3 = recycleBinPath.resolve("foo/bar/baz/file3.txt");

        // Wait some time. Although the recycle bin will have had time to delete files if it wanted to,
        // it won't have actually deleted them yet because not enough time has passed.
        for (int i = 0; i < 100; i++) {
            time.tick(Duration.ofMinutes(1));
            MILLISECONDS.sleep(5);

            assertTrue(Files.exists(recycledPath1));
            assertTrue(Files.exists(recycledPath2));
            assertTrue(Files.exists(recycledPath3));
        }

        // Advance time by the maximum file age. Now the recycle bin will be able to delete the files.
        time.tick(maximumFileAge);

        assertEventuallyDoesNotThrow(
                () -> {
                    assertFalse(Files.exists(recycledPath1));
                    assertFalse(Files.exists(recycledPath2));
                    assertFalse(Files.exists(recycledPath3));
                },
                Duration.ofSeconds(1),
                "Files were not deleted after the maximum file age elapsed.");

        recycleBin.stop();
    }

    @Test
    @DisplayName("clear() Test")
    void clearTest() throws IOException, InterruptedException {
        final FakeTime time = new FakeTime(Instant.now(), Duration.ZERO);

        final Configuration customConfiguration = new TestConfigBuilder()
                .withValue(StateConfig_.SAVED_STATE_DIRECTORY, testDirectory.toString())
                .withValue(RecycleBinConfig_.COLLECTION_PERIOD, "1ns")
                .getOrCreateConfig();

        final RecycleBinImpl recycleBin = new RecycleBinImpl(
                customConfiguration, new NoOpMetrics(), getStaticThreadManager(), time, new NodeId(0));
        recycleBin.start();

        // Recycle some files.
        final Path path1 = testDirectory.resolve("file1.txt");
        writeFile(path1, "file1");

        final Path path2 = testDirectory.resolve("file2.txt");
        writeFile(path2, "file2");

        final Path path3 = testDirectory.resolve("foo/bar/baz/file3.txt");
        writeFile(path3, "file3");

        recycleBin.recycle(path1);
        recycleBin.recycle(path2);
        recycleBin.recycle(testDirectory.resolve("foo"));

        assertFalse(Files.exists(path1));
        assertFalse(Files.exists(path2));
        assertFalse(Files.exists(path3));

        final Path recycleBinPath = testDirectory.resolve("swirlds-recycle-bin").resolve("0");
        final Path recycledPath1 = recycleBinPath.resolve("file1.txt");
        final Path recycledPath2 = recycleBinPath.resolve("file2.txt");
        final Path recycledPath3 = recycleBinPath.resolve("foo/bar/baz/file3.txt");

        // Wait some time. Although the recycle bin will have had time to delete files if it wanted to,
        // it won't have actually deleted them yet because not enough time has passed.
        for (int i = 0; i < 100; i++) {
            time.tick(Duration.ofMinutes(1));
            MILLISECONDS.sleep(5);

            assertTrue(Files.exists(recycledPath1));
            assertTrue(Files.exists(recycledPath2));
            assertTrue(Files.exists(recycledPath3));
        }

        // Manually clear the recycle bin
        recycleBin.clear();

        assertEventuallyDoesNotThrow(
                () -> {
                    assertFalse(Files.exists(recycledPath1));
                    assertFalse(Files.exists(recycledPath2));
                    assertFalse(Files.exists(recycledPath3));
                },
                Duration.ofSeconds(1),
                "Files were not deleted after the maximum file age elapsed.");

        recycleBin.stop();
    }
}
