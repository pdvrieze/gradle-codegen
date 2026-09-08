/*
 * Copyright (c) 2016.
 *
 * This file is part of kotlinsql.
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

@file:Suppress("OPT_IN_USAGE")

import org.gradle.plugin.devel.tasks.PluginUnderTestMetadata
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    kotlin("jvm") version embeddedKotlinVersion

    id("com.gradle.plugin-publish") version "2.1.1"
}

version = "0.7.1"
group = "net.devrieze"

base {
    archivesName.set("gradlecodegen")
}

java {
    targetCompatibility = JavaVersion.VERSION_17
}

val functionalTestPluginClasspath = configurations.create("functionalTestPluginClasspath")

dependencies {
    functionalTestPluginClasspath("org.jetbrains.kotlin:kotlin-gradle-plugin:${embeddedKotlinVersion}")
}

tasks.named<PluginUnderTestMetadata>("pluginUnderTestMetadata") {
    pluginClasspath.from(functionalTestPluginClasspath)
}

testing {
    suites {
        val functionalTest = register("functionalTest", JvmTestSuite::class) {

            useJUnitJupiter()

//            testType = TestType.FUNCTIONAL_TESTING
            dependencies {
                implementation(gradleTestKit())
                implementation("org.jetbrains.kotlin:kotlin-test-junit5:${embeddedKotlinVersion}")
//                runtimeOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.10")
//                runtimeOnly(libs.junit.engine)
//                implementation(libs.junit.api)
            }

            targets.all {
                testTask.configure {
                    // Pass the functional test runtime classpath (which contains the Kotlin plugin) to the test JVM
                    systemProperty("testClasspath", classpath.joinToString(File.pathSeparator))
                }
            }
        }

        // Link the functional test suite with our plugin's development configurations
        gradlePlugin.testSourceSets.add(sourceSets.getByName(functionalTest.name))
    }
}

tasks.named("check") {
    dependsOn(tasks.named("functionalTest"))
}
kotlin {
    target {
        compilerOptions {
            apiVersion = KotlinVersion.KOTLIN_2_2
            languageVersion = KotlinVersion.KOTLIN_2_2
            jvmTarget = JvmTarget.fromTarget(java.targetCompatibility.toString())
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}

gradlePlugin {
    website = "https://github.com/pdvrieze/gradle-codegen.git"
    vcsUrl = "https://github.com/pdvrieze/gradle-codegen.git"
    plugins {
        register("gradlecodegen") {
            id = "net.devrieze.gradlecodegen"
            displayName = "Code generation plugin for gradle"
            description =
                "A plugin to aid with codeGeneration without using buildSrc. It provides an additional generate section to sourceSets. In this section individual files to be generated can be specified. Each sourceset has an accompanying ...generator sourceSet where the actual generator source can live. See https://github.com/pdvrieze/gradle-codegen for documentation"
            tags = listOf("generate", "codegen", "code-generation")
            implementationClass = "net.devrieze.gradlecodegen.CodegenPlugin"
        }
    }
}

val kotlin_version: String = embeddedKotlinVersion

dependencies {
    implementation(gradleApi())
/*
    "functionalTestImplementation"{
        implementation("org.jetbrains.kotlin:kotlin-test:${embeddedKotlinVersion}")
    }
*/
}

repositories {
    mavenLocal()
    mavenCentral()
}
