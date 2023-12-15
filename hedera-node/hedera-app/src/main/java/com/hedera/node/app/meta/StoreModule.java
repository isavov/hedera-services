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

package com.hedera.node.app.meta;

import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.swirlds.common.utility.AutoCloseableWrapper;
import dagger.Module;
import dagger.Provides;
import java.util.function.Supplier;

@Module
public interface StoreModule {
    @HandleScope
    @Provides
    static ReadableStoreFactory provideReadableStoreFactory(final Supplier<AutoCloseableWrapper<HederaState>> state) {
        return new ReadableStoreFactory(state.get().get());
    }
}
