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


plugins {
    `java-gradle-plugin`
    kotlin("jvm") version embeddedKotlinVersion
    `maven-publish`
    id("com.gradle.plugin-publish") version "1.1.0"
}

version = "0.6.0"
group = "net.devrieze"

base {
    archivesName.set("gradlecodegen")
}

java {
    targetCompatibility = JavaVersion.VERSION_1_8
}

kotlin {
    target {
        compilations.all {
            kotlinOptions {
                apiVersion = "1.8"
                languageVersion = "1.8"
                jvmTarget = "1.8"
            }
        }
    }
}

publishing {
    repositories {
        mavenLocal()
    }
}

pluginBundle {
    website = "https://github.com/pdvrieze/gradle-codegen.git"
    vcsUrl = "https://github.com/pdvrieze/gradle-codegen.git"
    tags = listOf("generate", "codegen", "code-generation")
}

gradlePlugin {
    plugins {
        register("gradlecodegen") {
            id = "net.devrieze.gradlecodegen"
            displayName = "Code generation plugin for gradle"
            description =
                "A plugin to aid with codeGeneration without using buildSrc. It provides an additional generate section to sourceSets. In this section individual files to be generated can be specified. Each sourceset has an accompanying ...generator sourceSet where the actual generator source can live. See https://github.com/pdvrieze/gradle-codegen for documentation"
            implementationClass = "net.devrieze.gradlecodegen.CodegenPlugin"
        }
    }
}

val kotlin_version: String = embeddedKotlinVersion

dependencies {
    implementation(gradleApi())
}

repositories {
    mavenLocal()
    mavenCentral()
}
