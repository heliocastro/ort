/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.model.config

import com.fasterxml.jackson.module.kotlin.readValue

import com.here.ort.model.yamlMapper

import io.kotlintest.matchers.haveSize
import io.kotlintest.should
import io.kotlintest.shouldBe
import io.kotlintest.shouldNotBe
import io.kotlintest.specs.WordSpec

class RepositoryConfigurationTest : WordSpec() {
    init {
        "RepositoryConfiguration" should {
            "be deserializable with empty excludes and resolutions" {
                val configuration = """
                    excludes:
                    resolutions:
                    """.trimIndent()

                val repositoryConfiguration = yamlMapper.readValue<RepositoryConfiguration>(configuration)

                repositoryConfiguration shouldNotBe null
                repositoryConfiguration.excludes shouldBe null
            }

            "deserialize to a path regex working with double star" {
                val configuration = """
                    excludes:
                      paths:
                      - pattern: "android/**build.gradle"
                        reason: "BUILD_TOOL_OF"
                        comment: "project comment"
                    """.trimIndent()

                val config = yamlMapper.readValue<RepositoryConfiguration>(configuration)
                config.excludes!!.paths[0].matches("android/project1/build.gradle") shouldBe true
            }

            "be deserializable" {
                val configuration = """
                    excludes:
                      paths:
                      - pattern: "project1/path"
                        reason: "BUILD_TOOL_OF"
                        comment: "project comment"
                      scopes:
                      - name: "scope"
                        reason: "TEST_TOOL_OF"
                        comment: "scope comment"
                    resolutions:
                      errors:
                      - message: "message"
                        reason: "CANT_FIX_ISSUE"
                        comment: "error comment"
                      rule_violations:
                      - message: "rule message"
                        reason: "PATENT_GRANT_EXCEPTION"
                        comment: "rule comment"
                    """.trimIndent()

                val repositoryConfiguration = yamlMapper.readValue<RepositoryConfiguration>(configuration)

                repositoryConfiguration shouldNotBe null
                repositoryConfiguration.excludes shouldNotBe null
                repositoryConfiguration.bitbakeRecipes shouldBe null

                val paths = repositoryConfiguration.excludes!!.paths
                paths should haveSize(1)

                val path = paths[0]
                path.pattern shouldBe "project1/path"
                path.reason shouldBe PathExcludeReason.BUILD_TOOL_OF
                path.comment shouldBe "project comment"

                val scopes = repositoryConfiguration.excludes!!.scopes
                scopes should haveSize(1)
                scopes.first().name.pattern shouldBe "scope"
                scopes.first().reason shouldBe ScopeExcludeReason.TEST_TOOL_OF
                scopes.first().comment shouldBe "scope comment"

                val errors = repositoryConfiguration.resolutions!!.errors
                errors should haveSize(1)
                val error = errors.first()
                error.message shouldBe "message"
                error.reason shouldBe ErrorResolutionReason.CANT_FIX_ISSUE
                error.comment shouldBe "error comment"

                val evalErrors = repositoryConfiguration.resolutions!!.ruleViolations
                evalErrors should haveSize(1)
                val evalError = evalErrors.first()
                evalError.message shouldBe "rule message"
                evalError.reason shouldBe RuleViolationResolutionReason.PATENT_GRANT_EXCEPTION
                evalError.comment shouldBe "rule comment"
            }

            "include bitbake recipes" {
                val configuration = """
                    bitbake_recipes:
                      project1:
                        - gdb
                        - libpng
                      project2:
                        - boost
                    """.trimIndent()

                val repositoryConfiguration = yamlMapper.readValue(configuration, RepositoryConfiguration::class.java)

                repositoryConfiguration shouldNotBe null
                repositoryConfiguration.excludes shouldBe null
                repositoryConfiguration.bitbakeRecipes shouldNotBe null

                val recipes = repositoryConfiguration.bitbakeRecipes!!
                recipes.keys should haveSize(2)

                recipes.contains("project1") shouldBe true
                recipes["project1"]!! should haveSize(2)
                recipes["project1"]!![0] shouldBe "gdb"
                recipes["project1"]!![1] shouldBe "libpng"

                recipes.contains("project2") shouldBe true
                recipes["project2"]!! should haveSize(1)
                recipes["project2"]!![0] shouldBe "boost"
            }
        }
    }
}
