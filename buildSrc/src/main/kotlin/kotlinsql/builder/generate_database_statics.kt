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

/**
 * Created by pdvrieze on 04/04/16.
 */

package kotlinsql.builder

import net.devrieze.gradlecodgen.GenerateImpl
import org.gradle.api.file.FileCollection
import java.io.Writer

class GenerateDatabaseBaseKt(val count:Int): GenerateImpl {
  override fun doGenerate(output: Writer, input: FileCollection?) {
    output.apply {

      appendCopyright()
      appendln()
      appendln("package uk.ac.bournemouth.util.kotlin.sql.impl.gen")
      appendln()
      appendln("import uk.ac.bournemouth.kotlinsql.Column")
      appendln("import uk.ac.bournemouth.kotlinsql.IColumnType")
      appendln("import uk.ac.bournemouth.kotlinsql.Database")
      appendln()
      appendln("open class DatabaseMethods {")
//      appendln("  companion object {")

      appendFunctionGroup("SELECT","_Select")
      appendFunctionGroup("INSERT","_Insert")

//      appendln("  }")
      appendln("}")
    }
  }

  private fun Writer.appendFunctionGroup(funName:String, className:String) {
    for (n in 1..count) {
      appendln()
  //        appendln("    @JvmStatic")
      append("    fun <")

      run {
        val indent = " ".repeat(if (n < 9) 9 else 10)
        (1..n).joinToString(",\n${indent}") { m -> "T$m:Any, S$m:IColumnType<T$m,S$m,C$m>, C$m: Column<T$m, S$m, C$m>" }.apply {
          append(this)
        }
      }
      append("> ${funName}(")
      (1..n).joinToString { m -> "col$m: C$m" }.apply { append(this) }
      appendln(")=")
      if (n == 1 && funName=="SELECT") {
        append("            Database.$className$n(")
      } else {
        append("            $className$n(")
      }
      (1..n).joinToString { m -> "col$m" }.apply { append(this) }
      appendln(")")
    }
  }
}

fun main(args:Array<String>) {
}