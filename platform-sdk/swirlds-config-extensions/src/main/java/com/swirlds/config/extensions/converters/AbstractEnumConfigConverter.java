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

package com.swirlds.config.extensions.converters;

import com.swirlds.config.api.converter.ConfigConverter;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

/**
 * Abstract class to support {@link ConfigConverter} for {@link Enum} types.
 *
 * @param <E> type of the enum
 */
public abstract class AbstractEnumConfigConverter<E extends Enum<E>> implements ConfigConverter<E> {

    @Override
    public E convert(final String value) throws IllegalArgumentException, NullPointerException {
        if (value == null) {
            throw new NullPointerException("null can not be converted");
        }
        final Class<E> enumType = getEnumType();
        Objects.requireNonNull(enumType, "enumType");
        return Enum.valueOf(enumType, value);
    }

    /**
     * Returns the {@link Class} of the {@link Enum} type.
     *
     * @return the {@link Class} of the {@link Enum} type
     */
    @NonNull
    protected abstract Class<E> getEnumType();
}
