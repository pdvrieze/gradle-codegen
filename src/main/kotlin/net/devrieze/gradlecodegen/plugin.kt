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
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.withType
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
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
    val generatorInst = if (Modifier.isStatic(modifiers)) null else receiver.getDeclaredConstructor().newInstance()

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

inline fun NamedDomainObjectContainer<GenerateSpec>.main(
    crossinline configure: GenerateSpec.() -> Unit
) = register("main") { configure() }

class CodegenPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.pluginManager.withPlugin("java") {
            project.logger.trace("Applying CodegenPlugin (after Java found)")
            val specContainer = project.objects.domainObjectContainer(GenerateSpec::class.java)
            project.extensions.add("generate", specContainer)

            specContainer.all {
                project.logger.trace("Configuring codegen ($name)")
                processSourceSet(project, this)
            }

        }

//    project.logger.lifecycle("Welcome to the kotlinsql builder plugin")
    }

    private fun processSourceSet(project: Project, generateSpec: GenerateSpec) {
        val generateName = generateSpec.name
        val targetSourceSetName = generateSpec.targetSourceSet.get()


        val generatorSourceSetName = if (generateName == "main") "generators" else "${generateName}Generators"
        val generatorSourceSet = project.sourceSets.register(generatorSourceSetName)

        val generateTaskName = if (targetSourceSetName == "main") "generate" else "${targetSourceSetName}Generate"
        val generateConfiguration = project.configurations.maybeCreate(generateTaskName)
        project.sourceSets.named(targetSourceSetName) {
            val targetSourceSet = this

            val outputDir = project.file("gen/$generateName")

            val generateTask = project.tasks.register(generateTaskName, GenerateTask::class.java) {
                dependsOn(Callable { generateConfiguration })
                dependsOn(generatorSourceSet.map { it.classesTaskName })
                this.classpath.set(project.files(Callable { generateConfiguration }, generatorSourceSet.map { it.runtimeClasspath }))
                this.outputDir.set(outputDir)
                this.output.set(generateSpec.output)
                this.input.set(generateSpec.input)
                this.generator.set(generateSpec.generator)
                project.logger.info("Generate task $name configured")
            }

            targetSourceSet.java.srcDir(generateTask.flatMap { it.outputDir })

            project.tasks.matching { it.name == "compileKotlin" }.withType(KotlinCompile::class).configureEach {
                this.doFirst { project.logger.lifecycle("Compiling Kotlin (${javaClass}) sources from: ${this}/${targetSourceSet.allSource.files}") }
            }

            project.configurations.getByName(targetSourceSet.implementationConfigurationName).extendsFrom(generateConfiguration)

            generateConfiguration.incoming.artifactView {  }.files.plus(generateTask.map { it.outputDir })

            project.extensions.findByType(IdeaModel::class.java)?.let { ideaModel ->
                ideaModel.module.generatedSourceDirs.add(project.file(generateTask.map { it.outputDir }))
            }

            project.tasks.matching { it.name == "clean" }.withType<Delete>().configureEach {
                delete(outputDir)
            }
        }
    }

}

private fun <T> closure(block: (args: Array<out Any?>) -> T): Closure<T> = object : Closure<T>(Unit) {

    override fun call(vararg args: Any?): T {
        return block(args)
    }
}