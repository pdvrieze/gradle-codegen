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

import kotlin.reflect.KProperty

/**
 * Implementation for the database API
 */

open class ColumnImpl<T:Any>(override val table:TableRef, override val type: ColumnType<T>, override val name: String, val notnull: Boolean?, val unique: Boolean): Column<T> {
  constructor(configuration: ColumnConfiguration<T>):
      this(table=configuration.table,
           type=configuration.type,
           name=configuration.name,
           notnull=configuration.notnull,
           unique=configuration.unique) {

  }
  override fun ref():ColumnRef<T> = this
}

open class Table(override val name: String, val cols: List<out Column<*>>, val primaryKey: List<out ColumnRef<*>>?, foreignKeys: MutableList<ForeignKey>, uniqueKeys: MutableList<List<ColumnRef<*>>>, val indices: List<out List<out ColumnRef<*>>>, val extra: String?) :TableRef{
  constructor(name:String, extra: String? = null, block:TableConfiguration.()->Unit): this(TableConfiguration(name, extra).apply(block)  )
  constructor(c:TableConfiguration):this(c.name, c.cols, c.primaryKey, c.foreignKeys, c.uniqueKeys, c.indices, c.extra)

  fun ref(): TableRef = TableRefImpl(name)

  fun field(name:String) = cols.firstOrNull {it.name==name}

  protected fun <T:Any> type(type: ColumnType<T>) = FieldAccessor<T>(type)

  operator fun <T:Any> get(thisRef: Table, property:KProperty<out Column<T>>):Column<T> {
    return field(property.name) as Column<T>
  }



  protected class FieldAccessor<T:Any>(val type: ColumnType<T>) {
    lateinit var value:Column<T>
    operator fun getValue(thisRef: Table, property:KProperty<*>):Column<T> {
      if (value==null) {
        value = type.cast(thisRef.field(property.name)?: throw IllegalArgumentException("There is no field with the given name ${property.name}"))
      }
      return value
    }
  }

}

class TableRefImpl(override val name: String) : TableRef {}




