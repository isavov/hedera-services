/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gossip.sync;

import static com.swirlds.common.io.extendable.ExtendableInputStream.extendInputStream;

import com.swirlds.common.config.SocketConfig;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.extendable.extensions.CountingStreamExtension;
import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.platform.event.GossipEvent;
import com.swirlds.platform.gossip.SyncException;
import com.swirlds.platform.gossip.shadowgraph.Generations;
import com.swirlds.platform.network.ByteConstants;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.zip.Inflater;
import java.util.zip.InflaterInputStream;

public class SyncInputStream extends SerializableDataInputStream {

    /** The maximum number of tips allowed per node. */
    private static final int MAX_TIPS_PER_NODE = 1000;

    private final CountingStreamExtension syncByteCounter;

    private SyncInputStream(InputStream in, CountingStreamExtension syncByteCounter) {
        super(in);
        this.syncByteCounter = syncByteCounter;
    }

    public static SyncInputStream createSyncInputStream(
            @NonNull final PlatformContext platformContext, @NonNull final InputStream in, final int bufferSize) {

        final CountingStreamExtension syncCounter = new CountingStreamExtension();

        final boolean compress = platformContext
                .getConfiguration()
                .getConfigData(SocketConfig.class)
                .gzipCompression();

        final InputStream meteredStream = extendInputStream(in, syncCounter);

        final InputStream wrappedStream;
        if (compress) {
            wrappedStream = new InflaterInputStream(meteredStream, new Inflater(true), bufferSize);
        } else {
            wrappedStream = new BufferedInputStream(meteredStream, bufferSize);
        }

        return new SyncInputStream(wrappedStream, syncCounter);
    }

    public CountingStreamExtension getSyncByteCounter() {
        return syncByteCounter;
    }

    /**
     * Reads a sync request response from the stream
     *
     * @return true if the sync has been accepted, false if it was rejected
     * @throws IOException   if a stream exception occurs
     * @throws SyncException if something unexpected has been read from the stream
     */
    public boolean readSyncRequestResponse() throws IOException, SyncException {
        final byte b = readByte();
        if (b == ByteConstants.COMM_SYNC_NACK) {
            // sync rejected
            return false;
        }
        if (b != ByteConstants.COMM_SYNC_ACK) {
            throw new SyncException(String.format(
                    "COMM_SYNC_REQUEST was sent but reply was %02x instead of COMM_SYNC_ACK or COMM_SYNC_NACK", b));
        }
        return true;
    }

    /**
     * Read the other node's generation numbers from an input stream
     *
     * @throws IOException if a stream exception occurs
     */
    public Generations readGenerations() throws IOException {
        return readSerializable(false, Generations::new);
    }

    /**
     * Read the other node's tip hashes
     *
     * @throws IOException is a stream exception occurs
     */
    public List<Hash> readTipHashes(final int numberOfNodes) throws IOException {
        return readSerializableList(numberOfNodes * MAX_TIPS_PER_NODE, false, Hash::new);
    }

    public GossipEvent readEventData() throws IOException {
        return readSerializable(false, GossipEvent::new);
    }
}
