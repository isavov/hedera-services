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

package com.hedera.node.app.service.file;

import com.hedera.hapi.node.base.Transaction;
import com.hedera.hapi.node.transaction.Query;
import com.hedera.hapi.node.transaction.Response;
import com.hedera.hapi.node.transaction.TransactionResponse;
import com.hedera.pbj.runtime.RpcMethodDefinition;
import com.hedera.pbj.runtime.RpcServiceDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Transactions and queries for the file service.
 */
@SuppressWarnings("java:S6548")
public final class FileServiceDefinition implements RpcServiceDefinition {
    public static final FileServiceDefinition INSTANCE = new FileServiceDefinition();

    private static final Set<RpcMethodDefinition<?, ?>> methods = Set.of(
            new RpcMethodDefinition<>("createFile", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("updateFile", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("deleteFile", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("appendContent", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("getFileContent", Query.class, Response.class),
            new RpcMethodDefinition<>("getFileInfo", Query.class, Response.class),
            new RpcMethodDefinition<>("systemDelete", Transaction.class, TransactionResponse.class),
            new RpcMethodDefinition<>("systemUndelete", Transaction.class, TransactionResponse.class));

    private FileServiceDefinition() {
        // Forbid instantiation
    }

    @Override
    @NonNull
    public String basePath() {
        return "proto.FileService";
    }

    @Override
    @NonNull
    public Set<RpcMethodDefinition<?, ?>> methods() {
        return methods;
    }
}
