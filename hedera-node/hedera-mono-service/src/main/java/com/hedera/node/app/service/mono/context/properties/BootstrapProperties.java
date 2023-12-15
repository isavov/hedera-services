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

package com.hedera.node.app.service.mono.context.properties;

import static com.hedera.node.app.service.mono.context.properties.PropUtils.loadOverride;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_ADDRESS_BOOK_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_BLOCKLIST_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_BLOCKLIST_PATH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_EXCHANGE_RATES_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_FEE_SCHEDULE_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_FREEZE_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_LAST_THROTTLE_EXEMPT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_MAX_NUM;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_NODE_REWARD_ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_STAKING_REWARD_ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_STORE_ON_DISK;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_SYSTEM_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_SYSTEM_DELETE_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_SYSTEM_UNDELETE_ADMIN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ACCOUNTS_TREASURY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.AUTO_CREATION_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.AUTO_RENEW_GRACE_PERIOD;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.AUTO_RENEW_GRANT_FREE_RENEWALS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.AUTO_RENEW_TARGET_TYPES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BALANCES_COMPRESS_ON_CREATION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BALANCES_EXPORT_DIR_PATH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BALANCES_EXPORT_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BALANCES_EXPORT_PERIOD_SECS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BALANCES_EXPORT_TOKEN_BALANCES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BALANCES_NODE_BALANCE_WARN_THRESHOLD;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_FEE_SCHEDULE_JSON_RESOURCE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_GENESIS_PUBLIC_KEY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_HAPI_PERMISSIONS_PATH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_NETWORK_PROPERTIES_PATH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_RATES_CURRENT_CENT_EQUIV;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_RATES_CURRENT_EXPIRY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_RATES_NEXT_CENT_EQUIV;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_RATES_NEXT_EXPIRY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_RATES_NEXT_HBAR_EQUIV;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_SYSTEM_ENTITY_EXPIRY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.BOOTSTRAP_THROTTLE_DEF_JSON_RESOURCE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CACHE_CRYPTO_TRANSFER_WARM_THREADS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CACHE_RECORDS_TTL;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONFIG_VERSION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_ALLOW_AUTO_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_ALLOW_CREATE2;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_CHAIN_ID;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_DEFAULT_LIFETIME;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_DYNAMIC_EVM_VERSION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_ENFORCE_CREATION_THROTTLE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_EVM_VERSION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_FREE_STORAGE_TIER_LIMIT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_ITEMIZE_STORAGE_FEES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_KEYS_LEGACY_ACTIVATIONS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_KNOWN_BLOCK_HASH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_LOCAL_CALL_EST_RET_BYTES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_MAX_GAS_PER_SEC;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_MAX_KV_PAIRS_AGGREGATE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_MAX_NUM;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_NONCES_EXTERNALIZATION_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PERMITTED_DELEGATE_CALLERS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_ATOMIC_CRYPTO_TRANSFER_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_HRC_FACADE_ASSOCIATE_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_PRECOMPILE_HTS_UNSUPPORTED_CUSTOM_FEE_RECEIVER_DEBITS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_REDIRECT_TOKEN_CALLS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_REFERENCE_SLOT_LIFETIME;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_SIDECARS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_SIDECAR_VALIDATION_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_STORAGE_SLOT_PRICE_TIERS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_THROTTLE_THROTTLE_BY_GAS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CONTRACTS_WITH_SPECIAL_HAPI_SIGS_ACCESS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.CRYPTO_CREATE_WITH_ALIAS_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.DEV_DEFAULT_LISTENING_NODE_ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.DEV_ONLY_DEFAULT_NODE_LISTENS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ENTITIES_LIMIT_TOKEN_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ENTITIES_MAX_LIFETIME;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ENTITIES_SYSTEM_DELETABLE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.EXPIRY_MIN_CYCLE_ENTRY_CAPACITY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.EXPIRY_THROTTLE_RESOURCE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FEES_MIN_CONGESTION_PERIOD;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FEES_PERCENT_CONGESTION_MULTIPLIERS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FEES_PERCENT_UTILIZATION_SCALE_FACTORS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FILES_ADDRESS_BOOK;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FILES_EXCHANGE_RATES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FILES_FEE_SCHEDULES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FILES_HAPI_PERMISSIONS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FILES_MAX_NUM;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FILES_MAX_SIZE_KB;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FILES_NETWORK_PROPERTIES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FILES_NODE_DETAILS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FILES_SOFTWARE_UPDATE_RANGE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.FILES_THROTTLE_DEFINITIONS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.GRPC_PORT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.GRPC_TLS_PORT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.GRPC_WORKFLOWS_PORT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.GRPC_WORKFLOWS_TLS_PORT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_ACCOUNTS_EXPORT_PATH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_ALLOWANCES_IS_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_ALLOWANCES_MAX_TXN_LIMIT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_EXPORT_ACCOUNTS_ON_STARTUP;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_FIRST_USER_ENTITY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_PREFETCH_CODE_CACHE_TTL_SECS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_PREFETCH_QUEUE_CAPACITY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_PREFETCH_THREAD_POOL_SIZE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_PROFILES_ACTIVE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_REALM;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_COMPRESS_FILES_ON_CREATION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_IS_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_LOG_DIR;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_LOG_EVERY_TRANSACTION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_LOG_PERIOD;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_QUEUE_CAPACITY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_RECORD_FILE_VERSION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_SIDE_CAR_DIR;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_RECORD_STREAM_SIG_FILE_VERSION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_SHARD;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_TXN_EIP2930_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_TXN_MAX_MEMO_UTF8_BYTES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_TXN_MAX_VALID_DURATION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.HEDERA_TXN_MIN_VALID_DURATION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ISS_RESET_PERIOD;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.ISS_ROUNDS_TO_LOG;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LAZY_CREATION_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_CHANGE_HIST_MEM_SECS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_FUNDING_ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_ID;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_MAX_AUTO_ASSOCIATIONS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_NFT_TRANSFERS_MAX_LEN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_NUM_SYSTEM_ACCOUNTS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TOKEN_TRANSFERS_MAX_LEN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TOTAL_TINY_BAR_FLOAT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_TRANSFERS_MAX_LEN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.LEDGER_XFER_BAL_CHANGES_MAX_LEN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.NETTY_MODE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.NETTY_PROD_FLOW_CONTROL_WINDOW;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.NETTY_PROD_KEEP_ALIVE_TIME;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.NETTY_PROD_KEEP_ALIVE_TIMEOUT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.NETTY_PROD_MAX_CONCURRENT_CALLS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.NETTY_PROD_MAX_CONNECTION_AGE_GRACE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.NETTY_PROD_MAX_CONNECTION_IDLE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.NETTY_START_RETRIES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.NETTY_START_RETRY_INTERVAL_MS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.NETTY_TLS_CERT_PATH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.NETTY_TLS_KEY_PATH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.QUERIES_BLOB_LOOK_UP_RETRIES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.RATES_MIDNIGHT_CHECK_INTERVAL;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.RECORDS_USE_CONSOLIDATED_FCQ;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.SCHEDULING_LONG_TERM_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.SCHEDULING_MAX_EXPIRATION_FUTURE_SECS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.SCHEDULING_MAX_NUM;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.SCHEDULING_MAX_TXN_PER_SEC;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.SCHEDULING_WHITE_LIST;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.SIGS_EXPAND_FROM_IMMUTABLE_STATE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_FEES_NODE_REWARD_PERCENT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_FEES_STAKING_REWARD_PERCENT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_IS_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_MAX_STAKE_REWARDED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_NODE_MAX_TO_MIN_STAKE_RATIOS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_PERIOD_MINS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_PER_HBAR_REWARD_RATE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_REQUIRE_MIN_STAKE_TO_REWARD;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_REWARD_BALANCE_THRESHOLD;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_REWARD_HISTORY_NUM_STORED_PERIODS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_STARTUP_HELPER_RECOMPUTE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_START_THRESH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STAKING_SUM_OF_CONSENSUS_WEIGHTS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STATS_CONS_THROTTLES_TO_SAMPLE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STATS_EXECUTION_TIMES_TO_TRACK;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STATS_HAPI_THROTTLES_TO_SAMPLE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STATS_RUNNING_AVG_HALF_LIFE_SECS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STATS_SPEEDOMETER_HALF_LIFE_SECS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_AUTO_CREATIONS_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_AGGREGATE_RELS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_CUSTOM_FEES_ALLOWED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_CUSTOM_FEE_DEPTH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_NUM;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_PER_ACCOUNT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_RELS_PER_INFO_QUERY;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_SYMBOL_UTF8_BYTES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_MAX_TOKEN_NAME_UTF8_BYTES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_ARE_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MAX_ALLOWED_MINTS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_BURN;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_MINT;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MAX_BATCH_SIZE_WIPE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MAX_METADATA_BYTES;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MAX_QUERY_RANGE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_USE_TREASURY_WILD_CARDS;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_NFTS_USE_VIRTUAL_MERKLE;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOKENS_STORE_RELS_ON_DISK;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TOPICS_MAX_NUM;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TRACEABILITY_MAX_EXPORTS_PER_CONS_SEC;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.TRACEABILITY_MIN_FREE_TO_USED_GAS_THROTTLE_RATIO;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.UPGRADE_ARTIFACTS_PATH;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.UTIL_PRNG_IS_ENABLED;
import static com.hedera.node.app.service.mono.context.properties.PropertyNames.WORKFLOWS_ENABLED;
import static java.util.Collections.unmodifiableSet;
import static java.util.Map.entry;
import static java.util.stream.Collectors.toSet;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public final class BootstrapProperties implements PropertySource {

    private static final Map<String, Object> MISSING_PROPS = null;

    private static final Function<String, InputStream> nullableResourceStreamProvider =
            BootstrapProperties.class.getClassLoader()::getResourceAsStream;

    private static final Logger log = LogManager.getLogger(BootstrapProperties.class);

    static ThrowingStreamProvider resourceStreamProvider = resource -> {
        var in = nullableResourceStreamProvider.apply(resource);
        if (in == null) {
            throw new IOException(String.format("Resource '%s' cannot be loaded.", resource));
        }
        return in;
    };

    private static final ThrowingStreamProvider fileStreamProvider = loc -> Files.newInputStream(Paths.get(loc));

    private final boolean logEnabled;

    private final Properties rawProperties = new Properties();

    @Inject
    public BootstrapProperties() {
        this(true);
    }

    public BootstrapProperties(final boolean logEnabled) {
        this.logEnabled = logEnabled;
    }

    String bootstrapPropsResource = "bootstrap.properties";
    String bootstrapOverridePropsLoc = "data/config/bootstrap.properties";

    Map<String, Object> bootstrapProps = MISSING_PROPS;

    private void initPropsFromResource() throws IllegalStateException {
        rawProperties.clear();
        load(bootstrapPropsResource, rawProperties);
        loadOverride(bootstrapOverridePropsLoc, rawProperties, fileStreamProvider, log);
        checkForUnrecognizedProps(rawProperties);
        checkForMissingProps(rawProperties);
        resolveBootstrapProps(rawProperties);
    }

    private void checkForUnrecognizedProps(final Properties resourceProps) throws IllegalStateException {
        final Set<String> unrecognizedProps = new HashSet<>(resourceProps.stringPropertyNames());
        unrecognizedProps.removeAll(BOOTSTRAP_PROP_NAMES);
        if (!unrecognizedProps.isEmpty()) {
            final var msg = String.format(
                    "'%s' contains unrecognized properties: %s!", bootstrapPropsResource, unrecognizedProps);
            throw new IllegalStateException(msg);
        }
    }

    private void checkForMissingProps(final Properties resourceProps) throws IllegalStateException {
        final var missingProps = BOOTSTRAP_PROP_NAMES.stream()
                .filter(name -> !resourceProps.containsKey(name))
                .sorted()
                .toList();
        if (!missingProps.isEmpty()) {
            final var msg = String.format("'%s' is missing properties: %s!", bootstrapPropsResource, missingProps);
            throw new IllegalStateException(msg);
        }
    }

    private void resolveBootstrapProps(final Properties resourceProps) {
        bootstrapProps = new HashMap<>();
        BOOTSTRAP_PROP_NAMES.forEach(
                prop -> bootstrapProps.put(prop, transformFor(prop).apply(resourceProps.getProperty(prop))));

        if (logEnabled) {
            final var msg = "Resolved bootstrap properties:\n  "
                    + BOOTSTRAP_PROP_NAMES.stream()
                            .sorted()
                            .map(name -> String.format("%s=%s", name, bootstrapProps.get(name)))
                            .collect(Collectors.joining("\n  "));
            log.info(msg);
        }
    }

    private void load(final String resource, final Properties intoProps) throws IllegalStateException {
        try (final var fin = resourceStreamProvider.newInputStream(resource)) {
            intoProps.load(fin);
        } catch (final IOException e) {
            throw new IllegalStateException(String.format("'%s' could not be loaded!", resource), e);
        }
    }

    public void ensureProps() throws IllegalStateException {
        if (bootstrapProps == MISSING_PROPS) {
            initPropsFromResource();
        }
    }

    @Override
    public boolean containsProperty(final String name) {
        return BOOTSTRAP_PROP_NAMES.contains(name);
    }

    @Override
    public Object getProperty(final String name) {
        ensureProps();
        if (bootstrapProps.containsKey(name)) {
            return bootstrapProps.get(name);
        } else {
            throw new IllegalArgumentException(String.format("Argument 'name=%s' is invalid!", name));
        }
    }

    @Override
    public Set<String> allPropertyNames() {
        return BOOTSTRAP_PROP_NAMES;
    }

    @Override
    public String getRawValue(final String name) {
        ensureProps();
        if (rawProperties.contains(name)) {
            return rawProperties.getProperty(name);
        }
        throw new NoSuchElementException("Property of name '" + name + "' can not be found!");
    }

    private static final Set<String> BOOTSTRAP_PROPS = Set.of(
            BOOTSTRAP_FEE_SCHEDULE_JSON_RESOURCE, // possibly node property
            BOOTSTRAP_GENESIS_PUBLIC_KEY,
            BOOTSTRAP_HAPI_PERMISSIONS_PATH, // possibly node property
            BOOTSTRAP_NETWORK_PROPERTIES_PATH, // possibly node property
            BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV,
            BOOTSTRAP_RATES_CURRENT_CENT_EQUIV,
            BOOTSTRAP_RATES_CURRENT_EXPIRY,
            BOOTSTRAP_RATES_NEXT_HBAR_EQUIV,
            BOOTSTRAP_RATES_NEXT_CENT_EQUIV,
            BOOTSTRAP_RATES_NEXT_EXPIRY,
            BOOTSTRAP_SYSTEM_ENTITY_EXPIRY,
            BOOTSTRAP_THROTTLE_DEF_JSON_RESOURCE);

    private static final Set<String> GLOBAL_STATIC_PROPS = Set.of(
            ACCOUNTS_ADDRESS_BOOK_ADMIN,
            ACCOUNTS_EXCHANGE_RATES_ADMIN,
            ACCOUNTS_FEE_SCHEDULE_ADMIN,
            ACCOUNTS_FREEZE_ADMIN,
            ACCOUNTS_LAST_THROTTLE_EXEMPT,
            ACCOUNTS_NODE_REWARD_ACCOUNT,
            ACCOUNTS_STAKING_REWARD_ACCOUNT,
            ACCOUNTS_SYSTEM_ADMIN,
            ACCOUNTS_SYSTEM_DELETE_ADMIN,
            ACCOUNTS_SYSTEM_UNDELETE_ADMIN,
            ACCOUNTS_TREASURY,
            ACCOUNTS_STORE_ON_DISK,
            AUTO_RENEW_GRANT_FREE_RENEWALS,
            ENTITIES_MAX_LIFETIME,
            ENTITIES_SYSTEM_DELETABLE,
            FILES_ADDRESS_BOOK,
            FILES_NETWORK_PROPERTIES,
            FILES_EXCHANGE_RATES,
            FILES_FEE_SCHEDULES,
            FILES_HAPI_PERMISSIONS,
            FILES_NODE_DETAILS,
            FILES_SOFTWARE_UPDATE_RANGE,
            FILES_THROTTLE_DEFINITIONS,
            HEDERA_FIRST_USER_ENTITY,
            HEDERA_REALM,
            HEDERA_SHARD,
            LEDGER_NUM_SYSTEM_ACCOUNTS,
            LEDGER_TOTAL_TINY_BAR_FLOAT,
            LEDGER_ID,
            STAKING_PERIOD_MINS,
            STAKING_REWARD_HISTORY_NUM_STORED_PERIODS,
            STAKING_STARTUP_HELPER_RECOMPUTE,
            WORKFLOWS_ENABLED,
            STAKING_SUM_OF_CONSENSUS_WEIGHTS,
            CONFIG_VERSION,
            RECORDS_USE_CONSOLIDATED_FCQ);

    static final Set<String> GLOBAL_DYNAMIC_PROPS = Set.of(
            ACCOUNTS_MAX_NUM,
            AUTO_CREATION_ENABLED,
            LAZY_CREATION_ENABLED,
            CRYPTO_CREATE_WITH_ALIAS_ENABLED,
            BALANCES_EXPORT_DIR_PATH, // possibly node property
            BALANCES_EXPORT_ENABLED,
            BALANCES_EXPORT_PERIOD_SECS,
            BALANCES_EXPORT_TOKEN_BALANCES,
            BALANCES_NODE_BALANCE_WARN_THRESHOLD,
            BALANCES_COMPRESS_ON_CREATION,
            CACHE_RECORDS_TTL,
            CONTRACTS_DEFAULT_LIFETIME,
            CONTRACTS_PERMITTED_DELEGATE_CALLERS,
            CONTRACTS_KEYS_LEGACY_ACTIVATIONS,
            CONTRACTS_ENFORCE_CREATION_THROTTLE,
            CONTRACTS_KNOWN_BLOCK_HASH,
            CONTRACTS_LOCAL_CALL_EST_RET_BYTES,
            CONTRACTS_ALLOW_CREATE2,
            CONTRACTS_ALLOW_AUTO_ASSOCIATIONS,
            CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS,
            CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS,
            CONTRACTS_WITH_SPECIAL_HAPI_SIGS_ACCESS,
            CONTRACTS_MAX_GAS_PER_SEC,
            CONTRACTS_MAX_KV_PAIRS_AGGREGATE,
            CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL,
            CONTRACTS_MAX_NUM,
            CONTRACTS_CHAIN_ID,
            CONTRACTS_SIDECARS,
            CONTRACTS_SIDECAR_VALIDATION_ENABLED,
            CONTRACTS_STORAGE_SLOT_PRICE_TIERS,
            CONTRACTS_REFERENCE_SLOT_LIFETIME,
            CONTRACTS_ITEMIZE_STORAGE_FEES,
            CONTRACTS_FREE_STORAGE_TIER_LIMIT,
            CONTRACTS_THROTTLE_THROTTLE_BY_GAS,
            CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT,
            CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT,
            CONTRACTS_REDIRECT_TOKEN_CALLS,
            CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST,
            CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST,
            CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS,
            CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE,
            CONTRACTS_PRECOMPILE_HTS_UNSUPPORTED_CUSTOM_FEE_RECEIVER_DEBITS,
            CONTRACTS_PRECOMPILE_ATOMIC_CRYPTO_TRANSFER_ENABLED,
            CONTRACTS_PRECOMPILE_HRC_FACADE_ASSOCIATE_ENABLED,
            CONTRACTS_NONCES_EXTERNALIZATION_ENABLED,
            CONTRACTS_EVM_VERSION,
            CONTRACTS_DYNAMIC_EVM_VERSION,
            EXPIRY_MIN_CYCLE_ENTRY_CAPACITY,
            EXPIRY_THROTTLE_RESOURCE,
            FILES_MAX_NUM,
            FILES_MAX_SIZE_KB,
            HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB,
            FEES_MIN_CONGESTION_PERIOD,
            FEES_PERCENT_CONGESTION_MULTIPLIERS,
            FEES_PERCENT_UTILIZATION_SCALE_FACTORS,
            FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER,
            TRACEABILITY_MIN_FREE_TO_USED_GAS_THROTTLE_RATIO,
            TRACEABILITY_MAX_EXPORTS_PER_CONS_SEC,
            HEDERA_TXN_MAX_MEMO_UTF8_BYTES,
            HEDERA_TXN_MAX_VALID_DURATION,
            HEDERA_TXN_MIN_VALID_DURATION,
            HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS,
            HEDERA_TXN_EIP2930_ENABLED,
            HEDERA_RECORD_STREAM_RECORD_FILE_VERSION,
            HEDERA_RECORD_STREAM_SIG_FILE_VERSION,
            HEDERA_RECORD_STREAM_LOG_EVERY_TRANSACTION,
            HEDERA_RECORD_STREAM_COMPRESS_FILES_ON_CREATION,
            AUTO_RENEW_TARGET_TYPES,
            AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN,
            AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE,
            AUTO_RENEW_GRACE_PERIOD,
            LEDGER_CHANGE_HIST_MEM_SECS,
            LEDGER_MAX_AUTO_ASSOCIATIONS,
            LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION,
            LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION,
            LEDGER_XFER_BAL_CHANGES_MAX_LEN,
            LEDGER_FUNDING_ACCOUNT,
            LEDGER_TRANSFERS_MAX_LEN,
            LEDGER_TOKEN_TRANSFERS_MAX_LEN,
            LEDGER_NFT_TRANSFERS_MAX_LEN,
            LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT,
            LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS,
            RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT,
            RATES_MIDNIGHT_CHECK_INTERVAL,
            SCHEDULING_LONG_TERM_ENABLED,
            SCHEDULING_MAX_TXN_PER_SEC,
            SCHEDULING_MAX_NUM,
            SCHEDULING_MAX_EXPIRATION_FUTURE_SECS,
            SCHEDULING_WHITE_LIST,
            SIGS_EXPAND_FROM_IMMUTABLE_STATE,
            STAKING_FEES_NODE_REWARD_PERCENT,
            STAKING_FEES_STAKING_REWARD_PERCENT,
            STAKING_NODE_MAX_TO_MIN_STAKE_RATIOS,
            STAKING_IS_ENABLED,
            STAKING_REQUIRE_MIN_STAKE_TO_REWARD,
            STAKING_PER_HBAR_REWARD_RATE,
            STAKING_START_THRESH,
            STAKING_MAX_STAKE_REWARDED,
            STAKING_REWARD_BALANCE_THRESHOLD,
            TOKENS_MAX_AGGREGATE_RELS,
            TOKENS_STORE_RELS_ON_DISK,
            TOKENS_MAX_NUM,
            TOKENS_MAX_RELS_PER_INFO_QUERY,
            TOKENS_MAX_PER_ACCOUNT,
            TOKENS_MAX_SYMBOL_UTF8_BYTES,
            TOKENS_MAX_TOKEN_NAME_UTF8_BYTES,
            TOKENS_MAX_CUSTOM_FEES_ALLOWED,
            TOKENS_MAX_CUSTOM_FEE_DEPTH,
            TOKENS_NFTS_ARE_ENABLED,
            TOKENS_NFTS_MAX_METADATA_BYTES,
            TOKENS_NFTS_MAX_BATCH_SIZE_BURN,
            TOKENS_NFTS_MAX_BATCH_SIZE_WIPE,
            TOKENS_NFTS_MAX_BATCH_SIZE_MINT,
            TOKENS_NFTS_MAX_ALLOWED_MINTS,
            TOKENS_NFTS_MAX_QUERY_RANGE,
            TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR,
            TOKENS_NFTS_USE_VIRTUAL_MERKLE,
            TOPICS_MAX_NUM,
            TOKENS_NFTS_USE_TREASURY_WILD_CARDS,
            CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED,
            CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS,
            CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS,
            UPGRADE_ARTIFACTS_PATH, // possibly node property
            HEDERA_ALLOWANCES_MAX_TXN_LIMIT,
            HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT,
            HEDERA_ALLOWANCES_IS_ENABLED,
            ENTITIES_LIMIT_TOKEN_ASSOCIATIONS,
            UTIL_PRNG_IS_ENABLED,
            TOKENS_AUTO_CREATIONS_ENABLED,
            ACCOUNTS_BLOCKLIST_ENABLED,
            ACCOUNTS_BLOCKLIST_PATH,
            CACHE_CRYPTO_TRANSFER_WARM_THREADS);

    static final Set<String> NODE_PROPS = Set.of(
            DEV_ONLY_DEFAULT_NODE_LISTENS,
            DEV_DEFAULT_LISTENING_NODE_ACCOUNT,
            GRPC_PORT,
            GRPC_TLS_PORT,
            GRPC_WORKFLOWS_PORT,
            GRPC_WORKFLOWS_TLS_PORT,
            HEDERA_ACCOUNTS_EXPORT_PATH,
            HEDERA_EXPORT_ACCOUNTS_ON_STARTUP, // possibly network property
            HEDERA_PREFETCH_QUEUE_CAPACITY,
            HEDERA_PREFETCH_THREAD_POOL_SIZE,
            HEDERA_PREFETCH_CODE_CACHE_TTL_SECS,
            HEDERA_PROFILES_ACTIVE,
            HEDERA_RECORD_STREAM_IS_ENABLED, // possibly network property
            HEDERA_RECORD_STREAM_LOG_DIR,
            HEDERA_RECORD_STREAM_SIDE_CAR_DIR,
            HEDERA_RECORD_STREAM_LOG_PERIOD,
            HEDERA_RECORD_STREAM_QUEUE_CAPACITY,
            ISS_RESET_PERIOD, // possibly network property
            ISS_ROUNDS_TO_LOG,
            NETTY_MODE,
            NETTY_PROD_FLOW_CONTROL_WINDOW,
            NETTY_PROD_MAX_CONCURRENT_CALLS,
            NETTY_PROD_MAX_CONNECTION_AGE,
            NETTY_PROD_MAX_CONNECTION_AGE_GRACE,
            NETTY_PROD_MAX_CONNECTION_IDLE,
            NETTY_PROD_KEEP_ALIVE_TIME,
            NETTY_PROD_KEEP_ALIVE_TIMEOUT,
            NETTY_START_RETRIES,
            NETTY_START_RETRY_INTERVAL_MS,
            NETTY_TLS_CERT_PATH,
            NETTY_TLS_KEY_PATH,
            QUERIES_BLOB_LOOK_UP_RETRIES, // possibly network property
            STATS_CONS_THROTTLES_TO_SAMPLE,
            STATS_HAPI_THROTTLES_TO_SAMPLE,
            STATS_EXECUTION_TIMES_TO_TRACK,
            STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS,
            STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS,
            STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS,
            STATS_RUNNING_AVG_HALF_LIFE_SECS,
            STATS_SPEEDOMETER_HALF_LIFE_SECS);

    public static final Set<String> BOOTSTRAP_PROP_NAMES =
            unmodifiableSet(Stream.of(BOOTSTRAP_PROPS, GLOBAL_STATIC_PROPS, GLOBAL_DYNAMIC_PROPS, NODE_PROPS)
                    .flatMap(Set::stream)
                    .collect(toSet()));

    public static Function<String, Object> transformFor(final String prop) {
        return PROP_TRANSFORMS.getOrDefault(prop, AS_STRING);
    }

    private static final Map<String, Function<String, Object>> PROP_TRANSFORMS = Map.ofEntries(
            entry(ACCOUNTS_ADDRESS_BOOK_ADMIN, AS_LONG),
            entry(ACCOUNTS_EXCHANGE_RATES_ADMIN, AS_LONG),
            entry(ACCOUNTS_FEE_SCHEDULE_ADMIN, AS_LONG),
            entry(ACCOUNTS_FREEZE_ADMIN, AS_LONG),
            entry(ACCOUNTS_LAST_THROTTLE_EXEMPT, AS_LONG),
            entry(ACCOUNTS_MAX_NUM, AS_LONG),
            entry(ACCOUNTS_NODE_REWARD_ACCOUNT, AS_LONG),
            entry(ACCOUNTS_STAKING_REWARD_ACCOUNT, AS_LONG),
            entry(ACCOUNTS_SYSTEM_ADMIN, AS_LONG),
            entry(ACCOUNTS_SYSTEM_DELETE_ADMIN, AS_LONG),
            entry(ACCOUNTS_SYSTEM_UNDELETE_ADMIN, AS_LONG),
            entry(ACCOUNTS_TREASURY, AS_LONG),
            entry(ACCOUNTS_STORE_ON_DISK, AS_BOOLEAN),
            entry(BALANCES_EXPORT_ENABLED, AS_BOOLEAN),
            entry(BALANCES_EXPORT_PERIOD_SECS, AS_INT),
            entry(BALANCES_NODE_BALANCE_WARN_THRESHOLD, AS_LONG),
            entry(BALANCES_COMPRESS_ON_CREATION, AS_BOOLEAN),
            entry(CACHE_RECORDS_TTL, AS_INT),
            entry(DEV_ONLY_DEFAULT_NODE_LISTENS, AS_BOOLEAN),
            entry(BALANCES_EXPORT_TOKEN_BALANCES, AS_BOOLEAN),
            entry(ENTITIES_MAX_LIFETIME, AS_LONG),
            entry(ENTITIES_SYSTEM_DELETABLE, AS_ENTITY_TYPES),
            entry(FILES_ADDRESS_BOOK, AS_LONG),
            entry(EXPIRY_MIN_CYCLE_ENTRY_CAPACITY, AS_ACCESS_LIST),
            entry(FILES_MAX_NUM, AS_LONG),
            entry(FILES_MAX_SIZE_KB, AS_INT),
            entry(FILES_NETWORK_PROPERTIES, AS_LONG),
            entry(FILES_EXCHANGE_RATES, AS_LONG),
            entry(FILES_FEE_SCHEDULES, AS_LONG),
            entry(FILES_HAPI_PERMISSIONS, AS_LONG),
            entry(FILES_NODE_DETAILS, AS_LONG),
            entry(FILES_SOFTWARE_UPDATE_RANGE, AS_ENTITY_NUM_RANGE),
            entry(FILES_THROTTLE_DEFINITIONS, AS_LONG),
            entry(GRPC_PORT, AS_INT),
            entry(GRPC_TLS_PORT, AS_INT),
            entry(GRPC_WORKFLOWS_PORT, AS_INT),
            entry(GRPC_WORKFLOWS_TLS_PORT, AS_INT),
            entry(HEDERA_EXPORT_ACCOUNTS_ON_STARTUP, AS_BOOLEAN),
            entry(HEDERA_FIRST_USER_ENTITY, AS_LONG),
            entry(HEDERA_PREFETCH_QUEUE_CAPACITY, AS_INT),
            entry(HEDERA_PREFETCH_THREAD_POOL_SIZE, AS_INT),
            entry(HEDERA_PREFETCH_CODE_CACHE_TTL_SECS, AS_INT),
            entry(HEDERA_PROFILES_ACTIVE, AS_PROFILE),
            entry(HEDERA_REALM, AS_LONG),
            entry(HEDERA_RECORD_STREAM_LOG_PERIOD, AS_LONG),
            entry(HEDERA_RECORD_STREAM_IS_ENABLED, AS_BOOLEAN),
            entry(HEDERA_RECORD_STREAM_RECORD_FILE_VERSION, AS_INT),
            entry(HEDERA_RECORD_STREAM_SIG_FILE_VERSION, AS_INT),
            entry(HEDERA_RECORD_STREAM_QUEUE_CAPACITY, AS_INT),
            entry(HEDERA_RECORD_STREAM_SIDECAR_MAX_SIZE_MB, AS_INT),
            entry(TRACEABILITY_MIN_FREE_TO_USED_GAS_THROTTLE_RATIO, AS_LONG),
            entry(TRACEABILITY_MAX_EXPORTS_PER_CONS_SEC, AS_LONG),
            entry(HEDERA_RECORD_STREAM_LOG_EVERY_TRANSACTION, AS_BOOLEAN),
            entry(HEDERA_RECORD_STREAM_COMPRESS_FILES_ON_CREATION, AS_BOOLEAN),
            entry(HEDERA_SHARD, AS_LONG),
            entry(HEDERA_TXN_MAX_MEMO_UTF8_BYTES, AS_INT),
            entry(HEDERA_TXN_MAX_VALID_DURATION, AS_LONG),
            entry(HEDERA_TXN_MIN_VALID_DURATION, AS_LONG),
            entry(HEDERA_TXN_MIN_VALIDITY_BUFFER_SECS, AS_INT),
            entry(HEDERA_TXN_EIP2930_ENABLED, AS_BOOLEAN),
            entry(CONTRACTS_PRECOMPILE_HTS_UNSUPPORTED_CUSTOM_FEE_RECEIVER_DEBITS, AS_CUSTOM_FEES_TYPE),
            entry(AUTO_CREATION_ENABLED, AS_BOOLEAN),
            entry(LAZY_CREATION_ENABLED, AS_BOOLEAN),
            entry(CRYPTO_CREATE_WITH_ALIAS_ENABLED, AS_BOOLEAN),
            entry(AUTO_RENEW_TARGET_TYPES, AS_ENTITY_TYPES),
            entry(AUTO_RENEW_NUM_OF_ENTITIES_TO_SCAN, AS_INT),
            entry(AUTO_RENEW_MAX_NUM_OF_ENTITIES_TO_RENEW_OR_DELETE, AS_INT),
            entry(AUTO_RENEW_GRACE_PERIOD, AS_LONG),
            entry(AUTO_RENEW_GRANT_FREE_RENEWALS, AS_BOOLEAN),
            entry(LEDGER_AUTO_RENEW_PERIOD_MAX_DURATION, AS_LONG),
            entry(LEDGER_AUTO_RENEW_PERIOD_MIN_DURATION, AS_LONG),
            entry(NETTY_MODE, AS_PROFILE),
            entry(QUERIES_BLOB_LOOK_UP_RETRIES, AS_INT),
            entry(NETTY_START_RETRIES, AS_INT),
            entry(NETTY_START_RETRY_INTERVAL_MS, AS_LONG),
            entry(BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV, AS_INT),
            entry(BOOTSTRAP_RATES_CURRENT_CENT_EQUIV, AS_INT),
            entry(BOOTSTRAP_RATES_CURRENT_EXPIRY, AS_LONG),
            entry(BOOTSTRAP_RATES_NEXT_HBAR_EQUIV, AS_INT),
            entry(BOOTSTRAP_RATES_NEXT_CENT_EQUIV, AS_INT),
            entry(BOOTSTRAP_RATES_NEXT_EXPIRY, AS_LONG),
            entry(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY, AS_LONG),
            entry(FEES_MIN_CONGESTION_PERIOD, AS_INT),
            entry(FEES_TOKEN_TRANSFER_USAGE_MULTIPLIER, AS_INT),
            entry(FEES_PERCENT_CONGESTION_MULTIPLIERS, AS_CONGESTION_MULTIPLIERS),
            entry(FEES_PERCENT_UTILIZATION_SCALE_FACTORS, AS_ENTITY_SCALE_FACTORS),
            entry(LEDGER_CHANGE_HIST_MEM_SECS, AS_INT),
            entry(LEDGER_MAX_AUTO_ASSOCIATIONS, AS_INT),
            entry(LEDGER_XFER_BAL_CHANGES_MAX_LEN, AS_INT),
            entry(LEDGER_FUNDING_ACCOUNT, AS_LONG),
            entry(LEDGER_NUM_SYSTEM_ACCOUNTS, AS_INT),
            entry(LEDGER_TRANSFERS_MAX_LEN, AS_INT),
            entry(LEDGER_TOKEN_TRANSFERS_MAX_LEN, AS_INT),
            entry(LEDGER_NFT_TRANSFERS_MAX_LEN, AS_INT),
            entry(LEDGER_TOTAL_TINY_BAR_FLOAT, AS_LONG),
            entry(LEDGER_SCHEDULE_TX_EXPIRY_TIME_SECS, AS_INT),
            entry(LEDGER_RECORDS_MAX_QUERYABLE_BY_ACCOUNT, AS_INT),
            entry(ISS_RESET_PERIOD, AS_INT),
            entry(ISS_ROUNDS_TO_LOG, AS_INT),
            entry(NETTY_PROD_FLOW_CONTROL_WINDOW, AS_INT),
            entry(NETTY_PROD_MAX_CONCURRENT_CALLS, AS_INT),
            entry(NETTY_PROD_MAX_CONNECTION_AGE, AS_LONG),
            entry(NETTY_PROD_MAX_CONNECTION_AGE_GRACE, AS_LONG),
            entry(NETTY_PROD_MAX_CONNECTION_IDLE, AS_LONG),
            entry(NETTY_PROD_KEEP_ALIVE_TIME, AS_LONG),
            entry(NETTY_PROD_KEEP_ALIVE_TIMEOUT, AS_LONG),
            entry(SCHEDULING_MAX_NUM, AS_LONG),
            entry(STAKING_FEES_NODE_REWARD_PERCENT, AS_INT),
            entry(STAKING_FEES_STAKING_REWARD_PERCENT, AS_INT),
            entry(STAKING_PERIOD_MINS, AS_LONG),
            entry(STAKING_REWARD_HISTORY_NUM_STORED_PERIODS, AS_INT),
            entry(STAKING_STARTUP_HELPER_RECOMPUTE, AS_RECOMPUTE_TYPES),
            entry(STAKING_REQUIRE_MIN_STAKE_TO_REWARD, AS_BOOLEAN),
            entry(STAKING_PER_HBAR_REWARD_RATE, AS_LONG),
            entry(STAKING_START_THRESH, AS_LONG),
            entry(STAKING_SUM_OF_CONSENSUS_WEIGHTS, AS_INT),
            entry(STAKING_MAX_STAKE_REWARDED, AS_LONG),
            entry(STAKING_REWARD_BALANCE_THRESHOLD, AS_LONG),
            entry(TOKENS_MAX_AGGREGATE_RELS, AS_LONG),
            entry(TOKENS_STORE_RELS_ON_DISK, AS_BOOLEAN),
            entry(TOKENS_MAX_NUM, AS_LONG),
            entry(TOKENS_MAX_PER_ACCOUNT, AS_INT),
            entry(TOKENS_MAX_RELS_PER_INFO_QUERY, AS_INT),
            entry(TOKENS_MAX_CUSTOM_FEES_ALLOWED, AS_INT),
            entry(TOKENS_MAX_CUSTOM_FEE_DEPTH, AS_INT),
            entry(TOKENS_MAX_SYMBOL_UTF8_BYTES, AS_INT),
            entry(TOKENS_MAX_TOKEN_NAME_UTF8_BYTES, AS_INT),
            entry(TOKENS_NFTS_MAX_METADATA_BYTES, AS_INT),
            entry(TOKENS_NFTS_MAX_BATCH_SIZE_BURN, AS_INT),
            entry(TOKENS_NFTS_MINT_THORTTLE_SCALE_FACTOR, AS_THROTTLE_SCALE_FACTOR),
            entry(TOKENS_NFTS_MAX_BATCH_SIZE_WIPE, AS_INT),
            entry(TOKENS_NFTS_MAX_BATCH_SIZE_MINT, AS_INT),
            entry(TOKENS_NFTS_MAX_ALLOWED_MINTS, AS_LONG),
            entry(TOKENS_NFTS_MAX_QUERY_RANGE, AS_LONG),
            entry(TOKENS_NFTS_USE_TREASURY_WILD_CARDS, AS_BOOLEAN),
            entry(TOKENS_NFTS_USE_VIRTUAL_MERKLE, AS_BOOLEAN),
            entry(TOPICS_MAX_NUM, AS_LONG),
            entry(CONTRACTS_MAX_NUM, AS_LONG),
            entry(CONTRACTS_PERMITTED_DELEGATE_CALLERS, AS_EVM_ADDRESSES),
            entry(CONTRACTS_KEYS_LEGACY_ACTIVATIONS, AS_LEGACY_ACTIVATIONS),
            entry(CONTRACTS_KNOWN_BLOCK_HASH, AS_KNOWN_BLOCK_VALUES),
            entry(CONTRACTS_LOCAL_CALL_EST_RET_BYTES, AS_INT),
            entry(CONTRACTS_ALLOW_CREATE2, AS_BOOLEAN),
            entry(CONTRACTS_ALLOW_AUTO_ASSOCIATIONS, AS_BOOLEAN),
            entry(CONTRACTS_ALLOW_SYSTEM_USE_OF_HAPI_SIGS, AS_FUNCTIONS),
            entry(CONTRACTS_MAX_NUM_WITH_HAPI_SIGS_ACCESS, AS_LONG),
            entry(CONTRACTS_WITH_SPECIAL_HAPI_SIGS_ACCESS, AS_EVM_ADDRESSES),
            entry(CONTRACTS_DEFAULT_LIFETIME, AS_LONG),
            entry(CONTRACTS_MAX_GAS_PER_SEC, AS_LONG),
            entry(CONTRACTS_ITEMIZE_STORAGE_FEES, AS_BOOLEAN),
            entry(CONTRACTS_ENFORCE_CREATION_THROTTLE, AS_BOOLEAN),
            entry(CONTRACTS_REFERENCE_SLOT_LIFETIME, AS_LONG),
            entry(CONTRACTS_FREE_STORAGE_TIER_LIMIT, AS_INT),
            entry(CONTRACTS_MAX_KV_PAIRS_AGGREGATE, AS_LONG),
            entry(CONTRACTS_MAX_KV_PAIRS_INDIVIDUAL, AS_INT),
            entry(CONTRACTS_CHAIN_ID, AS_INT),
            entry(CONTRACTS_SIDECARS, AS_SIDECARS),
            entry(CONTRACTS_SIDECAR_VALIDATION_ENABLED, AS_BOOLEAN),
            entry(CONTRACTS_MAX_REFUND_PERCENT_OF_GAS_LIMIT, AS_INT),
            entry(CONTRACTS_SCHEDULE_THROTTLE_MAX_GAS_LIMIT, AS_LONG),
            entry(CONTRACTS_REDIRECT_TOKEN_CALLS, AS_BOOLEAN),
            entry(CONTRACTS_PRECOMPILE_EXCHANGE_RATE_GAS_COST, AS_LONG),
            entry(CONTRACTS_PRECOMPILE_HTS_DEFAULT_GAS_COST, AS_LONG),
            entry(CONTRACTS_PRECOMPILE_EXPORT_RECORD_RESULTS, AS_BOOLEAN),
            entry(CONTRACTS_PRECOMPILE_HTS_ENABLE_TOKEN_CREATE, AS_BOOLEAN),
            entry(CONTRACTS_PRECOMPILE_ATOMIC_CRYPTO_TRANSFER_ENABLED, AS_BOOLEAN),
            entry(CONTRACTS_PRECOMPILE_HRC_FACADE_ASSOCIATE_ENABLED, AS_BOOLEAN),
            entry(CONTRACTS_NONCES_EXTERNALIZATION_ENABLED, AS_BOOLEAN),
            entry(CONTRACTS_THROTTLE_THROTTLE_BY_GAS, AS_BOOLEAN),
            entry(CONTRACTS_EVM_VERSION, AS_STRING),
            entry(CONTRACTS_DYNAMIC_EVM_VERSION, AS_BOOLEAN),
            entry(RATES_INTRA_DAY_CHANGE_LIMIT_PERCENT, AS_INT),
            entry(RATES_MIDNIGHT_CHECK_INTERVAL, AS_LONG),
            entry(RECORDS_USE_CONSOLIDATED_FCQ, AS_BOOLEAN),
            entry(SIGS_EXPAND_FROM_IMMUTABLE_STATE, AS_BOOLEAN),
            entry(SCHEDULING_LONG_TERM_ENABLED, AS_BOOLEAN),
            entry(SCHEDULING_MAX_TXN_PER_SEC, AS_LONG),
            entry(SCHEDULING_MAX_EXPIRATION_FUTURE_SECS, AS_LONG),
            entry(SCHEDULING_WHITE_LIST, AS_FUNCTIONS),
            entry(STAKING_NODE_MAX_TO_MIN_STAKE_RATIOS, AS_NODE_STAKE_RATIOS),
            entry(STAKING_IS_ENABLED, AS_BOOLEAN),
            entry(STATS_ENTITY_UTILS_GAUGE_UPDATE_INTERVAL_MS, AS_LONG),
            entry(STATS_HAPI_OPS_SPEEDOMETER_UPDATE_INTERVAL_MS, AS_LONG),
            entry(STATS_THROTTLE_UTILS_GAUGE_UPDATE_INTERVAL_MS, AS_LONG),
            entry(STATS_RUNNING_AVG_HALF_LIFE_SECS, AS_DOUBLE),
            entry(STATS_SPEEDOMETER_HALF_LIFE_SECS, AS_DOUBLE),
            entry(CONSENSUS_MESSAGE_MAX_BYTES_ALLOWED, AS_INT),
            entry(CONSENSUS_HANDLE_MAX_PRECEDING_RECORDS, AS_LONG),
            entry(CONSENSUS_HANDLE_MAX_FOLLOWING_RECORDS, AS_LONG),
            entry(TOKENS_NFTS_ARE_ENABLED, AS_BOOLEAN),
            entry(STATS_CONS_THROTTLES_TO_SAMPLE, AS_CS_STRINGS),
            entry(STATS_HAPI_THROTTLES_TO_SAMPLE, AS_CS_STRINGS),
            entry(STATS_EXECUTION_TIMES_TO_TRACK, AS_INT),
            entry(HEDERA_ALLOWANCES_MAX_TXN_LIMIT, AS_INT),
            entry(HEDERA_ALLOWANCES_MAX_ACCOUNT_LIMIT, AS_INT),
            entry(HEDERA_ALLOWANCES_IS_ENABLED, AS_BOOLEAN),
            entry(ENTITIES_LIMIT_TOKEN_ASSOCIATIONS, AS_BOOLEAN),
            entry(UTIL_PRNG_IS_ENABLED, AS_BOOLEAN),
            entry(TOKENS_AUTO_CREATIONS_ENABLED, AS_BOOLEAN),
            entry(WORKFLOWS_ENABLED, AS_FUNCTIONS),
            entry(ACCOUNTS_BLOCKLIST_ENABLED, AS_BOOLEAN),
            entry(ACCOUNTS_BLOCKLIST_PATH, AS_STRING),
            entry(CACHE_CRYPTO_TRANSFER_WARM_THREADS, AS_INT),
            entry(CONFIG_VERSION, AS_INT));
}
