/*
 * Copyright (c) 2021.
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

package net.devrieze.gradlecodegen

import org.gradle.api.Named
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.provider.Property
import org.gradle.kotlin.dsl.property
import java.io.Serializable

class GenerateSpec(val _name: String, project: Project): Serializable, Named {
    val targetSourceSet: Property<String> = project.objects.property<String>().convention(_name)
    val output: Property<String> = project.objects.property()
    val generator: Property<String> = project.objects.property()
    val classpath: Property<FileCollection> = project.objects.property<FileCollection>().convention(project.files())
    val input: Property<Any> = project.objects.property<Any>()

    override fun getName(): String = _name
}