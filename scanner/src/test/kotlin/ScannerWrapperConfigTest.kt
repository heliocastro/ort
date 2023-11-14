/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

package org.ossreviewtoolkit.scanner

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.maps.shouldContainExactly
import io.kotest.matchers.shouldBe

class ScannerWrapperConfigTest : WordSpec({
    "ScannerWrapperConfig.create()" should {
        "obtain values from the options" {
            val options = mapOf(
                ScannerMatcherConfig.PROP_CRITERIA_NAME to "foo",
                ScannerMatcherConfig.PROP_CRITERIA_MIN_VERSION to "1.2.3",
                ScannerMatcherConfig.PROP_CRITERIA_MAX_VERSION to "4.5.6",
                ScannerMatcherConfig.PROP_CRITERIA_CONFIGURATION to "config"
            )

            with(ScannerWrapperConfig.create(options).first) {
                with(matcherConfig) {
                    regScannerName shouldBe "foo"
                    minVersion shouldBe "1.2.3"
                    maxVersion shouldBe "4.5.6"
                    configuration shouldBe "config"
                }
            }
        }

        "filter used properties from the options" {
            val options = mapOf(
                ScannerMatcherConfig.PROP_CRITERIA_NAME to "foo",
                ScannerMatcherConfig.PROP_CRITERIA_MIN_VERSION to "1.2.3",
                ScannerMatcherConfig.PROP_CRITERIA_MAX_VERSION to "4.5.6",
                ScannerMatcherConfig.PROP_CRITERIA_CONFIGURATION to "config",
                "other" to "value"
            )

            ScannerWrapperConfig.create(options).second shouldContainExactly mapOf("other" to "value")
        }
    }
})