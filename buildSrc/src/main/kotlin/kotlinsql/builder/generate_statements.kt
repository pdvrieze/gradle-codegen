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

import org.gradle.api.file.FileCollection
import java.io.Writer

class GenerateStatementsKt(val count:Int):GenerateImpl {
  override fun doGenerate(output: Writer, input: FileCollection?) {
    output.apply {
      appendCopyright()
      appendln()
      appendln("package uk.ac.bournemouth.util.kotlin.sql.impl.gen")
      appendln()
      appendln("import uk.ac.bournemouth.kotlinsql.Column")
//      appendln("import uk.ac.bournemouth.kotlinsql.Database")
      appendln("import uk.ac.bournemouth.kotlinsql.Database.*")
      appendln("import uk.ac.bournemouth.kotlinsql.IColumnType")
      appendln("import uk.ac.bournemouth.util.kotlin.sql.DBConnection")
      appendln("import java.sql.SQLException")

      for (n in 1..count) {
        appendln()
        append("class _Statement$n<")
        (1..n).joinToString(",\n                  ") { m -> "T$m:Any, S$m:IColumnType<T$m,S$m,C$m>, C$m: Column<T$m, S$m, C$m>" }.apply { append(this) }
        append(">(${if (n==1) "" else "override val "}select:_Select$n<")
        (1..n).joinToString(",") { m -> "T$m,S$m,C$m" }.apply { append(this) }
        when(n) {
          1    -> appendln(">, where:WhereClause):_Statement1Base<T1,S1,C1>(select,where) {")
          else -> appendln(">, where:WhereClause):_StatementBase(where) {")
        }
        if (n>1) {
          appendln()
          append("  data class Result<")
          (1..n).joinTo(this) { "T$it" }
          append(">(")
          (1..n).joinTo(this) { "val col$it:T$it?" }
          appendln(")")
        }

        appendln()
        append("  fun execute(connection:DBConnection, block: (")
        (1..n).joinToString(",") {m -> "T$m?"}.apply { append(this) }
        appendln(")->Unit):Boolean {")
        appendln("    return executeHelper(connection, block) { rs, block ->")
        append("      block(")
        (1..n).joinToString(",\n${" ".repeat(12)}") { m -> "select.col$m.type.fromResultSet(rs, $m)" }.apply { append(this) }
//        if (n==1) {
//          append("select.col1.type.fromResultSet(rs, 1)")
//        } else {
//        }
        appendln(')')
        appendln("    }")
        appendln("  }")

        if (n>1) {
          appendln()
          append("  fun getSingle(connection:DBConnection)")
          append(" = getSingle(connection) { ")
          (1..n).joinTo(this,",") { "p$it" }
          append(" -> Result(")
          (1..n).joinTo(this,",") { "p$it" }
          appendln(")}")

          appendln()
          append("  fun <R>getSingle(connection:DBConnection, factory:")
          appendFactorySignature(n)
          appendln("):R? {")
          appendln("    return connection.prepareStatement(toSQL()) {")
          appendln("      setParams(this)")
          appendln("      execute { rs ->")
          appendln("        if (rs.next()) {")
          appendln("          if (!rs.isLast()) throw SQLException(\"Multiple results found, where only one or none expected\")")
            append("          factory(")
          (1..n).joinTo(this,",\n${" ".repeat(18)}") { m -> "select.col$m.type.fromResultSet(rs, $m)" }
          appendln(")")

          appendln("        } else null ")
          appendln("      }")
          appendln("    }")
          appendln("  }")
        }

        appendln()
        append("  fun <R>getList(connection: DBConnection, factory:")
        appendFactorySignature(n)
        appendln("): List<R> {")
        appendln("    val result=mutableListOf<R>()")
        append("    execute(connection) { ")
        (1..n).joinToString { "p$it" }.apply { append(this) }
        append(" -> result.add(factory(")
        (1..n).joinToString { "p$it" }.apply { append(this) }
        appendln(")) }")
        appendln("    return result")
        appendln("  }")

        appendln()

        appendln("}")
      }
    }
  }

  private fun Appendable.appendFactorySignature(n: Int) {
    append("(")
    (1..n).joinToString(",") { "T$it?" }.apply { append(this) }
    append(")->R")
  }
}