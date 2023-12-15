/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.event.preconsensus;

import com.swirlds.common.io.extendable.ExtendableOutputStream;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.platform.event.GossipEvent;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Represents a preconsensus event file that can be written to.
 */
public class PreconsensusEventMutableFile {
    /** the file version to write at the beginning of the file. atm, this is just a placeholder for future changes */
    public static final int FILE_VERSION = 1;

    /**
     * Describes the file that is being written to.
     */
    private final PreconsensusEventFile descriptor;

    /**
     * Counts the bytes written to the file.
     */
    private final CountingStreamExtension counter;

    /**
     * The highest generation of all events written to the file.
     */
    private long highestGenerationInFile;

    /**
     * The output stream to write to.
     */
    private final SerializableDataOutputStream out;

    /**
     * Create a new preconsensus event file that can be written to.
     *
     * @param descriptor a description of the file
     */
    PreconsensusEventMutableFile(@NonNull final PreconsensusEventFile descriptor) throws IOException {
        if (Files.exists(descriptor.getPath())) {
            throw new IOException("File " + descriptor.getPath() + " already exists");
        }

        Files.createDirectories(descriptor.getPath().getParent());

        this.descriptor = descriptor;
        counter = new CountingStreamExtension(false);
        out = new SerializableDataOutputStream(new ExtendableOutputStream(
                new BufferedOutputStream(
                        new FileOutputStream(descriptor.getPath().toFile())),
                counter));
        out.writeInt(FILE_VERSION);
        highestGenerationInFile = descriptor.getMinimumGeneration();
    }

    /**
     * Check if this file is eligible to contain an event based on generational bounds.
     *
     * @param generation the generation of the event in question
     * @return true if this file is eligible to contain the event
     */
    public boolean canContain(final long generation) {
        return descriptor.canContain(generation);
    }

    /**
     * Write an event to the file.
     *
     * @param event the event to write
     */
    public void writeEvent(final GossipEvent event) throws IOException {
        if (!descriptor.canContain(event.getGeneration())) {
            throw new IllegalStateException(
                    "Cannot write event " + event.getHashedData().getHash() + " with generation "
                            + event.getGeneration() + " to file " + descriptor);
        }
        out.writeSerializable(event, false);
        highestGenerationInFile = Math.max(highestGenerationInFile, event.getGeneration());
    }

    /**
     * Atomically rename this file so that its un-utilized span is 0.
     *
     * @param highestGenerationInPreviousFile the previous file's highest generation. Even if we are not utilizing the
     *                                        entire span of this file, we cannot reduce the highest generation so that
     *                                        it is smaller than the previous file's highest generation.
     * @return the new span compressed file
     */
    public PreconsensusEventFile compressGenerationalSpan(final long highestGenerationInPreviousFile) {
        if (highestGenerationInFile == descriptor.getMaximumGeneration()) {
            // No need to compress, we used the entire span.
            return descriptor;
        }

        final PreconsensusEventFile newDescriptor = descriptor.buildFileWithCompressedSpan(
                Math.max(highestGenerationInFile, highestGenerationInPreviousFile));

        try {
            Files.move(descriptor.getPath(), newDescriptor.getPath(), StandardCopyOption.ATOMIC_MOVE);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }

        return newDescriptor;
    }

    /**
     * Flush the file.
     */
    public void flush() throws IOException {
        out.flush();
    }

    /**
     * Close the file.
     */
    public void close() throws IOException {
        out.close();
    }

    /**
     * Get the current size of the file, in bytes.
     *
     * @return the size of the file in bytes
     */
    public long fileSize() {
        return counter.getCount();
    }

    /**
     * Get the difference between the highest generation written to the file and the lowest legal generation for this
     * file. Higher values mean that the maximum generation was chosen well.
     */
    public long getUtilizedGenerationalSpan() {
        return highestGenerationInFile - descriptor.getMinimumGeneration();
    }

    /**
     * Get the generational span that is unused in this file. Low values mean that the maximum generation was chosen
     * well, resulting in less overlap between files. A value of 0 represents a "perfect" choice.
     */
    public long getUnUtilizedGenerationalSpan() {
        return descriptor.getMaximumGeneration() - highestGenerationInFile;
    }

    /**
     * Get the span of generations that this file can legally contain.
     */
    public long getGenerationalSpan() {
        return descriptor.getMaximumGeneration() - descriptor.getMinimumGeneration();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        return descriptor.toString();
    }
}
