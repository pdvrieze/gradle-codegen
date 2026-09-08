/*
 * Copyright (c) 2026.
 *
 * This file is part of gradle-codegen.
 *
 * This file is licenced to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You should have received a copy of the license with the source distribution.
 * Alternatively, you may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package io.github.pdvrieze.gradlecodegen.test

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertTrue

class GreetingPluginFunctionalTest {

    private lateinit var testProjectDir: File
    private lateinit var settingsFile: File
    private lateinit var buildFile: File
    private lateinit var generateSource: File

    @AfterTest
    fun cleanup() {
//        testProjectDir.deleteRecursively()
    }

    @BeforeTest
    fun setup() {
        // Generates an isolated test workspace layout
        testProjectDir = Files.createTempDirectory("gradle-functional-test").toFile()
        settingsFile = File(testProjectDir, "settings.gradle.kts")
        buildFile = File(testProjectDir, "build.gradle.kts")
        val generateSourceSet = testProjectDir.resolve("src/mainGenerate/kotlin/")
        generateSource = generateSourceSet.resolve("org.example.generators.MyGenerator.kt")

        settingsFile.writeText("""
            rootProject.name = "test-sandbox"
            """.trimIndent())

        generateSourceSet.mkdirs()
        generateSource.writeText("""
            package org.example.generators
                     
            class MyGenerator {
                fun doGenerate(target: Appendable, param: String) {
                    target.append("package org.example.outputpackage\n\n")
                    target.append("object GeneratedClass {\n")
                    target.append("    fun hello() {\n")
                    target.append("        println(param)\n")
                    target.append("    }\n")
                    target.append("}\n")
                }
            }
                     
        """.trimIndent()
        )
    }

    @org.junit.jupiter.api.Test
    fun `plugin registers hello task and runs successfully`() {
        // Dynamically configure a synthetic build project using the plugin id
        buildFile.writeText("""
            plugins {
                java
                id("org.jetbrains.kotlin.jvm")
                id("net.devrieze.gradlecodegen")
            }
            
            generate {
                register("main") {
                    output = "kotlin/org/example/outputpackage/GeneratedClass.kt"
                    generator = "org.example.generators.MyGenerator"
                    input = "hello generate plugin!"
                }                
            }
           
            
        """.trimIndent())

        val testClasspath = System.getProperty("testClasspath")
            .split(File.pathSeparator)
            .map { File(it) }

        // Run the runner lifecycle targeting our generated structure
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("tasks", "--stacktrace")
            .withPluginClasspath(/*testClasspath*/) // Automatic mapping of binary distributions to the sub-build
            .forwardOutput()
            .build()

        // Assert build log behaviors
//        assertTrue(result.output.contains("Hello from the custom Gradle 9 plugin!"))
    }
}
