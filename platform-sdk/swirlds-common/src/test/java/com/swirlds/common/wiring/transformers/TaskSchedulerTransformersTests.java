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

package com.swirlds.common.wiring.transformers;

import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyEquals;
import static com.swirlds.common.test.fixtures.AssertionUtils.assertEventuallyTrue;
import static com.swirlds.common.utility.NonCryptographicHashing.hash32;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.swirlds.common.wiring.model.WiringModel;
import com.swirlds.common.wiring.schedulers.TaskScheduler;
import com.swirlds.common.wiring.wires.input.BindableInputWire;
import com.swirlds.common.wiring.wires.output.OutputWire;
import com.swirlds.test.framework.TestWiringModelBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.lang.Thread.UncaughtExceptionHandler;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class TaskSchedulerTransformersTests {

    @Test
    void wireListSplitterTest() {
        final WiringModel model = TestWiringModelBuilder.create();

        // Component A produces lists of integers. It passes data to B, C, and D.
        // Components B and C want individual integers. Component D wants the full list of integers.

        final TaskScheduler<List<Integer>> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final BindableInputWire<Integer, List<Integer>> wireAIn = taskSchedulerA.buildInputWire("A in");

        final TaskScheduler<Void> taskSchedulerB =
                model.schedulerBuilder("B").build().cast();
        final BindableInputWire<Integer, Void> wireBIn = taskSchedulerB.buildInputWire("B in");

        final TaskScheduler<Void> taskSchedulerC =
                model.schedulerBuilder("C").build().cast();
        final BindableInputWire<Integer, Void> wireCIn = taskSchedulerC.buildInputWire("C in");

        final TaskScheduler<Void> taskSchedulerD =
                model.schedulerBuilder("D").build().cast();
        final BindableInputWire<List<Integer>, Void> wireDIn = taskSchedulerD.buildInputWire("D in");

        final OutputWire<Integer> splitter = taskSchedulerA.getOutputWire().buildSplitter();
        splitter.solderTo(wireBIn);
        splitter.solderTo(wireCIn);
        taskSchedulerA.getOutputWire().solderTo(wireDIn);

        wireAIn.bind(x -> {
            return List.of(x, x, x);
        });

        final AtomicInteger countB = new AtomicInteger(0);
        wireBIn.bind(x -> {
            countB.set(hash32(countB.get(), x));
        });

        final AtomicInteger countC = new AtomicInteger(0);
        wireCIn.bind(x -> {
            countC.set(hash32(countC.get(), -x));
        });

        final AtomicInteger countD = new AtomicInteger(0);
        wireDIn.bind(x -> {
            int product = 1;
            for (final int i : x) {
                product *= i;
            }
            countD.set(hash32(countD.get(), product));
        });

        int expectedCountB = 0;
        int expectedCountC = 0;
        int expectedCountD = 0;

        for (int i = 0; i < 100; i++) {
            wireAIn.put(i);

            for (int j = 0; j < 3; j++) {
                expectedCountB = hash32(expectedCountB, i);
                expectedCountC = hash32(expectedCountC, -i);
            }

            expectedCountD = hash32(expectedCountD, i * i * i);
        }

        assertEventuallyEquals(expectedCountB, countB::get, Duration.ofSeconds(1), "B did not receive all data");
        assertEventuallyEquals(expectedCountC, countC::get, Duration.ofSeconds(1), "C did not receive all data");
        assertEventuallyEquals(expectedCountD, countD::get, Duration.ofSeconds(1), "D did not receive all data");
    }

    @Test
    void wireFilterTest() {
        final WiringModel model = TestWiringModelBuilder.create();

        // Wire A passes data to B, C, and a lambda.
        // B wants all of A's data, but C and the lambda only want even values.

        final TaskScheduler<Integer> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final BindableInputWire<Integer, Integer> inA = taskSchedulerA.buildInputWire("A in");

        final TaskScheduler<Void> taskSchedulerB =
                model.schedulerBuilder("B").build().cast();
        final BindableInputWire<Integer, Void> inB = taskSchedulerB.buildInputWire("B in");

        final TaskScheduler<Void> taskSchedulerC =
                model.schedulerBuilder("C").build().cast();
        final BindableInputWire<Integer, Void> inC = taskSchedulerC.buildInputWire("C in");

        final AtomicInteger countA = new AtomicInteger(0);
        final AtomicInteger countB = new AtomicInteger(0);
        final AtomicInteger countC = new AtomicInteger(0);
        final AtomicInteger countLambda = new AtomicInteger(0);

        taskSchedulerA.getOutputWire().solderTo(inB);
        final OutputWire<Integer> filter = taskSchedulerA.getOutputWire().buildFilter("onlyEven", x -> x % 2 == 0);
        filter.solderTo(inC);
        filter.solderTo("lambda", x -> countLambda.set(hash32(countLambda.get(), x)));

        inA.bind(x -> {
            countA.set(hash32(countA.get(), x));
            return x;
        });

        inB.bind(x -> {
            countB.set(hash32(countB.get(), x));
        });

        inC.bind(x -> {
            countC.set(hash32(countC.get(), x));
        });

        int expectedCount = 0;
        int expectedEvenCount = 0;

        for (int i = 0; i < 100; i++) {
            inA.put(i);
            expectedCount = hash32(expectedCount, i);
            if (i % 2 == 0) {
                expectedEvenCount = hash32(expectedEvenCount, i);
            }
        }

        assertEventuallyEquals(expectedCount, countA::get, Duration.ofSeconds(1), "A did not receive all data");
        assertEventuallyEquals(expectedCount, countB::get, Duration.ofSeconds(1), "B did not receive all data");
        assertEventuallyEquals(expectedEvenCount, countC::get, Duration.ofSeconds(1), "C did not receive all data");
        assertEventuallyEquals(
                expectedEvenCount, countLambda::get, Duration.ofSeconds(1), "Lambda did not receive all data");
    }

    private record TestData(int value, boolean invert) {}

    @Test
    void wireTransformerTest() {
        final WiringModel model = TestWiringModelBuilder.create();

        // A produces data of type TestData.
        // B wants all of A's data, C wants the integer values, and D wants the boolean values.

        final TaskScheduler<TestData> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final BindableInputWire<TestData, TestData> inA = taskSchedulerA.buildInputWire("A in");

        final TaskScheduler<Void> taskSchedulerB =
                model.schedulerBuilder("B").build().cast();
        final BindableInputWire<TestData, Void> inB = taskSchedulerB.buildInputWire("B in");

        final TaskScheduler<Void> taskSchedulerC =
                model.schedulerBuilder("C").build().cast();
        final BindableInputWire<Integer, Void> inC = taskSchedulerC.buildInputWire("C in");

        final TaskScheduler<Void> taskSchedulerD =
                model.schedulerBuilder("D").build().cast();
        final BindableInputWire<Boolean, Void> inD = taskSchedulerD.buildInputWire("D in");

        taskSchedulerA.getOutputWire().solderTo(inB);
        taskSchedulerA
                .getOutputWire()
                .buildTransformer("getValue", TestData::value)
                .solderTo(inC);
        taskSchedulerA
                .getOutputWire()
                .buildTransformer("getInvert", TestData::invert)
                .solderTo(inD);

        final AtomicInteger countA = new AtomicInteger(0);
        inA.bind(x -> {
            final int invert = x.invert() ? -1 : 1;
            countA.set(hash32(countA.get(), x.value() * invert));
            return x;
        });

        final AtomicInteger countB = new AtomicInteger(0);
        inB.bind(x -> {
            final int invert = x.invert() ? -1 : 1;
            countB.set(hash32(countB.get(), x.value() * invert));
        });

        final AtomicInteger countC = new AtomicInteger(0);
        inC.bind(x -> {
            countC.set(hash32(countC.get(), x));
        });

        final AtomicInteger countD = new AtomicInteger(0);
        inD.bind(x -> {
            countD.set(hash32(countD.get(), x ? 1 : 0));
        });

        int expectedCountAB = 0;
        int expectedCountC = 0;
        int expectedCountD = 0;

        for (int i = 0; i < 100; i++) {
            final boolean invert = i % 3 == 0;
            inA.put(new TestData(i, invert));

            expectedCountAB = hash32(expectedCountAB, i * (invert ? -1 : 1));
            expectedCountC = hash32(expectedCountC, i);
            expectedCountD = hash32(expectedCountD, invert ? 1 : 0);
        }

        assertEventuallyEquals(expectedCountAB, countA::get, Duration.ofSeconds(1), "A did not receive all data");
        assertEventuallyEquals(expectedCountAB, countB::get, Duration.ofSeconds(1), "B did not receive all data");
        assertEventuallyEquals(expectedCountC, countC::get, Duration.ofSeconds(1), "C did not receive all data");
        assertEventuallyEquals(expectedCountD, countD::get, Duration.ofSeconds(1), "D did not receive all data");
    }

    /**
     * This test performs the same actions as the {@link #wireTransformerTest()} test, but it uses the advanced
     * transformer implementation. It should be possible to perform these tasks with both implementations.
     */
    @Test
    void advancedWireTransformerSimpleTaskTest() {
        final WiringModel model = TestWiringModelBuilder.create();

        // A produces data of type TestData.
        // B wants all of A's data, C wants the integer values, and D wants the boolean values.

        final TaskScheduler<TestData> taskSchedulerA =
                model.schedulerBuilder("A").build().cast();
        final BindableInputWire<TestData, TestData> inA = taskSchedulerA.buildInputWire("A in");

        final TaskScheduler<Void> taskSchedulerB =
                model.schedulerBuilder("B").build().cast();
        final BindableInputWire<TestData, Void> inB = taskSchedulerB.buildInputWire("B in");

        final TaskScheduler<Void> taskSchedulerC =
                model.schedulerBuilder("C").build().cast();
        final BindableInputWire<Integer, Void> inC = taskSchedulerC.buildInputWire("C in");

        final TaskScheduler<Void> taskSchedulerD =
                model.schedulerBuilder("D").build().cast();
        final BindableInputWire<Boolean, Void> inD = taskSchedulerD.buildInputWire("D in");

        taskSchedulerA.getOutputWire().solderTo(inB);
        taskSchedulerA
                .getOutputWire()
                .buildAdvancedTransformer("getValue", TestData::value, null)
                .solderTo(inC);
        taskSchedulerA
                .getOutputWire()
                .buildAdvancedTransformer("getInvert", TestData::invert, null)
                .solderTo(inD);

        final AtomicInteger countA = new AtomicInteger(0);
        inA.bind(x -> {
            final int invert = x.invert() ? -1 : 1;
            countA.set(hash32(countA.get(), x.value() * invert));
            return x;
        });

        final AtomicInteger countB = new AtomicInteger(0);
        inB.bind(x -> {
            final int invert = x.invert() ? -1 : 1;
            countB.set(hash32(countB.get(), x.value() * invert));
        });

        final AtomicInteger countC = new AtomicInteger(0);
        inC.bind(x -> {
            countC.set(hash32(countC.get(), x));
        });

        final AtomicInteger countD = new AtomicInteger(0);
        inD.bind(x -> {
            countD.set(hash32(countD.get(), x ? 1 : 0));
        });

        int expectedCountAB = 0;
        int expectedCountC = 0;
        int expectedCountD = 0;

        for (int i = 0; i < 100; i++) {
            final boolean invert = i % 3 == 0;
            inA.put(new TestData(i, invert));

            expectedCountAB = hash32(expectedCountAB, i * (invert ? -1 : 1));
            expectedCountC = hash32(expectedCountC, i);
            expectedCountD = hash32(expectedCountD, invert ? 1 : 0);
        }

        assertEventuallyEquals(expectedCountAB, countA::get, Duration.ofSeconds(1), "A did not receive all data");
        assertEventuallyEquals(expectedCountAB, countB::get, Duration.ofSeconds(1), "B did not receive all data");
        assertEventuallyEquals(expectedCountC, countC::get, Duration.ofSeconds(1), "C did not receive all data");
        assertEventuallyEquals(expectedCountD, countD::get, Duration.ofSeconds(1), "D did not receive all data");
    }

    /**
     * A test object with vaguely similar semantics to a reserved signed state.
     */
    private static class FooBar {
        private final AtomicInteger referenceCount;

        public FooBar() {
            referenceCount = new AtomicInteger(1);
        }

        private FooBar(@NonNull final FooBar that) {
            this.referenceCount = that.referenceCount;
        }

        /**
         * Make a copy and increase the reference count.
         *
         * @return a copy of this object
         */
        @NonNull
        public FooBar copyAndReserve() {
            final int previousCount = referenceCount.getAndIncrement();
            if (previousCount == 0) {
                throw new IllegalStateException("Cannot reserve a copy once it has been fully released");
            }

            return new FooBar(this);
        }

        /**
         * Release this copy.
         */
        public void release() {
            final int count = referenceCount.decrementAndGet();
            if (count < 0) {
                throw new IllegalStateException("Cannot release a copy more times than it was reserved");
            }
        }

        /**
         * Get the reference count.
         *
         * @return the reference count
         */
        public int getReferenceCount() {
            return referenceCount.get();
        }
    }

    /**
     * Test a wiring setup that vaguely resembles the way states are reserved and passed around. How to pass around
     * state reservations was the original use case for advanced wire transformers.
     */
    @Test
    void advancedWireTransformerTest() {
        // Component A passes data to components B, C, and D.
        final WiringModel model = TestWiringModelBuilder.create();

        final AtomicBoolean error = new AtomicBoolean(false);
        final UncaughtExceptionHandler exceptionHandler = (t, e) -> error.set(true);

        final TaskScheduler<FooBar> taskSchedulerA = model.schedulerBuilder("A")
                .withUncaughtExceptionHandler(exceptionHandler)
                .build()
                .cast();
        final BindableInputWire<FooBar, FooBar> inA = taskSchedulerA.buildInputWire("A in");
        final OutputWire<FooBar> outA = taskSchedulerA.getOutputWire();
        final OutputWire<FooBar> outAReserved =
                outA.buildAdvancedTransformer("reserve FooBar", FooBar::copyAndReserve, FooBar::release);

        final TaskScheduler<Void> taskSchedulerB = model.schedulerBuilder("B")
                .withUncaughtExceptionHandler(exceptionHandler)
                .build()
                .cast();
        final BindableInputWire<FooBar, Void> inB = taskSchedulerB.buildInputWire("B in");

        final TaskScheduler<Void> taskSchedulerC = model.schedulerBuilder("C")
                .withUncaughtExceptionHandler(exceptionHandler)
                .build()
                .cast();
        final BindableInputWire<FooBar, Void> inC = taskSchedulerC.buildInputWire("C in");

        final TaskScheduler<Void> taskSchedulerD = model.schedulerBuilder("D")
                .withUncaughtExceptionHandler(exceptionHandler)
                .build()
                .cast();
        final BindableInputWire<FooBar, Void> inD = taskSchedulerD.buildInputWire("D in");

        outAReserved.solderTo(inB);
        outAReserved.solderTo(inC);
        outAReserved.solderTo(inD);

        final AtomicInteger countA = new AtomicInteger();
        inA.bind(x -> {
            assertTrue(x.getReferenceCount() > 0);
            countA.getAndIncrement();
            return x;
        });

        final AtomicInteger countB = new AtomicInteger();
        inB.bind(x -> {
            assertTrue(x.getReferenceCount() > 0);
            countB.getAndIncrement();
            x.release();
        });

        final AtomicInteger countC = new AtomicInteger();
        inC.bind(x -> {
            assertTrue(x.getReferenceCount() > 0);
            countC.getAndIncrement();
            x.release();
        });

        final AtomicInteger countD = new AtomicInteger();
        inD.bind(x -> {
            assertTrue(x.getReferenceCount() > 0);
            countD.getAndIncrement();
            x.release();
        });

        final List<FooBar> fooBars = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            final FooBar fooBar = new FooBar();
            fooBars.add(fooBar);
            inA.put(fooBar);
        }

        assertEventuallyEquals(100, countA::get, Duration.ofSeconds(1), "A did not receive all data");
        assertEventuallyEquals(100, countB::get, Duration.ofSeconds(1), "B did not receive all data");
        assertEventuallyEquals(100, countC::get, Duration.ofSeconds(1), "C did not receive all data");
        assertEventuallyEquals(100, countD::get, Duration.ofSeconds(1), "D did not receive all data");

        assertEventuallyTrue(
                () -> {
                    for (final FooBar fooBar : fooBars) {
                        if (fooBar.getReferenceCount() != 0) {
                            return false;
                        }
                    }
                    return true;
                },
                Duration.ofSeconds(1),
                "Not all FooBars were released");

        assertFalse(error.get());

        model.stop();
    }

    private record FooBarTransformer(String name) implements AdvancedTransformation<FooBar, FooBar> {
        @Nullable
        @Override
        public FooBar transform(@NonNull final FooBar fooBar) {
            return fooBar.copyAndReserve();
        }

        @Override
        public void cleanup(@NonNull final FooBar fooBar) {
            fooBar.release();
        }

        @NonNull
        @Override
        public String getName() {
            return name;
        }
    }

    /**
     * Tests the version of the buildAdvancedTransformer() method that takes a single object implementing
     * {@link AdvancedTransformation}.
     */
    @Test
    void advancedWireTransformerInterfaceVariationTest() {
        // Component A passes data to components B, C, and D.
        final WiringModel model = TestWiringModelBuilder.create();

        final AtomicBoolean error = new AtomicBoolean(false);
        final UncaughtExceptionHandler exceptionHandler = (t, e) -> error.set(true);

        final TaskScheduler<FooBar> taskSchedulerA = model.schedulerBuilder("A")
                .withUncaughtExceptionHandler(exceptionHandler)
                .build()
                .cast();
        final BindableInputWire<FooBar, FooBar> inA = taskSchedulerA.buildInputWire("A in");
        final OutputWire<FooBar> outA = taskSchedulerA.getOutputWire();
        final OutputWire<FooBar> outAReserved = outA.buildAdvancedTransformer(new FooBarTransformer("reserve FooBar"));

        final TaskScheduler<Void> taskSchedulerB = model.schedulerBuilder("B")
                .withUncaughtExceptionHandler(exceptionHandler)
                .build()
                .cast();
        final BindableInputWire<FooBar, Void> inB = taskSchedulerB.buildInputWire("B in");

        final TaskScheduler<Void> taskSchedulerC = model.schedulerBuilder("C")
                .withUncaughtExceptionHandler(exceptionHandler)
                .build()
                .cast();
        final BindableInputWire<FooBar, Void> inC = taskSchedulerC.buildInputWire("C in");

        final TaskScheduler<Void> taskSchedulerD = model.schedulerBuilder("D")
                .withUncaughtExceptionHandler(exceptionHandler)
                .build()
                .cast();
        final BindableInputWire<FooBar, Void> inD = taskSchedulerD.buildInputWire("D in");

        outAReserved.solderTo(inB);
        outAReserved.solderTo(inC);
        outAReserved.solderTo(inD);

        final AtomicInteger countA = new AtomicInteger();
        inA.bind(x -> {
            assertTrue(x.getReferenceCount() > 0);
            countA.getAndIncrement();
            return x;
        });

        final AtomicInteger countB = new AtomicInteger();
        inB.bind(x -> {
            assertTrue(x.getReferenceCount() > 0);
            countB.getAndIncrement();
            x.release();
        });

        final AtomicInteger countC = new AtomicInteger();
        inC.bind(x -> {
            assertTrue(x.getReferenceCount() > 0);
            countC.getAndIncrement();
            x.release();
        });

        final AtomicInteger countD = new AtomicInteger();
        inD.bind(x -> {
            assertTrue(x.getReferenceCount() > 0);
            countD.getAndIncrement();
            x.release();
        });

        final List<FooBar> fooBars = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            final FooBar fooBar = new FooBar();
            fooBars.add(fooBar);
            inA.put(fooBar);
        }

        assertEventuallyEquals(100, countA::get, Duration.ofSeconds(1), "A did not receive all data");
        assertEventuallyEquals(100, countB::get, Duration.ofSeconds(1), "B did not receive all data");
        assertEventuallyEquals(100, countC::get, Duration.ofSeconds(1), "C did not receive all data");
        assertEventuallyEquals(100, countD::get, Duration.ofSeconds(1), "D did not receive all data");

        assertEventuallyTrue(
                () -> {
                    for (final FooBar fooBar : fooBars) {
                        if (fooBar.getReferenceCount() != 0) {
                            return false;
                        }
                    }
                    return true;
                },
                Duration.ofSeconds(1),
                "Not all FooBars were released");

        assertFalse(error.get());

        model.stop();
    }
}
