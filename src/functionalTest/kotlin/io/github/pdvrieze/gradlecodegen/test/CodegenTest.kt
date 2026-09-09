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
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GreetingPluginFunctionalTest {

    private lateinit var testProjectDir: File
    private lateinit var settingsFile: File
    private lateinit var buildFile: File
    private lateinit var generateSource: File

    @AfterTest
    fun cleanup() {
        testProjectDir.deleteRecursively()
    }

    @BeforeTest
    fun setup() {
        // Generates an isolated test workspace layout
        testProjectDir = Files.createTempDirectory("gradle-functional-test").toFile()
        settingsFile = File(testProjectDir, "settings.gradle.kts")
        buildFile = File(testProjectDir, "build.gradle.kts")
        // Dynamically configure a synthetic build project using the plugin id
        buildFile.writeText("""
            plugins {
                java
                id("org.jetbrains.kotlin.jvm")
                id("net.devrieze.gradlecodegen")
            }
            
            generate {
                this.register("main") {
                    output = "org/example/outputpackage/GeneratedClass.kt"
                    generator = "org.example.generators.MyGenerator"
                    input = "hello generate plugin!"
                }                
            }
           
            repositories {
                mavenCentral()
            }
            
        """.trimIndent())


        val generateSourceSet = testProjectDir.resolve("src/generators/kotlin/")
        generateSource = generateSourceSet.resolve("org/example/generators/MyGenerator.kt")

        settingsFile.writeText("""
            rootProject.name = "test-sandbox"
            """.trimIndent())

        generateSource.parentFile.mkdirs()
        generateSource.writeText("""
            package org.example.generators
                     
            class MyGenerator {
                fun doGenerate(target: Appendable, param: Any) {
                    target.append("package org.example.outputpackage\n\n")
                    target.append("object GeneratedClass {\n")
                    target.append("    fun hello() {\n")
                    target.append("        println(\"${'$'}param\")\n")
                    target.append("    }\n")
                    target.append("}\n")
                }
            }
                     
        """.trimIndent()
        )
    }

    @org.junit.jupiter.api.Test
    fun `Create generator`() {

        // Run the runner lifecycle targeting our generated structure
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("--info","--stacktrace", "generatorsClasses")
            .withPluginClasspath(/*testClasspath*/) // Automatic mapping of binary distributions to the sub-build
            .forwardOutput()
            .build()

        val compileTask = assertNotNull(result.tasks.find { it.path == ":compileGeneratorsKotlin" })
        assertEquals(TaskOutcome.SUCCESS, compileTask.outcome)
    }

    @org.junit.jupiter.api.Test
    fun `Create simple file`() {

        // Run the runner lifecycle targeting our generated structure
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("--stacktrace", "generate")
            .withPluginClasspath(/*testClasspath*/) // Automatic mapping of binary distributions to the sub-build
            .forwardOutput()
            .build()

        val compileTask = assertNotNull(result.tasks.find { it.path == ":compileGeneratorsKotlin" })
        assertEquals(TaskOutcome.SUCCESS, compileTask.outcome)

        val genClassesTask = assertNotNull(result.tasks.find { it.path == ":generatorsClasses" })
        assertEquals(TaskOutcome.UP_TO_DATE, genClassesTask.outcome)

        val generatedFile = testProjectDir.resolve("gen/main/org/example/outputpackage/GeneratedClass.kt")
        assertTrue(generatedFile.exists())
        assertTrue(generatedFile.readLines().isNotEmpty())
    }

    @org.junit.jupiter.api.Test
    fun `Call simple generated class from simple main class`() {
        val useFile = testProjectDir.resolve("src/main/kotlin/org/example/Main.kt")
        useFile.parentFile.mkdirs()
        useFile.writeText("""
            package org.example
            
            import org.example.outputpackage.GeneratedClass
            
            fun main() {
                GeneratedClass.hello()
            }
        """.trimIndent())

        // Run the runner lifecycle targeting our generated structure
        val result = GradleRunner.create()
            .withProjectDir(testProjectDir)
            .withArguments("--stacktrace", "assemble")
            .withPluginClasspath(/*testClasspath*/) // Automatic mapping of binary distributions to the sub-build
            .forwardOutput()
            .build()
    }
}
