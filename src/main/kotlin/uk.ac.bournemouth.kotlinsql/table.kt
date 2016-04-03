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

package uk.ac.bournemouth.kotlinsql

import uk.ac.bournemouth.util.kotlin.sql.StatementHelper
import java.util.*

/**
 * Created by pdvrieze on 03/04/16.
 */

/**
 * A interface for tables. The properties have underscored names to reduce conflicts with members.
 *
 * @property _cols The list of columns in the table.
 * @property _primaryKey The primary key of the table (if defined)
 * @property _foreignKeys The foreign keys defined on the table.
 * @property _uniqueKeys Unique keys / uniqueness constraints defined on the table
 * @property _indices Additional indices defined on the table
 * @property _extra Extra table configuration to be appended after the definition. This contains information such as the
 *                  engine or charset to use.
 */
interface Table:TableRef {
  val _cols: List<Column<*, *, *>>
  val _primaryKey: List<Column<*, *, *>>?
  val _foreignKeys: List<ForeignKey>
  val _uniqueKeys: List<List<Column<*, *, *>>>
  val _indices: List<List<Column<*, *, *>>>
  val _extra: String?

  fun column(name:String): Column<*,*,*>?
  fun ref(): TableRef
  fun resolve(ref: ColumnRef<*,*,*>): Column<*,*,*>

  interface FieldAccessor<T:Any, S: ColumnType<T,S,C>, C:Column<T,S,C>> {
    operator fun getValue(thisRef: Table, property: kotlin.reflect.KProperty<*>): C
  }

  fun appendDDL(appendable: Appendable)

  abstract  class WhereClause {

    abstract fun setParameters(statementHelper: StatementHelper, first:Int=1):Int

    abstract fun toSQL(): String
  }

  private class EqWhereClause<T:Any>(val ref:ColumnRef<T,*,*>,val value: T?):WhereClause() {
    override fun toSQL(): String {
      return "`${ref.name}` = ?"
    }

    override fun setParameters(statementHelper: StatementHelper, first: Int):Int {
      statementHelper.setParam_(first, value)
      return first+1
    }
  }

  class _Where {

    //    @JvmStatic
    infix fun <T : Any> ColumnRef<T, *, *>.eq(value: T): WhereClause = EqWhereClause(this, value)
  }

  interface Statement<T:Any, S:IColumnType<T,S,C>, C:Column<T,S,C>> {
    val select:Select

    fun toSQL(): String
  }

  class _Statement1<T:Any, S:IColumnType<T,S,C>, C:Column<T,S,C>>(override val select:_Select1<T,S,C>, val where:WhereClause):Statement<T,S,C> {
    override fun toSQL(): String {
      return "${select.toSQL()} WHERE ${where.toSQL()}"
    }
  }

  interface Select {
    fun toSQL(): String
  }

  class _Select1<T1:Any, S1:IColumnType<T1,S1,C1>, C1: Column<T1, S1,C1>>(val col1:C1):Select {

    fun WHERE(config: _Where.() -> WhereClause):_Statement1<T1,S1, C1> {
      return _Statement1(this, _Where().config())
    }

    override fun toSQL(): String {
      return "SELECT `${col1.name}` FROM `${col1.table._name}`"
    }
  }


  fun <T:Any, S:ColumnType<T,S,C>, C: Column<T, S,C>> SELECT(col1: C)= _Select1(col1)

}

operator fun Table.get(name:String) = this.column(name) ?: throw NoSuchElementException("The column with the name ${name} does not exist")
