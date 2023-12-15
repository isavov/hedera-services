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

package com.hedera.services.bdd.junit;

import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.isRecordFile;
import static com.hedera.node.app.hapi.utils.exports.recordstreaming.RecordStreamingUtils.isSidecarFile;

import java.io.File;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.commons.io.monitor.FileAlterationListenerAdaptor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * A small utility class that listens for record stream files and provides them to any subscribed
 * listeners.
 */
public class BroadcastingRecordStreamListener extends FileAlterationListenerAdaptor {
    private static final Logger log = LogManager.getLogger(BroadcastingRecordStreamListener.class);
    private final List<StreamDataListener> listeners = new CopyOnWriteArrayList<>();

    /**
     * Subscribes a listener to receive record stream items.
     *
     * @param listener the listener to subscribe
     * @return a runnable that can be used to unsubscribe the listener
     */
    public Runnable subscribe(final StreamDataListener listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    enum FileType {
        RECORD_STREAM_FILE,
        SIDE_CAR_FILE,
        OTHER
    }

    @Override
    public void onFileCreate(final File file) {
        final var newFilePath = file.getPath();

        final var fileType = typeOf(file);
        switch (fileType) {
            case RECORD_STREAM_FILE -> {
                var retryCount = 0;
                while (true) {
                    retryCount++;
                    try {
                        exposeItems(file);
                        return;
                    } catch (UncheckedIOException e) {
                        log.warn(
                                "Attempt #{} - an error occurred trying to parse" + " recordStream file {} - {}.",
                                retryCount,
                                newFilePath,
                                e);

                        if (retryCount < 8) {
                            try {
                                Thread.sleep(500);
                            } catch (InterruptedException ignored) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        } else {
                            log.fatal("Could not read recordStream file {} - {}, exiting now.", newFilePath, e);
                            throw new IllegalStateException();
                        }
                    }
                }
            }
            case SIDE_CAR_FILE -> exposeSidecars(file);
            case OTHER -> {
                // Nothing to expose
            }
        }
    }

    private void exposeSidecars(final File file) {
        log.info("Providing validators with access to sidecar stream file {}", file.getAbsolutePath());
        final var contents = RecordStreamAccess.ensurePresentSidecarFile(file.getAbsolutePath());
        contents.getSidecarRecordsList().forEach(sidecar -> listeners.forEach(l -> l.onNewSidecar(sidecar)));
    }

    private void exposeItems(final File file) {
        log.info("Providing validators with access to record stream file {}", file.getAbsolutePath());
        final var contents = RecordStreamAccess.ensurePresentRecordFile(file.getAbsolutePath());
        contents.getRecordStreamItemsList().forEach(item -> listeners.forEach(l -> l.onNewItem(item)));
    }

    public int numListeners() {
        return listeners.size();
    }

    private FileType typeOf(final File file) {
        if (isRecordFile(file.getName())) {
            return FileType.RECORD_STREAM_FILE;
        } else if (isSidecarFile(file.getName())) {
            return FileType.SIDE_CAR_FILE;
        } else {
            return FileType.OTHER;
        }
    }
}
