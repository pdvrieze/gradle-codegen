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

open class ColumnImpl<T:Any, S: ColumnType<T, S>>(
      override val table: TableRef,
      override val type: S,
      override val name: String,
      override val notnull: Boolean?,
      override val unique: Boolean,
      override val autoincrement: Boolean,
      override val default: T?,
      override val comment: String?,
      override val columnFormat: ColumnConfiguration.ColumnFormat?,
      override val storageFormat: ColumnConfiguration.StorageFormat?,
      override val references: ColsetRef?) : Column<T, S> {
  constructor(configuration: ColumnConfiguration<T, S>):
      this(table=configuration.table,
           type=configuration.type,
           name=configuration.name,
           notnull=configuration.notnull,
           unique=configuration.unique,
           autoincrement=configuration.autoincrement,
           default=configuration.default,
           comment=configuration.comment,
           columnFormat=configuration.columnFormat,
           storageFormat=configuration.storageFormat,
           references=configuration.references)
  
  override fun ref():ColumnRef<T, S> = this
}

class TableRefImpl(override val _name: String) : TableRef {}


abstract class AbstractTable: Table {

  companion object {

    fun List<Column<*, *>>.resolve(ref: ColumnRef<*,*>) = find { it.name == ref.name } ?: throw java.util.NoSuchElementException(
          "No column with the name ${ref.name} could be found")

    fun List<Column<*, *>>.resolve(refs: List<ColumnRef<*,*>>) = refs.map { resolve(it) }

    fun List<Column<*, *>>.resolve(refs: Array<out ColumnRef<*,*>>) = refs.map { resolve(it) }

  }

  override fun resolve(ref: ColumnRef<*,*>) : Column<*, *> = (_cols.find {it.name==ref.name}) !!

  override fun ref(): TableRef = TableRefImpl(_name)

  override fun field(name:String) = _cols.firstOrNull {it.name==name}

  operator fun <T:Any, S: ColumnType<T, S>> getValue(thisRef: ImmutableTable, property: KProperty<*>): Column<T, S> {
    return field(property.name) as Column<T, S>
  }

  open protected class TypeFieldAccessor<T:Any, S: ColumnType<T, S>>(val type: ColumnType<T, S>): Table.FieldAccessor<T, S> {
    lateinit var value: Column<T, S>
    open fun name(property: kotlin.reflect.KProperty<*>) = property.name
    override operator fun getValue(thisRef: Table, property: kotlin.reflect.KProperty<*>): Column<T, S> {
      if (value==null) {
        value = type.cast(thisRef.field(property.name)?: throw IllegalArgumentException("There is no field with the given name ${property.name}"))
      }
      return value
    }
  }

  /** Property delegator to access database columns by name and type. */
  protected fun <T:Any, S: ColumnType<T, S>> name(name:String, type: ColumnType<T, S>) = NamedFieldAccessor<T, S>(
        name,
        type)

  final protected class NamedFieldAccessor<T:Any, S: ColumnType<T, S>>(val name:String, type: ColumnType<T, S>): TypeFieldAccessor<T, S>(type) {
    override fun name(property: kotlin.reflect.KProperty<*>): String = this.name
  }

}

