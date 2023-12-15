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

package com.hedera.node.config.converter;

import com.hedera.node.app.service.mono.context.properties.EntityType;
import com.swirlds.config.api.converter.ConfigConverter;
import com.swirlds.config.extensions.converters.AbstractEnumConfigConverter;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Config api {@link ConfigConverter} implementation for the type {@link EntityType}. Based on
 * https://github.com/hashgraph/hedera-services/issues/6106 we need to add {@code implements ConfigConverter} to the
 * class for now.
 */
public class EntityTypeConverter extends AbstractEnumConfigConverter<EntityType>
        implements ConfigConverter<EntityType> {

    @NonNull
    @Override
    protected Class<EntityType> getEnumType() {
        return EntityType.class;
    }
}
