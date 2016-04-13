/*
 * Copyright (c) 2016.
 *
 * This file is part of kotlinsql.
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

package net.devrieze.gradlecodgen

import groovy.lang.Closure
import org.gradle.api.DefaultTask
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.HasConvention
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.util.ConfigureUtil
import java.io.File
import java.io.StringWriter
import java.io.Writer
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import java.util.concurrent.Callable

val Project.sourceSets: SourceSetContainer
  get() = project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets

operator fun SourceSetContainer.get(name:String): SourceSet = getByName(name)

open class GenerateTask: DefaultTask() {

  init {
    group = "generate"
    outputs.upToDateWhen { task-> false } // do something smarter
  }

  @OutputDirectory
  var outputDir:Any = project.file(DEFAULT_GEN_DIR)

  var classPath: FileCollection? = null

  @Input
  internal var container: NamedDomainObjectContainer<GenerateSpec>? = null

  @TaskAction
  private fun generate() {
    container?.all { spec: GenerateSpec ->
      // TODO don't pass the local classloader, generators should not be able to use internals
      URLClassLoader(combinedClasspath(spec.classpath),javaClass.classLoader).use { classLoader ->

        if (spec.output!=null) {
          val outFile = File(project.file(outputDir), spec.output)

          if (project.logger.isInfoEnabled) {
            project.logger.info("Generating ${spec.name} as '${spec.output}' as '${outFile}'")
          } else {
            project.logger.lifecycle("Generating ${spec.name} as '${spec.output}'")
          }
          spec.generator?.let { gname ->
            val generatorClass = classLoader.loadClass(gname)
            val generatorInst = generatorClass.newInstance()
            if (! outFile.isFile) {
              outFile.parentFile.mkdirs()
              outFile.createNewFile()
            }
            val m = generatorClass.getGenerateMethod(spec.input)
            outFile.writer().use { writer ->
              if (m.parameterCount==2) {
                m.invoke(generatorInst, writer, spec.input)
              } else {
                m.invoke(generatorInst, writer)
              }
            }

          } ?: logger.quiet("Missing output code for generateSpec ${spec.name}")
        }
      }
    }
  }

  private fun Class<*>.getGenerateMethod(input: Any?):Method {
    return methods.asSequence()
        .filter { it.name=="doGenerate" }
        .filter { Modifier.isPublic(it.modifiers) }
        .filter { if (input==null) it.parameterCount in 1..2 else it.parameterCount==2 }
        .filter { Appendable::class.java.isAssignableFrom(it.parameterTypes[0]) && it.parameterTypes[0].isAssignableFrom(Writer::class.java) }
        .filter { if (it.parameterCount==1) true else  it.parameterTypes[1].isInstance(input) }
        .singleOrNull() ?: throw NoSuchMethodError("Generators must have a unique public method \"doGenerate(Appendable|Writer, " +
                "[Object])\" where the second parameter is optional iff the input is null" )
  }

  private fun combinedClasspath(others: FileCollection?): Array<out URL>? {

    fun Iterable<File>.toUrls():Sequence<URL> = asSequence().map { it.toURI().toURL() }

    return mutableListOf<URL>().apply {
      classPath?.let{ it.toUrls().forEach{ add(it) } }
      others?.let{ it.toUrls().forEach{ add(it) } }
    }.toTypedArray()
  }

}

public const val INPUT_SOURCE_SET = "generatorSources"
public const val OUTPUT_SOURCE_SET = "generatedSources"
public const val DEFAULT_GEN_DIR = "gen"

interface GenerateImpl {
  fun doGenerate(output: Writer, input: Iterable<out File>?)
}

class GenerateSpec(val name: String) {
  var output: String?=null
  var generator: String?=null
  var classpath: FileCollection? = null
  var input: Any?=null
}

open class GenerateSourceSet(val name:String, val generate: NamedDomainObjectContainer<GenerateSpec>) {

  fun generate(configureClosure: Closure<Any?>?): GenerateSourceSet {
    ConfigureUtil.configure(configureClosure, generate)
    return this
  }
}

class CodegenPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    val javaBasePlugin = project.plugins.apply(JavaBasePlugin::class.java)
    project.plugins.apply(JavaPlugin::class.java)

    val sourceSets = project.sourceSets.all {sourceSet ->
      processSourceSet(project, sourceSet)

    }




//    project.logger.lifecycle("Welcome to the kotlinsql builder plugin")
  }

  private fun processSourceSet(project: Project, sourceSet: SourceSet) {
    val generateTaskName = if (sourceSet.name == "main") "generate" else sourceSet.getTaskName("generate", null)
    val cleanTaskName = if (sourceSet.name == "main") "${BasePlugin.CLEAN_TASK_NAME} Generate" else sourceSet.getTaskName(BasePlugin.CLEAN_TASK_NAME, "generate")

    val configuration = project.configurations.create(generateTaskName)
    project.configurations.add(configuration)

    val generateExt = project.container(GenerateSpec::class.java)
    if (sourceSet is HasConvention) {
      sourceSet.convention.plugins.put("net.devrieze.gradlecodegen", GenerateSourceSet("generate", generateExt))
    } else {
      project.extensions.add(generateTaskName, generateExt)
    }

    val outputDir = project.file("gen/${sourceSet.name}")

    val generateTask = project.tasks.create(generateTaskName, GenerateTask::class.java).apply {
      dependsOn(configuration)
      this.outputDir = outputDir
      container = generateExt
    }

    configuration.files (object : Closure<Any>(this) {
      override fun call() = generateTask.outputDir
    })
    project.dependencies.add(sourceSet.compileConfigurationName, project.files(Callable { configuration.files }))

    // Late bind the actual output directory
    sourceSet.java.srcDir(Callable { generateTask.outputDir })


    val cleanTask = project.tasks.create(cleanTaskName) { clean ->
      clean.description = "Clean the generated source folder"
      clean.group = BasePlugin.BUILD_GROUP
      clean.doFirst { project.delete(outputDir) }
    }

    project.tasks.getByName(sourceSet.compileJavaTaskName).dependsOn.add(generateTask)
    project.tasks.getByName(sourceSet.getCompileTaskName("kotlin")).dependsOn(generateTask)

    project.afterEvaluate {
      project.extensions.getByType(IdeaModel::class.java)?.let { ideaModel ->
        ideaModel.module.generatedSourceDirs.add(project.file(generateTask.outputDir))
      }
    }
  }
}
