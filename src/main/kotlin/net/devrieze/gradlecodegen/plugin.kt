/*
 * Copyright (c) 2016.
 *
 * This file is part of gradle-codegen.
 *
 * This file is licenced to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You should have received a copy of the license with the source distribution.
 * Alternatively, you may obtain a copy of the License at6uHXpFWIZR
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

import groovy.lang.Closure
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.HasConvention
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.plugins.ide.idea.model.IdeaModel
import java.io.File
import java.io.Writer
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.concurrent.Callable

val Project.sourceSets: SourceSetContainer
    get() = project.extensions.getByType(JavaPluginExtension::class.java).sourceSets

operator fun SourceSetContainer.get(name: String): SourceSet = getByName(name)

internal val Method.parameterCountCompat: Int get() = parameterTypes.size

fun Method.doInvoke(receiver: Class<out Any>, firstParam: Any, input: Any?) {
    val generatorInst = if (Modifier.isStatic(modifiers)) null else receiver.newInstance()

    val body = { output: Any ->
        if (this.parameterCountCompat == 1) {
            invoke(generatorInst, output)
        } else {
            invoke(generatorInst, output, input)
        }
    }

    if (firstParam is File) {
        body(firstParam)
    } else {
        @Suppress("UNCHECKED_CAST")
        (((firstParam as () -> Any).invoke()) as Writer).use(body)
    }
}

const val DEFAULT_GEN_DIR = "gen"

class CodegenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.plugins.apply(JavaBasePlugin::class.java)

        val sourceSetsToSkip = mutableSetOf("generators")
        project.sourceSets.all { sourceSet ->
            if (!sourceSetsToSkip.contains(sourceSet.name)) {
                if (sourceSet.name.toLowerCase().endsWith("generators")) {
                    project.logger.error("Generators sourceSet (${sourceSet.name}) not registered in $sourceSetsToSkip")
                } else {
                    processSourceSet(project, sourceSet, sourceSetsToSkip)
                    project.logger.debug("sourceSetsToSkip is now: $sourceSetsToSkip")
                }
            }

        }


//    project.logger.lifecycle("Welcome to the kotlinsql builder plugin")
    }

    private fun processSourceSet(project: Project, sourceSet: SourceSet, doSkip: MutableSet<String>) {
        val generateTaskName = if (sourceSet.name == "main") "generate" else sourceSet.getTaskName("generate", null)

        val generateConfiguration = project.configurations.maybeCreate(generateTaskName)
        project.configurations.add(generateConfiguration)

        val generatorSourceSetName = if (sourceSet.name == "main") "generators" else "${sourceSet.name}Generators"
        doSkip.add(generatorSourceSetName)

        val generatorSourceSet = project.sourceSets.maybeCreate(generatorSourceSetName)

        val generateExt = createConfigurationExtension(project, sourceSet, generateTaskName) !!

        val outputDir = project.file("gen/${sourceSet.name}")

        val generateTask = project.tasks.create(generateTaskName, GenerateTask::class.java).apply {
            dependsOn(Callable { generateConfiguration })
            dependsOn(Callable { generatorSourceSet.classesTaskName })
            classpath =
                project.files(Callable { generateConfiguration }, Callable { generatorSourceSet.runtimeClasspath })
            this.outputDir = outputDir
            container = generateExt
        }


        project.dependencies.add(sourceSet.implementationConfigurationName,
                                 project.files(Callable { generateConfiguration.files })
                                     .apply { builtBy(generateTask) })

        // Late bind the actual output directory
        sourceSet.java.srcDir(Callable { generateTask.outputDir })

        project.configurations.getByName(sourceSet.implementationConfigurationName).extendsFrom(generateConfiguration)

        project.afterEvaluate {
            generateConfiguration.files(closure { generateTask.outputDir })

            project.extensions.findByType(IdeaModel::class.java)?.let { ideaModel ->
                ideaModel.module.generatedSourceDirs.add(project.file(generateTask.outputDir))
            }

            (project.tasks.getByName("clean") as? Delete)?.let { cleanTask ->
                cleanTask.delete(outputDir)
            }

        }
    }

    private fun createConfigurationExtension(
        project: Project,
        sourceSet: SourceSet,
        generateExtensionName: String?,
    ): NamedDomainObjectContainer<GenerateSpec>? {
        val generateExt = project.container(GenerateSpec::class.java)
/*
        if (sourceSet is HasConvention) {
            sourceSet.convention.plugins.put("net.devrieze.gradlecodegen", GenerateSourceSet(generateExt))
        } else {
*/
            sourceSet.extensions.add(generateExtensionName, generateExt)
//        }
        return generateExt
    }

}

private fun <T> closure(block: (args: Array<out Any?>) -> T): Closure<T> = object : Closure<T>(Unit) {

    override fun call(vararg args: Any?): T {
        return block(args)
    }
}