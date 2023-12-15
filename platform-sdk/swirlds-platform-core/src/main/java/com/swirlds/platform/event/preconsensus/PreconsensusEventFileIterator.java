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

import com.swirlds.common.io.IOIterator;
import com.swirlds.common.io.extendable.ExtendableInputStream;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.platform.event.GossipEvent;
import java.io.BufferedInputStream;
import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.NoSuchElementException;

/**
 * Iterates over the events in a single preconsensus event file.
 */
public class PreconsensusEventFileIterator implements IOIterator<GossipEvent> {

    private final long minimumGeneration;
    private final SerializableDataInputStream stream;
    private boolean hasPartialEvent = false;
    private final CountingStreamExtension counter;
    private GossipEvent next;
    private boolean streamClosed = false;

    /**
     * Create a new iterator that walks over events in a preconsensus event file.
     *
     * @param fileDescriptor
     * 		describes a preconsensus event file
     * @param minimumGeneration
     * 		the minimum generation to return, any events in the file with a smaller
     * 		generation are ignored and not returned
     */
    public PreconsensusEventFileIterator(final PreconsensusEventFile fileDescriptor, final long minimumGeneration)
            throws IOException {

        this.minimumGeneration = minimumGeneration;
        counter = new CountingStreamExtension();
        stream = new SerializableDataInputStream(new ExtendableInputStream(
                new BufferedInputStream(
                        new FileInputStream(fileDescriptor.getPath().toFile())),
                counter));

        try {
            final int fileVersion = stream.readInt();
            if (fileVersion != PreconsensusEventMutableFile.FILE_VERSION) {
                throw new IOException("unsupported file version: " + fileVersion);
            }
        } catch (final EOFException e) {
            // Empty file. Possible if the node crashed right after it created this file.
            stream.close();
            streamClosed = true;
        }
    }

    /**
     * Find the next event that should be returned.
     */
    private void findNext() throws IOException {
        while (next == null && !streamClosed) {

            final long initialCount = counter.getCount();

            try {
                final GossipEvent candidate = stream.readSerializable(false, GossipEvent::new);
                if (candidate.getGeneration() >= minimumGeneration) {
                    next = candidate;
                }
            } catch (final EOFException e) {
                if (counter.getCount() > initialCount) {
                    // We started parsing an event but couldn't find enough bytes to finish it.
                    // This is possible (if not likely) when a node is shut down abruptly.
                    hasPartialEvent = true;
                }
                stream.close();
                streamClosed = true;
            }
        }
    }

    /**
     * If true then this file contained a partial event. If false then the last event in the file was fully written
     * when the file was closed.
     */
    public boolean hasPartialEvent() {
        return hasPartialEvent;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean hasNext() throws IOException {
        findNext();
        return next != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public GossipEvent next() throws IOException {
        if (!hasNext()) {
            throw new NoSuchElementException("no files remain in this iterator");
        }
        try {
            return next;
        } finally {
            next = null;
        }
    }
}
