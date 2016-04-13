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

package kotlinsql.builder

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
import java.util.*
import java.util.concurrent.Callable

val Project.sourceSets: SourceSetContainer
  get() = project.convention.getPlugin(JavaPluginConvention::class.java).sourceSets

operator fun SourceSetContainer.get(name:String):SourceSet = getByName(name)

open class GenerateTask: DefaultTask() {

  init {
    group = "generate"
    outputs.upToDateWhen { task-> false } // do something smarter
  }

  @OutputDirectory
  var outputDir:Any = project.file(DEFAULT_GEN_DIR)
//
//  @OutputFiles
//  val outputFiles  = project.files().apply {
//      container?.all { spec: GenerateSpec ->
//        if (spec.output != null) {
//          this.add(project.files(File(outputDir, spec.output)))
//        }
//      }
//    }

  @Input
  internal var container: NamedDomainObjectContainer<GenerateSpec>? = null

  @TaskAction
  private fun generate() {

    container?.all { spec: GenerateSpec ->
      if (spec.output!=null) {
        val outFile = File(project.file(outputDir), project.file(spec.output).path)
        if (project.logger.isInfoEnabled) {
          project.logger.info("Generating ${spec.name} as '${spec.output}' as '${outFile}'")
        } else {
          project.logger.lifecycle("Generating ${spec.name} as '${spec.output}'")
        }
        spec.generator?.let { g ->
          if (! outFile.isFile) {
            outFile.parentFile.mkdirs()
            outFile.createNewFile()
          }
          outFile.writer().use { writer ->
            g.doGenerate(writer, project.files(spec.input))
          }

        } ?: logger.quiet("Missing output code for generateSpec ${spec.name}")
      }
    }
  }

  fun doGenerate(output: Writer, classPath: Iterable<File>, input:Iterable<File>) {
    project.logger.quiet("output code missing for ${output.toString()}")
  }
}

private const val OUTPUT_SOURCE_SET = "generatedSources"
private const val DEFAULT_GEN_DIR = "gen/kotlin"

interface GenerateImpl {
  fun doGenerate(output: Writer, input: FileCollection?)
}

class GenerateSpec(val name: String) {
  var output: Any?=null
  var generator: GenerateImpl?=null
  var input: Any?=null
}

open class GenerateSourceSet(val name:String, val generate:NamedDomainObjectContainer<GenerateSpec>) {

  fun generate(configureClosure: Closure<Any?>?): GenerateSourceSet {
    ConfigureUtil.configure(configureClosure, generate)
    return this
  }
}

class BuilderPlugin: Plugin<Project> {

  override fun apply(project: Project) {
    val javaBasePlugin = project.plugins.apply(JavaBasePlugin::class.java)
    project.plugins.apply(JavaPlugin::class.java)

    val sourceSets = project.sourceSets.all {sourceSet ->
      val generateTaskName = if(sourceSet.name=="main") "generate" else sourceSet.getTaskName("generate",null)
      val cleanTaskName = if(sourceSet.name=="main") "${BasePlugin.CLEAN_TASK_NAME}Generate" else sourceSet.getTaskName(BasePlugin.CLEAN_TASK_NAME,"generate")

      val configuration = project.configurations.create(generateTaskName)
      project.configurations.add(configuration)

      val generateExt = project.container(GenerateSpec::class.java)
      if (sourceSet is HasConvention) {
        sourceSet.convention.plugins.put("kotlinsql", GenerateSourceSet("generate", generateExt))
      } else {
        project.extensions.add(generateTaskName, generateExt)
      }

      val outputDir = project.file("gen/${sourceSet.name}/kotlin")

      val generateTask = project.tasks.create(generateTaskName, GenerateTask::class.java).apply {
        dependsOn(configuration)
        this.outputDir = outputDir
        container = generateExt
      }

      configuration.files ( object:Closure<Any>(this){ override fun call()= generateTask.outputDir })
      project.dependencies.add(sourceSet.compileConfigurationName, project.files(Callable{configuration.files}))

      // Late bind the actual output directory
      sourceSet.java.srcDir(Callable{generateTask.outputDir})


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




//    project.logger.lifecycle("Welcome to the kotlinsql builder plugin")
  }
}
