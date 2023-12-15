/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.state.signed;

import static com.swirlds.common.io.streams.StreamDebugUtils.deserializeAndDebugOnFailure;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.MAX_MERKLE_NODES_IN_STATE;
import static com.swirlds.platform.state.signed.SignedStateFileUtils.VERSIONED_FILE_BYTE;
import static java.nio.file.Files.exists;

import com.swirlds.common.config.StateConfig;
import com.swirlds.common.config.singleton.ConfigurationHolder;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Hash;
import com.swirlds.common.io.streams.MerkleDataInputStream;
import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.state.State;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

/**
 * Utility methods for reading a signed state from disk.
 */
public final class SignedStateFileReader {
    private SignedStateFileReader() {}

    /**
     * Same as {@link SignedStateFilePath#getSavedStateFiles(String, NodeId, String)} but uses the config from
     * {@link ConfigurationHolder}
     *
     * @deprecated this uses a static config, which means that a unit test cannot configure it for its scope. this
     * causes unit tests to fail randomly if another test sets an inadequate value in the config holder.
     */
    @Deprecated(forRemoval = true)
    @NonNull
    public static List<SavedStateInfo> getSavedStateFiles(
            final String mainClassName, final NodeId platformId, final String swirldName) {
        // new instance on every call in case the config changes in the holder
        return new SignedStateFilePath(ConfigurationHolder.getConfigData(StateConfig.class))
                .getSavedStateFiles(mainClassName, platformId, swirldName);
    }

    /**
     * Reads a SignedState from disk
     *
     * @param platformContext the platform context
     * @param stateFile
     * 		the file to read from
     * @return a signed state with it's associated hash (as computed when the state was serialized)
     * @throws IOException
     * 		if there is any problems with reading from a file
     */
    public static @NonNull DeserializedSignedState readStateFile(
            @NonNull final PlatformContext platformContext, @NonNull final Path stateFile) throws IOException {

        Objects.requireNonNull(platformContext);
        Objects.requireNonNull(stateFile);

        checkSignedStatePath(stateFile);

        final DeserializedSignedState returnState;

        record StateFileData(State state, Hash hash, SigSet sigSet) {}

        final StateFileData data = deserializeAndDebugOnFailure(
                () -> new BufferedInputStream(new FileInputStream(stateFile.toFile())),
                (final MerkleDataInputStream in) -> {
                    readAndCheckVersion(in);

                    final Path directory = stateFile.getParent();

                    final State state = in.readMerkleTree(directory, MAX_MERKLE_NODES_IN_STATE);
                    final Hash hash = in.readSerializable();
                    final SigSet sigSet = in.readSerializable();

                    return new StateFileData(state, hash, sigSet);
                });

        final SignedState newSignedState =
                new SignedState(platformContext, data.state(), "SignedStateFileReader.readStateFile()", false);

        newSignedState.setSigSet(data.sigSet());

        returnState = new DeserializedSignedState(
                newSignedState.reserve("SignedStateFileReader.readStateFile()"), data.hash());

        return returnState;
    }

    /**
     * Read only the signed state from a file, do not read the hash and signatures
     *
     * @param platformContext the platform context
     * @param stateFile the file to read from
     * @return the signed state read
     * @throws IOException if any problem occurs while reading
     */
    public static @NonNull SignedState readSignedStateOnly(
            @NonNull final PlatformContext platformContext, @NonNull final Path stateFile) throws IOException {
        checkSignedStatePath(stateFile);

        return deserializeAndDebugOnFailure(
                () -> new BufferedInputStream(new FileInputStream(stateFile.toFile())),
                (final MerkleDataInputStream in) -> {
                    readAndCheckVersion(in);
                    return new SignedState(
                            platformContext,
                            in.readMerkleTree(stateFile.getParent(), MAX_MERKLE_NODES_IN_STATE),
                            "SignedStateFileReader.readSignedStateOnly()",
                            false);
                });
    }

    /**
     * Check the path of a signed state file
     *
     * @param stateFile the path to check
     * @throws IOException if the path is not valid
     */
    private static void checkSignedStatePath(@NonNull final Path stateFile) throws IOException {
        if (!exists(stateFile)) {
            throw new IOException("File " + stateFile.toAbsolutePath() + " does not exist!");
        }
        if (!Files.isRegularFile(stateFile)) {
            throw new IOException("File " + stateFile.toAbsolutePath() + " is not a file!");
        }
    }

    /**
     * Read the version from a signed state file and check it
     *
     * @param in the stream to read from
     * @throws IOException if the version is invalid
     */
    private static void readAndCheckVersion(@NonNull final MerkleDataInputStream in) throws IOException {
        final byte versionByte = in.readByte();
        if (versionByte != VERSIONED_FILE_BYTE) {
            throw new IOException("File is not versioned -- data corrupted or is an unsupported legacy state");
        }

        in.readInt(); // file version
        in.readProtocolVersion();
    }
}
