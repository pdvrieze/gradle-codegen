/*
 * Copyright (c) 2016.
 *
 * This file is part of ProcessManager.
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

package uk.ac.bournemouth.util.kotlin.sql

import java.sql.*
import java.util.*
import java.util.concurrent.Executor
import javax.sql.DataSource

class StatementHelper constructor(val statement: PreparedStatement) : PreparedStatement by statement {
  inline fun <R> raw(block: (PreparedStatement) -> R): R = block(statement)

  @Deprecated("Use withResultSet")
  override fun getResultSet(): ResultSet? {
    throw UnsupportedOperationException()
  }

  @Deprecated("Use withGeneratedKeys")
  override fun getGeneratedKeys(): ResultSet? {
    throw UnsupportedOperationException()
  }

  @Deprecated("Use Execute with lambda instead", replaceWith = ReplaceWith("execute"))
  override fun executeQuery(p0: String?): ResultSet? {
    throw UnsupportedOperationException()
  }

  val warningsIt: Iterator<SQLWarning>
    get() = object : AbstractIterator<SQLWarning>() {
      override fun computeNext() {
        val w = statement.warnings
        if (w != null) {
          setNext(w)
        } else {
          done()
        }
      }
    }


  inline fun <reified T> setParam_(index: Int, value: T) = when (value) {
    is Int -> setParam(index, value)
    is Long -> setParam(index, value)
    is String -> setParam(index, value)
    is Boolean -> setParam(index, value)
    is Byte -> setParam(index, value)
    is Short -> setParam(index, value)
    else -> throw UnsupportedOperationException("Not possible to set this value")
  }


  fun setParam(index: Int, value: Int?) = if (value == null) setNull(index, Types.INTEGER) else setInt(index, value)
  fun setParam(index: Int, value: Long?) = if (value == null) setNull(index, Types.BIGINT) else setLong(index, value)
  fun setParam(index: Int, value: String?) = if (value == null) setNull(index, Types.VARCHAR) else setString(index, value)
  fun setParam(index: Int, value: Boolean?) = if (value == null) setNull(index, Types.BOOLEAN) else setBoolean(index, value)
  fun setParam(index: Int, value: Byte?) = if (value == null) setNull(index, Types.TINYINT) else setByte(index, value)
  fun setParam(index: Int, value: Short?) = if (value == null) setNull(index, Types.SMALLINT) else setShort(index, value)

  @Suppress("unused")
  class ParamHelper_(val sh: StatementHelper) {

    var index = 2
    inline operator fun Int?.unaryPlus(): Unit = sh.setParam(index++, this)
    inline operator fun Long?.unaryPlus(): Unit = sh.setParam(index++, this)
    inline operator fun String?.unaryPlus(): Unit = sh.setParam(index++, this)
    inline operator fun Boolean?.unaryPlus(): Unit = sh.setParam(index++, this)
    inline operator fun Byte?.unaryPlus(): Unit = sh.setParam(index++, this)
    inline operator fun Short?.unaryPlus(): Unit = sh.setParam(index++, this)

    inline operator fun plus(value: Int?): ParamHelper_ { sh.setParam(index++, value); return this }
    inline operator fun plus(value: Long?): ParamHelper_ { sh.setParam(index++, value); return this }
    inline operator fun plus(value: String?): ParamHelper_ { sh.setParam(index++, value); return this }
    inline operator fun plus(value: Boolean?): ParamHelper_ { sh.setParam(index++, value); return this }
    inline operator fun plus(value: Byte?): ParamHelper_ { sh.setParam(index++, value); return this }
    inline operator fun plus(value: Short?): ParamHelper_ { sh.setParam(index++, value); return this }
  }

//  inline fun <R> params(block: ParamHelper_.() -> R) = ParamHelper_(this).block()

  inline fun params(value:Int?):ParamHelper_ { setParam(1, value); return ParamHelper_(this) }
  inline fun params(value:Long?):ParamHelper_ { setParam(1, value); return ParamHelper_(this) }
  inline fun params(value:String?):ParamHelper_ { setParam(1, value); return ParamHelper_(this) }
  inline fun params(value:Boolean?):ParamHelper_ { setParam(1, value); return ParamHelper_(this) }
  inline fun params(value:Byte?):ParamHelper_ { setParam(1, value); return ParamHelper_(this) }
  inline fun params(value:Short?):ParamHelper_ { setParam(1, value); return ParamHelper_(this) }

  inline fun <R> withResultSet(block: (ResultSet) -> R) = statement.getResultSet().use(block)

  inline fun <R> withGeneratedKeys(block: (ResultSet) -> R) = statement.generatedKeys.use(block)

  inline fun <R> execute(block: (ResultSet) -> R) = statement.executeQuery().use(block)

  inline fun <R> executeEach(block: (ResultSet) -> R): List<R> = execute { resultSet ->
    ArrayList<R>().apply {
      add(block(resultSet))
    }
  }

  fun stringResult(): String? {
    execute { rs ->
      if(rs.next()) {
        return rs.getString(1)
      }
      return null
    }
  }

  inline fun executeHasRows(): Boolean = execute() && withResultSet { it.next() }
}

public inline fun <T : Statement, R> T.use(block: (T) -> R) = useHelper({ it.close() }, block)
public inline fun <T : ResultSet, R> T.use(block: (T) -> R) = useHelper({ it.close() }, block)
