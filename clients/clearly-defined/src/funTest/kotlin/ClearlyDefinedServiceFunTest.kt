/*
 * Copyright (C) 2019 Bosch Software Innovations GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

package org.ossreviewtoolkit.clients.clearlydefined

import io.kotest.core.spec.style.WordSpec
import io.kotest.matchers.comparables.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldStartWith

import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.ContributionInfo
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.ContributionPatch
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Coordinates
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Curation
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Licensed
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Patch
import org.ossreviewtoolkit.clients.clearlydefined.ClearlyDefinedService.Server
import org.ossreviewtoolkit.utils.test.ExpensiveTag
import org.ossreviewtoolkit.utils.test.shouldNotBeNull

class ClearlyDefinedServiceFunTest : WordSpec({
    "Downloading a contribution patch" should {
        "return curation data".config(tags = setOf(ExpensiveTag)) {
            val service = ClearlyDefinedService.create(Server.PRODUCTION)

            val curation = service.getCuration(
                ComponentType.MAVEN,
                Provider.MAVEN_CENTRAL,
                "javax.servlet",
                "javax.servlet-api",
                "3.1.0"
            )

            curation.licensed?.declared shouldBe "CDDL-1.0 OR GPL-2.0-only WITH Classpath-exception-2.0"
        }
    }

    "Uploading a contribution patch" should {
        val info = ContributionInfo(
            type = ContributionType.OTHER,
            summary = "summary",
            details = "details",
            resolution = "resolution",
            removedDefinitions = false
        )

        val revisions = mapOf(
            "6.2.3" to Curation(licensed = Licensed(declared = "Apache-1.0"))
        )

        val patch = Patch(
            Coordinates(
                type = ComponentType.NPM,
                provider = Provider.NPM_JS,
                namespace = "@nestjs",
                name = "platform-express",
                revision = "6.2.3"
            ),
            revisions
        )

        "only serialize non-null values".config(tags = setOf(ExpensiveTag)) {
            val contributionPatch = ContributionPatch(info, listOf(patch))

            val patchJson = ClearlyDefinedService.JSON_MAPPER.writeValueAsString(contributionPatch)

            patchJson shouldNot io.kotest.matchers.string.include("null")
        }

        // Disable this test by default as it talks to the real development instance of ClearlyDefined and creates
        // pull-requests at https://github.com/clearlydefined/curated-data-dev.
        "return a summary of the created pull-request".config(enabled = false, tags = setOf(ExpensiveTag)) {
            val service = ClearlyDefinedService.create(Server.DEVELOPMENT)

            val summary = service.putCuration(ContributionPatch(info, listOf(patch)))

            summary shouldNotBeNull {
                prNumber shouldBeGreaterThan 0
                url shouldStartWith "https://github.com/clearlydefined/curated-data-dev/pull/"
            }
        }
    }
})
