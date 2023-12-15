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

package com.hedera.node.app.service.schedule.impl;

import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.SemanticVersion;
import com.hedera.hapi.node.state.primitives.ProtoLong;
import com.hedera.hapi.node.state.primitives.ProtoString;
import com.hedera.hapi.node.state.schedule.Schedule;
import com.hedera.hapi.node.state.schedule.ScheduleList;
import com.hedera.node.app.service.schedule.ScheduleService;
import com.hedera.node.app.spi.state.Schema;
import com.hedera.node.app.spi.state.SchemaRegistry;
import com.hedera.node.app.spi.state.StateDefinition;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Set;

/**
 * Standard implementation of the {@link ScheduleService} {@link com.hedera.node.app.spi.Service}.
 */
public final class ScheduleServiceImpl implements ScheduleService {
    public static final String SCHEDULES_BY_ID_KEY = "SCHEDULES_BY_ID";
    public static final String SCHEDULES_BY_EXPIRY_SEC_KEY = "SCHEDULES_BY_EXPIRY_SEC";
    public static final String SCHEDULES_BY_EQUALITY_KEY = "SCHEDULES_BY_EQUALITY";

    @Override
    public void registerSchemas(@NonNull final SchemaRegistry registry, final SemanticVersion version) {
        registry.register(scheduleSchema(version));
    }

    private Schema scheduleSchema(final SemanticVersion version) {
        // Everything in memory for now
        return new ScheduleServiceSchema(version);
    }

    private static final class ScheduleServiceSchema extends Schema {
        public ScheduleServiceSchema(final SemanticVersion version) {
            super(version);
        }

        @SuppressWarnings("rawtypes")
        @NonNull
        @Override
        public Set<StateDefinition> statesToCreate() {
            return Set.of(schedulesByIdDef(), schedulesByExpirySec(), schedulesByEquality());
        }

        private static StateDefinition<ScheduleID, Schedule> schedulesByIdDef() {
            return StateDefinition.inMemory(SCHEDULES_BY_ID_KEY, ScheduleID.PROTOBUF, Schedule.PROTOBUF);
        }

        private static StateDefinition<ProtoLong, ScheduleList> schedulesByExpirySec() {
            return StateDefinition.inMemory(SCHEDULES_BY_EXPIRY_SEC_KEY, ProtoLong.PROTOBUF, ScheduleList.PROTOBUF);
        }

        private static StateDefinition<ProtoString, ScheduleList> schedulesByEquality() {
            return StateDefinition.inMemory(SCHEDULES_BY_EQUALITY_KEY, ProtoString.PROTOBUF, ScheduleList.PROTOBUF);
        }
    }
}
