/*
 * Copyright (C) 2020 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonPropertyOrder

import java.time.Instant

import org.ossreviewtoolkit.model.config.AdvisorConfiguration
import org.ossreviewtoolkit.model.vulnerabilities.Vulnerability
import org.ossreviewtoolkit.utils.ort.Environment

/**
 * Type alias for a function that allows filtering of [AdvisorResult]s.
 */
typealias AdvisorResultFilter = (AdvisorResult) -> Boolean

/**
 * The summary of a single run of the advisor.
 */
data class AdvisorRun(
    /**
     * The [Instant] the advisor was started.
     */
    val startTime: Instant,

    /**
     * The [Instant] the advisor has finished.
     */
    val endTime: Instant,

    /**
     * The [Environment] in which the advisor was executed.
     */
    val environment: Environment,

    /**
     * The [AdvisorConfiguration] used for this run.
     */
    val config: AdvisorConfiguration,

    /**
     * The [AdvisorResult]s for all [Package]s.
     */
    @JsonPropertyOrder(alphabetic = true)
    val results: Map<Identifier, List<AdvisorResult>>
) {
    companion object {
        val EMPTY = AdvisorRun(
            startTime = Instant.EPOCH,
            endTime = Instant.EPOCH,
            environment = Environment(),
            config = AdvisorConfiguration(),
            results = emptyMap()
        )

        /**
         * A filter for [AdvisorResult]s that matches only results that contain vulnerabilities.
         */
        val RESULTS_WITH_VULNERABILITIES: AdvisorResultFilter = { it.vulnerabilities.isNotEmpty() }

        /**
         * A filter for [AdvisorResult]s that matches only results that contain defects.
         */
        val RESULTS_WITH_DEFECTS: AdvisorResultFilter = { it.defects.isNotEmpty() }

        /**
         * Return a filter for [AdvisorResult]s that contain issues. Match only results with an issue whose severity
         * is greater or equal than [minSeverity]. Often, issues are only relevant for certain types of advisors. For
         * instance, when processing vulnerability information, it is not of interest if an advisor for defects had
         * encountered problems. Therefore, support an optional filter for a [capability] of the advisor that produced
         * a result.
         */
        fun resultsWithIssues(
            minSeverity: Severity = Severity.HINT,
            capability: AdvisorCapability? = null
        ): AdvisorResultFilter =
            { result ->
                (capability == null || capability in result.advisor.capabilities) && result.summary.issues.any {
                    it.severity >= minSeverity
                }
            }
    }

    @JsonIgnore
    fun getIssues(): Map<Identifier, Set<Issue>> =
        buildMap<Identifier, MutableSet<Issue>> {
            results.forEach { (id, results) ->
                results.forEach { result ->
                    if (result.summary.issues.isNotEmpty()) {
                        getOrPut(id) { mutableSetOf() } += result.summary.issues
                    }
                }
            }
        }

    /**
     * Return a map of all [Package]s and the associated [Vulnerabilities][Vulnerability].
     */
    @JsonIgnore
    fun getVulnerabilities(): Map<Identifier, List<Vulnerability>> =
        results.mapValues { (_, results) ->
            results.flatMap { it.vulnerabilities }.mergeVulnerabilities()
        }

    /**
     * Return a list with all [Vulnerability] objects that have been found for the given [package][pkgId]. Results
     * from different advisors are merged if necessary.
     */
    fun getVulnerabilities(pkgId: Identifier): List<Vulnerability> =
        getFindings(pkgId) { it.vulnerabilities }.mergeVulnerabilities()

    /**
     * Return a list with all [Defect] objects that have been found for the given [package][pkgId]. If there are
     * results from different advisors, a union list is constructed. No merging is done, as it is expected that the
     * results from different advisors cannot be combined.
     */
    fun getDefects(pkgId: Identifier): List<Defect> = getFindings(pkgId) { it.defects }

    /**
     * Apply the given [filter] to the results stored in this record and return a map with the results that pass the
     * filter. When processing advisor results, often specific criteria are relevant, e.g. whether security
     * vulnerabilities were found or certain issues were detected. Using this function, it is easy to filter out only
     * those results matching such criteria.
     */
    fun filterResults(filter: AdvisorResultFilter): Map<Identifier, List<AdvisorResult>> =
        results.mapNotNull { (id, results) ->
            results.filter(filter).takeIf { it.isNotEmpty() }?.let { id to it }
        }.toMap()

    /**
     * Helper function to obtain the findings of type [T] for the given [package][pkgId] using a [selector] function
     * to extract the desired field.
     */
    private fun <T> getFindings(pkgId: Identifier, selector: (AdvisorResult) -> List<T>): List<T> =
        results[pkgId].orEmpty().flatMap(selector)
}

/**
 * Merge this collection of [Vulnerability] objects by combining vulnerabilities with the same ID and merging their
 * references. Other [Vulnerability] properties are taken from the first object which has any such property set.
 */
private fun Collection<Vulnerability>.mergeVulnerabilities(): List<Vulnerability> {
    val vulnerabilitiesById = groupBy { it.id }
    return vulnerabilitiesById.map { it.value.mergeReferences() }
}

/**
 * Merge this (non-empty) collection of [Vulnerability] objects (which are expected to have the same ID) to a single
 * [Vulnerability] that contains all the references from the original vulnerabilities (with duplicates removed). Other
 * [Vulnerability] properties are taken from the first object which has any such property set.
 */
private fun Collection<Vulnerability>.mergeReferences(): Vulnerability {
    val references = flatMapTo(mutableSetOf()) { it.references }
    val entry = find { it.summary != null || it.description != null } ?: first()
    return entry.copy(references = references.toList())
}
