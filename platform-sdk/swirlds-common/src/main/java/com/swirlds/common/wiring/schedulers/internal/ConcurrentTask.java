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

package com.swirlds.common.wiring.schedulers.internal;

import com.swirlds.common.wiring.counters.ObjectCounter;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Consumer;

/**
 * A task in a {@link ConcurrentTaskScheduler}.
 */
class ConcurrentTask extends AbstractTask {

    private final Consumer<Object> handler;
    private final Object data;
    private final ObjectCounter offRamp;
    private final UncaughtExceptionHandler uncaughtExceptionHandler;

    /**
     * Constructor.
     *
     * @param pool                     the fork join pool that will execute this task
     * @param offRamp                  an object counter that is decremented when this task is executed
     * @param uncaughtExceptionHandler the handler for uncaught exceptions
     * @param handler                  the method that will be called when this task is executed
     * @param data                     the data to be passed to the consumer for this task
     */
    protected ConcurrentTask(
            @NonNull final ForkJoinPool pool,
            @NonNull final ObjectCounter offRamp,
            @NonNull final UncaughtExceptionHandler uncaughtExceptionHandler,
            @NonNull final Consumer<Object> handler,
            @Nullable final Object data) {
        super(pool, 0);
        this.handler = handler;
        this.data = data;
        this.offRamp = offRamp;
        this.uncaughtExceptionHandler = uncaughtExceptionHandler;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean exec() {
        try {
            handler.accept(data);
        } catch (final Throwable t) {
            uncaughtExceptionHandler.uncaughtException(Thread.currentThread(), t);
        } finally {
            offRamp.offRamp();
        }
        return true;
    }
}
