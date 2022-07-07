package com.hedera.services.txns.util;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.stream.RecordsRunningHashLeaf;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.TxnUtils;
import com.hederahashgraph.api.proto.java.PrngTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.Hash;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_PRNG_RANGE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PrngTransitionLogicTest {
	private static final Hash aFullHash = new Hash(TxnUtils.randomUtf8Bytes(48));

	private SideEffectsTracker tracker = new SideEffectsTracker();
	@Mock
	private RecordsRunningHashLeaf runningHashLeaf;
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private SignedTxnAccessor accessor;
	@Mock
	private GlobalDynamicProperties properties;

	private PrngTransitionLogic subject;
	private TransactionBody prngTxn;
	private PrngLogic logic;

	@BeforeEach
	private void setup() {
		logic = new PrngLogic(properties, () -> runningHashLeaf, tracker);
		subject = new PrngTransitionLogic(txnCtx, logic);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx(0);

		assertTrue(subject.applicability().test(prngTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void rejectsInvalidRange() {
		givenValidTxnCtx(-10000);
		assertEquals(INVALID_PRNG_RANGE, subject.semanticCheck().apply(prngTxn));
	}

	@Test
	void returnsIfNotEnabled() {
		given(properties.isPrngEnabled()).willReturn(false);
		givenValidTxnCtx(10000);
		given(accessor.getTxn()).willReturn(prngTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		subject.doStateTransition();

		assertFalse(tracker.hasTrackedRandomData());
	}

	@Test
	void acceptsPositiveAndZeroRange() {
		givenValidTxnCtx(10000);
		assertEquals(OK, subject.semanticCheck().apply(prngTxn));

		givenValidTxnCtx(0);
		assertEquals(OK, subject.semanticCheck().apply(prngTxn));
	}

	@Test
	void acceptsNoRange() {
		givenValidTxnCtxWithoutRange();
		assertEquals(OK, subject.semanticCheck().apply(prngTxn));
	}

	@Test
	void followsHappyPathWithNoRange() throws InterruptedException {
		givenValidTxnCtxWithoutRange();
		given(properties.isPrngEnabled()).willReturn(true);
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(aFullHash);
		given(accessor.getTxn()).willReturn(prngTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		subject.doStateTransition();

		assertEquals(aFullHash.getValue(), tracker.getPseudorandomBytes());
		assertEquals(48, tracker.getPseudorandomBytes().length);
		assertEquals(-1, tracker.getPseudorandomNumber());
	}

	@Test
	void followsHappyPathWithRange() throws InterruptedException {
		givenValidTxnCtx(20);
		given(properties.isPrngEnabled()).willReturn(true);
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(aFullHash);
		given(accessor.getTxn()).willReturn(prngTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		subject.doStateTransition();

		assertNull(tracker.getPseudorandomBytes());

		final var num = tracker.getPseudorandomNumber();
		assertTrue(num >= 0 && num < 20);
	}

	@Test
	void followsHappyPathWithMaxIntegerRange() throws InterruptedException {
		givenValidTxnCtx(Integer.MAX_VALUE);
		given(properties.isPrngEnabled()).willReturn(true);
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(aFullHash);
		given(accessor.getTxn()).willReturn(prngTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		subject.doStateTransition();

		assertNull(tracker.getPseudorandomBytes());

		final var num = tracker.getPseudorandomNumber();
		assertTrue(num >= 0 && num < Integer.MAX_VALUE);
	}

	@Test
	void anyNegativeValueThrowsInPrecheck() {
		givenValidTxnCtx(Integer.MIN_VALUE);

		final var response = subject.semanticCheck().apply(prngTxn);
		assertEquals(INVALID_PRNG_RANGE, response);
	}

	@Test
	void givenRangeZeroGivesBitString() throws InterruptedException {
		givenValidTxnCtx(0);
		given(properties.isPrngEnabled()).willReturn(true);
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(aFullHash);
		given(accessor.getTxn()).willReturn(prngTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		subject.doStateTransition();

		assertEquals(aFullHash.getValue(), tracker.getPseudorandomBytes());
		assertEquals(48, tracker.getPseudorandomBytes().length);
		assertEquals(-1, tracker.getPseudorandomNumber());
	}

	@Test
	void nullHashesReturnNoRandomNumber() throws InterruptedException {
		givenValidTxnCtx(0);
		given(properties.isPrngEnabled()).willReturn(true);
		given(runningHashLeaf.nMinusThreeRunningHash()).willReturn(null);
		given(accessor.getTxn()).willReturn(prngTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		subject.doStateTransition();

		assertNull(tracker.getPseudorandomBytes());
		assertEquals(-1, tracker.getPseudorandomNumber());
	}

	@Test
	void interruptedWhileGettingHash() throws InterruptedException {
		final var sideEffectsTracker = mock(SideEffectsTracker.class);
		logic = new PrngLogic(properties, () -> runningHashLeaf, sideEffectsTracker);
		subject = new PrngTransitionLogic(txnCtx, logic);

		givenValidTxnCtx(10);
		given(properties.isPrngEnabled()).willReturn(true);
		given(runningHashLeaf.nMinusThreeRunningHash()).willThrow(InterruptedException.class);
		given(accessor.getTxn()).willReturn(prngTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		final var msg = assertThrows(IllegalStateException.class, () -> subject.doStateTransition());
		assertTrue(msg.getMessage().contains("Interrupted when computing n-3 running hash"));

		verify(sideEffectsTracker, never()).trackRandomBytes(any());
		verify(sideEffectsTracker, never()).trackRandomNumber(anyInt());
	}

	private void givenValidTxnCtx(int range) {
		final var opBuilder = PrngTransactionBody.newBuilder()
				.setRange(range);
		prngTxn = TransactionBody.newBuilder().setPrng(opBuilder).build();
	}

	private void givenValidTxnCtxWithoutRange() {
		final var opBuilder = PrngTransactionBody.newBuilder();
		prngTxn = TransactionBody.newBuilder().setPrng(opBuilder).build();
	}
}