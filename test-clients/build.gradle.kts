/*-
 * ‌
 * Hedera Services Test Clients
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

plugins {
    id("com.hedera.hashgraph.hedera-conventions")
}

description = "Hedera Services Test Clients for End to End Tests (EET)"

// Add the EET task for executing end-to-end tests
testing {
    suites {
        @Suppress("UnstableApiUsage", "UNUSED_VARIABLE")
        val eet by registering(JvmTestSuite::class) {
            testType.set("end-to-end-test")
            dependencies {
                implementation(project)
            }

            // "shouldRunAfter" will only make sure if both test and eet are run concurrently,
            // that "test" completes first. If you run "eet" directly, it doesn't force "test" to run.
            targets {
                all {
                    testTask.configure {
                        shouldRunAfter(tasks.test)
                    }
                }
            }
        }
    }
}

tasks.test {
    // Disable these EET tests from being executed as part of the gradle "test" task. We should maybe remove them
    // from src/test into src/eet, so it can be part of an eet test task instead. See issue #3412
    // (https://github.com/hashgraph/hedera-services/issues/3412).
    exclude("**/*")
}

sourceSets {
    // Needed because "resource" directory is misnamed. See https://github.com/hashgraph/hedera-services/issues/3361
    main {
        resources {
            srcDir("src/main/resource")
        }
    }

    // This can be removed after fixing #3412 (https://github.com/hashgraph/hedera-services/issues/3412)
    getByName("eet") {
        java {
            srcDir("src/test/java")
        }
    }
}

dependencies {
    implementation(project(":hapi-utils"))
    implementation(project(":hapi-fees"))
    implementation(libs.bundles.besu)
    implementation(libs.bundles.logging)
    implementation(testLibs.besu.internal)
    implementation(testLibs.commons.collections4)
    implementation(libs.commons.io)
    implementation(testLibs.ethereumj) {
        exclude("", "junit")
        exclude("com.cedarsoftware")
        exclude("com.googlecode.json-simple")
        exclude("io.netty")
        exclude("org.apache.logging.log4j")
        exclude("org.ethereum")
        exclude("org.iq80.leveldb")
        exclude("org.slf4j")
        exclude("org.xerial.snappy")
    }
    implementation(libs.guava)
    implementation(libs.hapi)
    implementation(libs.headlong)
    implementation(testLibs.json)
    implementation(testLibs.junit.jupiter.api)
    implementation(testLibs.picocli)
    implementation(libs.protobuf.java)
    implementation(testLibs.snakeyaml)
    implementation(libs.swirlds.common)
}