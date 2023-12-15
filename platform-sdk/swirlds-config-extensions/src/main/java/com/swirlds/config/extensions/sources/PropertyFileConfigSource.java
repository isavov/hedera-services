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

package com.swirlds.config.extensions.sources;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/**
 * A {@link com.swirlds.config.api.source.ConfigSource} implementation that can be used to provide values from a
 * property file.
 */
public class PropertyFileConfigSource extends AbstractConfigSource {

    private final Map<String, String> internalProperties;

    private final Path filePath;

    private final int ordinal;

    /**
     * Creates a new instance based on a file by using the {@link ConfigSourceOrdinalConstants#PROPERTY_FILE_ORDINAL}
     * ordinal
     *
     * @param filePath the properties file
     * @throws IOException if the file can not loaded or parsed
     */
    public PropertyFileConfigSource(@NonNull final Path filePath) throws IOException {
        this(filePath, ConfigSourceOrdinalConstants.PROPERTY_FILE_ORDINAL);
    }

    /**
     * Creates a new instance based on a file
     *
     * @param filePath the properties file
     * @param ordinal  the ordinal of the config source
     * @throws IOException if the file can not loaded or parsed
     */
    public PropertyFileConfigSource(@NonNull final Path filePath, final int ordinal) throws IOException {
        this.filePath = Objects.requireNonNull(filePath, "filePath can not be null");
        this.ordinal = ordinal;
        this.internalProperties = new HashMap<>();
        try (final BufferedReader reader = Files.newBufferedReader(filePath)) {
            final Properties loadedProperties = new Properties();
            loadedProperties.load(reader);
            loadedProperties
                    .stringPropertyNames()
                    .forEach(name -> internalProperties.put(name, loadedProperties.getProperty(name)));
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return "Property file config source for " + filePath;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Map<String, String> getInternalProperties() {
        return Collections.unmodifiableMap(internalProperties);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getOrdinal() {
        return ordinal;
    }
}
