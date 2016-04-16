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

package net.devrieze.gradlecodegen

import groovy.lang.Closure
import org.gradle.api.*
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.HasConvention
import org.gradle.api.internal.tasks.DefaultSourceSetOutput
import org.gradle.api.internal.tasks.TaskDependencyInternal
import org.gradle.api.internal.tasks.TaskDependencyResolveContext
import org.gradle.api.plugins.BasePlugin
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.tasks.*
import org.gradle.plugins.ide.idea.model.IdeaModel
import org.gradle.util.ConfigureUtil
import org.jetbrains.annotations.NotNull
import java.io.File
import java.io.StringWriter
import java.io.Writer
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.net.URL
import java.net.URLClassLoader
import java.util.*
import java.util.concurrent.Callable
import kotlin.reflect.KClass

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

  var classpath: FileCollection? = null

  val dirGenerator = GenerateDirSpec()

  fun dirGenerator(closure: Closure<Any?>?) {
    ConfigureUtil.configure(closure, dirGenerator)
  }

  @Input
  internal var container: NamedDomainObjectContainer<GenerateSpec>? = null

  @TaskAction
  private fun generate() {
    URLClassLoader(combinedClasspath(null)). use { joinedLoader ->
      container?.all { spec: GenerateSpec ->
        val specClasspath = spec.classpath
        if (specClasspath ==null || specClasspath.isEmpty) {
          generateFile(spec, joinedLoader)
        } else {
          URLClassLoader(combinedClasspath(spec.classpath)).use { classLoader ->
            generateFile(spec, classLoader)
          }
        }
      }

      if (dirGenerator.generator!=null) {
        val outDir = if (dirGenerator.outputDir ==null) project.file(outputDir) else resolveFile(project.file(outputDir),dirGenerator.outputDir!!)
        if (dirGenerator.classpath!=null) {
          URLClassLoader(combinedClasspath(dirGenerator.classpath)). use {
            generateDir(outDir, it)
          }
        } else {
          generateDir(outDir, joinedLoader)
        }
      }

    }
  }

  private fun generateDir(outDir: File, classLoader: ClassLoader) {
    if (! outDir.exists()) {
      if (!outDir.mkdirs()) throw InvalidUserDataException("The output directory $outDir could not be created")
    } // ensure the output directory exists
    if (!outDir.canWrite()) throw InvalidUserDataException("The output directory $outDir is not writeable")

    val generatorClass = classLoader.loadClass(dirGenerator.generator)

    val baseError = """
              Directory generators must have a unique public method "doGenerate(File, [Object])"
              where the second parameter is optional iff the input is null. If not a static
              method, the class must have a noArg constructor. """.trimIndent()

    generatorClass.execute(outDir, dirGenerator.input, baseError)
  }

  private fun resolveFile(context:File, fileName:String):File {
    return File(fileName).let {
      if (it.isAbsolute) it
      else File(project.file(outputDir), fileName)
    }
  }

  private fun generateFile(spec: GenerateSpec, classLoader: ClassLoader) {
    if (spec.output != null) {
      val outFile = resolveFile(project.file(outputDir), spec.output!!)

      if (spec.generator != null) {
        val gname = spec.generator
        val generatorClass = classLoader.loadClass(gname)
        if (outFile.isDirectory) throw InvalidUserDataException("The output can not be a directory, it must be a file ($outFile)")
        if (!outFile.exists()) {
          outFile.parentFile.apply { if (! exists()) mkdirs() || throw InvalidUserDataException("The target directory for the output file $outFile could not be created")}
          outFile.createNewFile()
        }
        if (!outFile.canWrite()) throw InvalidUserDataException("The output file ($outFile) is not writeable.")

        if (project.logger.isInfoEnabled) {
          project.logger.info("Generating ${spec.name} as '${spec.output}' as '${outFile}'")
        } else {
          project.logger.lifecycle("Generating ${spec.name} as '${spec.output}'")
        }

        val baseError = """
              Generators must have a unique public method "doGenerate(Writer|Appendable, [Object])"
              where the second parameter is optional iff the input is null. If not a static
              method, the class must have a noArg constructor.""".trimIndent()

        generatorClass.execute({outFile.writer()}, spec.input, baseError)

      } else {
        throw InvalidUserDataException("Missing output code for generateSpec ${spec.name}, no generator provided")
      }
    }
  }

  private fun Class<*>.getGeneratorMethods(firstParamWriter: Boolean, input:Any?):List<Method> {
    return methods.asSequence()
        .filter { it.name=="doGenerate" }
        .filter { Modifier.isPublic(it.modifiers) }
        .filter { if (input==null) it.parameterCount in 1..2 else it.parameterCount==2 }
        .filter {
          if(firstParamWriter) {
            Appendable::class.java.isAssignableFrom(it.parameterTypes[0]) && it.parameterTypes[0].isAssignableFrom(Writer::class.java)
          } else {
            File::class.java==it.parameterTypes[0]
          }
        }.toList()
  }

  private fun Class<out Any>.execute(firstParam: Any, input:Any?, baseErrorMsg:String) {
    getGeneratorMethods(firstParam !is File, input).let { candidates ->
      try {
        var resolvedInput = input
        var methodIterator = candidates
            .asSequence()
            .filter { if (it.parameterCount == 1) true else isSecondParameterCompatible(input, it) }
            .iterator()

        if (input is Callable<*> && !methodIterator.hasNext()) {
          resolvedInput = input.call()
          methodIterator = candidates.asSequence()
              .filter { isSecondParameterCompatible(resolvedInput, it) }.iterator()
        }

        if (! methodIterator.hasNext()) throw InvalidUserDataException(errorMsg("No candidate method found", candidates, baseErrorMsg, input))

        val m = methodIterator.next()

        if (methodIterator.hasNext()) { throw InvalidUserCodeException(ambiguousChoice(candidates, baseErrorMsg, input)) }
        m.doInvoke(this, firstParam, resolvedInput)
        return
      } catch (e:Exception) {
        throw InvalidUserDataException("Could not execute the generator code", e)
      }
    }
  }

  private fun ambiguousChoice(candidates: Iterable<Method>, baseErrorMsg: String, input:Any?): String {
    return errorMsg("More than 1 valid candidate found.", candidates, baseErrorMsg, input)
  }

  private fun errorMsg(error:String, candidates: Iterable<Method>, baseErrorMsg: String, input:Any?): String {
    return buildString {
      appendln(error)
      appendln(baseErrorMsg).appendln()
      appendln("Candidates were: (with input = ${input?.javaClass?.name ?: "null"}):").append("    ")
      candidates.joinTo(this, "\n    ") { candidates.toString() }
    }
  }

  private fun isSecondParameterCompatible(input: Any?, method: Method): Boolean {
    if (input==null) {
      return (method.parameterAnnotations[1].none { annotation -> annotation is NotNull })
    } else {
      return method.parameterTypes[1].isInstance(input)
    }
  }

  private fun combinedClasspath(others: FileCollection?): Array<out URL>? {

    fun Iterable<File>.toUrls():Sequence<URL> = asSequence().map { it.toURI().toURL() }

    return mutableListOf<URL>().apply {
      classpath?.let{ it.toUrls().forEach{ add(it) } }
      others?.let{ it.toUrls().forEach{ add(it) } }
    }.toTypedArray().apply { project.logger.debug("Classpath for generator: ${Arrays.toString(this)}") }
  }

}

fun Method.doInvoke(receiver:Class<out Any>, firstParam: Any, input: Any?) {
  val generatorInst = if (Modifier.isStatic(modifiers)) null else receiver.newInstance()

  val body = { output:Any ->
    if (this.parameterCount == 1) {
      invoke(generatorInst, output)
    } else {
      invoke(generatorInst, output, input)
    }
  }

  if (firstParam is File) {
    body(firstParam)
  } else {
    @Suppress("UNCHECKED_CAST")
    (((firstParam as ()->Any).invoke()) as Writer).use(body)
  }
}

public const val INPUT_SOURCE_SET = "generatorSources"
public const val OUTPUT_SOURCE_SET = "generatedSources"
public const val DEFAULT_GEN_DIR = "gen"

interface GenerateImpl {
  fun doGenerate(output: Writer, input: Iterable<File>?)
}

class GenerateSpec(val name: String) {
  var output: String?=null
  var generator: String?=null
  var classpath: FileCollection? = null
  var input: Any?=null
}

class GenerateDirSpec() {
  var input: Any?=null
  var generator: String?=null
  var classpath: FileCollection? = null
  var outputDir: String?=null
}

open class GenerateSourceSet(val generate: NamedDomainObjectContainer<GenerateSpec>) {

  fun generate(configureClosure: Closure<Any?>?): GenerateSourceSet {
    ConfigureUtil.configure(configureClosure, generate)
    return this
  }
}

class CodegenPlugin : Plugin<Project> {

  override fun apply(project: Project) {
    project.plugins.apply(JavaBasePlugin::class.java)

    val sourceSetsToSkip = mutableSetOf<String>("generators")
    project.sourceSets.all {sourceSet ->
      if (! sourceSetsToSkip.contains(sourceSet.name)) {
        if (sourceSet.name.endsWith("enerators")) {
          project.logger.error("Generators sourceSet (${sourceSet.name}) not registered in ${sourceSetsToSkip}")
        }else {
          processSourceSet(project, sourceSet, sourceSetsToSkip)
          project.logger.debug("sourceSetsToSkip is now: ${sourceSetsToSkip}")
        }
      }

    }




//    project.logger.lifecycle("Welcome to the kotlinsql builder plugin")
  }

  private fun processSourceSet(project: Project, sourceSet: SourceSet, doSkip:MutableSet<String>) {
    val generateTaskName = if (sourceSet.name == "main") "generate" else sourceSet.getTaskName("generate", null)
    val cleanTaskName = if (sourceSet.name == "main") "${BasePlugin.CLEAN_TASK_NAME}Generate" else sourceSet.getTaskName(BasePlugin.CLEAN_TASK_NAME, "generate")

    val generateConfiguration = project.configurations.maybeCreate(generateTaskName)
    project.configurations.add(generateConfiguration)

    val generatorSourceSetName = if (sourceSet.name == "main") "generators" else "${sourceSet.name}Generators"
    doSkip.add(generatorSourceSetName)

    val generatorSourceSet = project.sourceSets.maybeCreate(generatorSourceSetName)

    val generateExt = createConfigurationExtension(project, sourceSet, generateTaskName)

    val outputDir = project.file("gen/${sourceSet.name}")

    val generateTask = project.tasks.create(generateTaskName, GenerateTask::class.java).apply {
      dependsOn(Callable { generateConfiguration })
      dependsOn(Callable { generatorSourceSet.classesTaskName })
      classpath = project.files(Callable {generateConfiguration} , Callable { generatorSourceSet.runtimeClasspath } )
      this.outputDir = outputDir
      container = generateExt
    }

    project.afterEvaluate { generateConfiguration.files (closure { generateTask.outputDir }) }

    project.dependencies.add(sourceSet.compileConfigurationName, project.files(Callable { generateConfiguration.files }).apply { builtBy(generateTask) })

    // Late bind the actual output directory
    sourceSet.java.srcDir(Callable { generateTask.outputDir })


    val cleanTask = project.tasks.create(cleanTaskName) { clean ->
      clean.description = "Clean the generated source folder"
      clean.group = BasePlugin.BUILD_GROUP
      clean.doFirst { project.delete(outputDir) }
    }

    project.configurations.getByName(sourceSet.compileConfigurationName).extendsFrom(generateConfiguration)

    project.afterEvaluate {
      project.extensions.getByType(IdeaModel::class.java)?.let { ideaModel ->
        ideaModel.module.generatedSourceDirs.add(project.file(generateTask.outputDir))
      }
    }
  }

  private fun createConfigurationExtension(project: Project, sourceSet: SourceSet, generateTaskName: String?): NamedDomainObjectContainer<GenerateSpec>? {
    val generateExt = project.container(GenerateSpec::class.java)
    if (sourceSet is HasConvention) {
      sourceSet.convention.plugins.put("net.devrieze.gradlecodegen", GenerateSourceSet(generateExt))
    } else {
      project.extensions.add(generateTaskName, generateExt)
    }
    return generateExt
  }

}

private fun<T> closure(block:(args:Array<out Any?>)->T): Closure<T> = object:Closure<T>(Unit) {

  override fun call(vararg args: Any?): T {
    return block(args)
  }
}