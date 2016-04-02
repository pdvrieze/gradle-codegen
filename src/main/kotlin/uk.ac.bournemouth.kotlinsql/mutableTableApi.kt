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

import java.math.BigDecimal
import uk.ac.bournemouth.kotlinsql.ColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.NumericColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.SimpleColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.CharColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.LengthCharColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.LengthColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.DecimalColumnType.*
import java.sql.Date
import java.sql.Time
import java.sql.Timestamp
import uk.ac.bournemouth.kotlinsql.AbstractColumnConfiguration.*
import uk.ac.bournemouth.kotlinsql.AbstractColumnConfiguration.AbstractNumberColumnConfiguration.*
import uk.ac.bournemouth.kotlinsql.AbstractColumnConfiguration.AbstractCharColumnConfiguration.*


/**
 * Created by pdvrieze on 01/04/16.
 */


/**
 * A base class for table declarations. Users of the code are expected to use this with a configuration closure to create
 * database tables. for typed columns to be available they need to be declared using `by [type]` or `by [name]`.
 *
 * A sample use is:
 * ```
 *    object peopleTable:[Mutable]("people", "ENGINE=InnoDB CHARSET=utf8") {
 *      val firstname by [VARCHAR]("firstname", 50)
 *      val surname by [VARCHAR]("familyname", 30) { NOT_NULL }
 *      val birthdate by [DATE]("birthdate")
 *
 *      override fun init() {
 *        [PRIMARY_KEY](firstname, familyname)
 *      }
 *    }
 * ```
 *
 * This class uses a small amount of reflection on construction to make the magic work.
 *
 * @property _cols The list of columns in the table.
 * @property _primaryKey The primary key of the table (if defined)
 * @property _foreignKeys The foreign keys defined on the table.
 * @property _uniqueKeys Unique keys / uniqueness constraints defined on the table
 * @property _indices Additional indices defined on the table
 * @property _extra Extra table configuration to be appended after the definition. This contains information such as the
 *                  engine or charset to use.
 */
// Note that the overloadResolver parameter on the primary constructor is there purely to fix overload resolving
@Suppress("NOTHING_TO_INLINE")
abstract class MutableTable private constructor(name: String?,
                                        override val _extra: String?, overloadResolver:Unit) : AbstractTable() {

  constructor(extra:String?=null): this(null, extra, Unit)
  constructor(name:String, extra: String?): this(name, extra, Unit)

  override val _name:String = if (name==null) javaClass.simpleName else name

  override val _cols: List<Column<*, *>> = mutableListOf()
  
  private var __primaryKey: List<Column<*,*>>? = null
  override val _primaryKey: List<Column<*, *>>?
    get() { doInit; return __primaryKey }
  
  private val __foreignKeys = mutableListOf<ForeignKey>()
  override val _foreignKeys: List<ForeignKey>
    get() { doInit; return __foreignKeys }

  private val __uniqueKeys= mutableListOf<List<Column<*,*>>>()
  override val _uniqueKeys: List<List<Column<*, *>>>
    get() { doInit; return __uniqueKeys }

  private val __indices = mutableListOf<List<Column<*,*>>>()
  override val _indices: List<List<Column<*, *>>>
    get() { doInit; return __indices }

  // Using lazy takes the pain out of on-demand initialisation
  private val doInit by lazy {
    init()
  }

  abstract fun init()


  private inline fun <T :Any, S: ColumnType<T,S,C>, C :Column<T, S>>
        add(column:C): Table.FieldAccessor<T,S, C> {
    return column.apply { (_cols as MutableList<Column<*,*>>).add(this)}.let{ name(column.name, it.type) }
  }

  private inline fun <T :Any, S: ColumnType<T,S,C>, CONF_T : AbstractColumnConfiguration<T, S, C, CONF_T>, C :Column<T, S>>
        CONF_T.add(block: CONF_T.() ->Unit): Table.FieldAccessor<T,S, C> {
    // Use the name delegator to prevent access issues.
    return add(apply(block).newColumn())
  }

  // @formatter:off
  protected fun BIT(name:String, block: NormalColumnConfiguration<Boolean, BIT_T>.() -> Unit) = NormalColumnConfiguration( this, name, BIT_T).add( block)
  protected fun BIT(name: String, length: Int, block: BaseLengthColumnConfiguration<Array<Boolean>, BITFIELD_T, LengthColumn<Array<Boolean>, BITFIELD_T>>.() -> Unit) = LengthColumnConfiguration( this, name, BITFIELD_T, length).add( block)
  protected fun TINYINT(name: String, block: NumberColumnConfiguration<Byte, TINYINT_T>.() -> Unit) = NumberColumnConfiguration( this, name, TINYINT_T).add( block)
  protected fun SMALLINT(name: String, block: NumberColumnConfiguration<Short, SMALLINT_T>.() -> Unit) = NumberColumnConfiguration( this, name, SMALLINT_T).add( block)
  protected fun MEDIUMINT(name: String, block: NumberColumnConfiguration<Int, MEDIUMINT_T>.() -> Unit) = NumberColumnConfiguration( this, name, MEDIUMINT_T).add( block)
  protected fun INT(name: String, block: NumberColumnConfiguration<Int, INT_T>.() -> Unit) = NumberColumnConfiguration( this, name, INT_T).add( block)
  protected fun BIGINT(name: String, block: NumberColumnConfiguration<Long, BIGINT_T>.() -> Unit) = NumberColumnConfiguration( this, name, BIGINT_T).add( block)
  protected fun FLOAT(name: String, block: NumberColumnConfiguration<Float, FLOAT_T>.() -> Unit) = NumberColumnConfiguration( this, name, FLOAT_T).add( block)
  protected fun DOUBLE(name: String, block: NumberColumnConfiguration<Double, DOUBLE_T>.() -> Unit) = NumberColumnConfiguration( this, name, DOUBLE_T).add( block)
  protected fun DECIMAL(name: String, precision: Int = -1, scale: Int = -1, block: DecimalColumnConfiguration<BigDecimal, DECIMAL_T>.() -> Unit) = DecimalColumnConfiguration( this, name, DECIMAL_T, precision, scale).add( block)
  protected fun NUMERIC(name: String, precision: Int = -1, scale: Int = -1, block: DecimalColumnConfiguration<BigDecimal, NUMERIC_T>.() -> Unit) = DecimalColumnConfiguration( this, name, NUMERIC_T, precision, scale).add( block)
  protected fun DATE(name: String, block: NormalColumnConfiguration<Date, DATE_T>.() -> Unit) = NormalColumnConfiguration( this, name, DATE_T).add( block)
  protected fun TIME(name: String, block: NormalColumnConfiguration<Time, TIME_T>.() -> Unit) = NormalColumnConfiguration( this, name, TIME_T).add( block)
  protected fun TIMESTAMP(name: String, block: NormalColumnConfiguration<Timestamp, TIMESTAMP_T>.() -> Unit) = NormalColumnConfiguration( this, name, TIMESTAMP_T).add( block)
  protected fun DATETIME(name: String, block: NormalColumnConfiguration<Timestamp, DATETIME_T>.() -> Unit) = NormalColumnConfiguration( this, name, DATETIME_T).add( block)
  protected fun YEAR(name: String, block: NormalColumnConfiguration<Date, YEAR_T>.() -> Unit) = NormalColumnConfiguration( this, name, YEAR_T).add( block)
  protected fun CHAR(name: String, length: Int = -1, block: LengthCharColumnConfiguration<String, CHAR_T>.() -> Unit) = LengthCharColumnConfiguration( this, name, CHAR_T, length).add( block)
  protected fun VARCHAR(name: String, length: Int, block: LengthCharColumnConfiguration<String, VARCHAR_T>.() -> Unit) = LengthCharColumnConfiguration( this, name, VARCHAR_T, length).add( block)
  protected fun BINARY(name: String, length: Int, block: BaseLengthColumnConfiguration<ByteArray, BINARY_T, LengthColumn<ByteArray, BINARY_T>>.() -> Unit) = LengthColumnConfiguration( this, name, BINARY_T, length).add( block)
  protected fun VARBINARY(name: String, length: Int, block: BaseLengthColumnConfiguration<ByteArray, VARBINARY_T, LengthColumn<ByteArray, VARBINARY_T>>.() -> Unit) = LengthColumnConfiguration( this, name, VARBINARY_T, length).add( block)
  protected fun TINYBLOB(name: String, block: NormalColumnConfiguration<ByteArray, TINYBLOB_T>.() -> Unit) = NormalColumnConfiguration( this, name, TINYBLOB_T).add( block)
  protected fun BLOB(name: String, block: NormalColumnConfiguration<ByteArray, BLOB_T>.() -> Unit) = NormalColumnConfiguration( this, name, BLOB_T).add( block)
  protected fun MEDIUMBLOB(name: String, block: NormalColumnConfiguration<ByteArray, MEDIUMBLOB_T>.() -> Unit) = NormalColumnConfiguration( this, name, MEDIUMBLOB_T).add( block)
  protected fun LONGBLOB(name: String, block: NormalColumnConfiguration<ByteArray, LONGBLOB_T>.() -> Unit) = NormalColumnConfiguration( this, name, LONGBLOB_T).add( block)
  protected fun TINYTEXT(name: String, block: CharColumnConfiguration<String, TINYTEXT_T>.() -> Unit) = CharColumnConfiguration( this, name, TINYTEXT_T).add( block)
  protected fun TEXT(name: String, block: CharColumnConfiguration<String, TEXT_T>.() -> Unit) = CharColumnConfiguration( this, name, TEXT_T).add( block)
  protected fun MEDIUMTEXT(name: String, block: CharColumnConfiguration<String, MEDIUMTEXT_T>.() -> Unit) = CharColumnConfiguration( this, name, MEDIUMTEXT_T).add( block)
  protected fun LONGTEXT(name: String, block: CharColumnConfiguration<String, LONGTEXT_T>.() -> Unit) = CharColumnConfiguration( this, name, LONGTEXT_T).add( block)

  /* Versions without configuration closure */
  protected fun BIT(name: String) = NormalColumnConfiguration(this, name, BIT_T).add({})
  protected fun BIT(name:String, length:Int) = LengthColumnConfiguration(this, name, BITFIELD_T, length).add({})
  protected fun TINYINT(name: String) = NumberColumnConfiguration(this, name, TINYINT_T).add({})
  protected fun SMALLINT(name: String) = NumberColumnConfiguration(this, name, SMALLINT_T).add({})
  protected fun MEDIUMINT(name: String) = NumberColumnConfiguration(this, name, MEDIUMINT_T).add({})
  protected fun INT(name: String) = NumberColumnConfiguration(this, name, INT_T).add({})
  protected fun BIGINT(name:String) = NumberColumnConfiguration(this, name, BIGINT_T).add({})
  protected fun FLOAT(name: String) = NumberColumnConfiguration(this, name, FLOAT_T).add({})
  protected fun DOUBLE(name: String) = NumberColumnConfiguration(this, name, DOUBLE_T).add({})
  protected fun DECIMAL(name: String, precision:Int=-1, scale:Int=-1) = DecimalColumnConfiguration(this, name, DECIMAL_T, precision, scale).add({})
  protected fun NUMERIC(name: String, precision: Int = -1, scale: Int = -1) = DecimalColumnConfiguration(this, name, NUMERIC_T, precision, scale).add({})
  protected fun DATE(name: String) = NormalColumnConfiguration(this, name, DATE_T).add({})
  protected fun TIME(name: String) = NormalColumnConfiguration(this, name, TIME_T).add({})
  protected fun TIMESTAMP(name: String) = NormalColumnConfiguration(this, name, TIMESTAMP_T).add({})
  protected fun DATETIME(name: String) = NormalColumnConfiguration(this, name, DATETIME_T).add({})
  protected fun YEAR(name: String) = NormalColumnConfiguration(this, name, YEAR_T).add({})
  protected fun CHAR(name: String, length: Int = -1) = LengthCharColumnConfiguration(this, name, CHAR_T, length).add({})
  protected fun VARCHAR(name: String, length: Int) = LengthCharColumnConfiguration(this, name, VARCHAR_T, length).add({})

  protected fun BINARY(name: String, length: Int) = LengthColumnConfiguration(this, name, BINARY_T, length).add({})
  protected fun VARBINARY(name: String, length: Int) = LengthColumnConfiguration(this, name, VARBINARY_T, length).add({})
  protected fun TINYBLOB(name: String) = NormalColumnConfiguration(this, name, TINYBLOB_T).add({})
  protected fun BLOB(name: String) = NormalColumnConfiguration(this, name, BLOB_T).add({})
  protected fun MEDIUMBLOB(name: String) = NormalColumnConfiguration(this, name, MEDIUMBLOB_T).add({})
  protected fun LONGBLOB(name: String) = NormalColumnConfiguration(this, name, LONGBLOB_T).add({})
  protected fun TINYTEXT(name: String) = CharColumnConfiguration(this, name, TINYTEXT_T).add({})
  protected fun TEXT(name: String) = CharColumnConfiguration(this, name, TEXT_T).add({})
  protected fun MEDIUMTEXT(name:String) = CharColumnConfiguration(this, name, MEDIUMTEXT_T).add({})
  protected fun LONGTEXT(name: String) = CharColumnConfiguration(this, name, LONGTEXT_T).add({})

  /* When there is no body, the configuration subtype does not matter */
  protected fun <T:Any, S:ColumnType<T,S,C>, C:Column<T,S>>reference(other: C): Table.FieldAccessor<T,S,C> {
    return add(other.copyConfiguration(owner = this).newColumn() as C)
  }

  protected fun <T:Any, S:ColumnType<T,S,C>, C:Column<T,S>>reference(newName:String, other: C): Table.FieldAccessor<T,S,C> {
    return add(other.copyConfiguration(newName = newName, owner = this).newColumn() as C)
  }

  /* Otherwise, the various types need to be distinguished. The different subtypes of column are needed for overload resolution */
  protected fun <T:Any, S:DecimalColumnType<T,S>>reference(other: DecimalColumn<T,S>, block: DecimalColumnConfiguration<T,S>.() -> Unit) = other.copyConfiguration(null, this).add(block)
  protected fun <T:Any, S:LengthCharColumnType<T,S>>reference(other: LengthCharColumn<T,S>, block: LengthCharColumnConfiguration<T,S>.() -> Unit) = other.copyConfiguration(null, this).add(block)
  protected fun <T:Any, S:CharColumnType<T,S>>reference(other: CharColumn<T,S>, block: CharColumnConfiguration<T,S>.() -> Unit) = other.copyConfiguration(null, this).add(block)
  protected fun <T:Any, S:SimpleColumnType<T,S>>reference(other: SimpleColumn<T,S>, block: NormalColumnConfiguration<T,S>.() -> Unit) = other.copyConfiguration(null, this).add(block)
  protected fun <T:Any, S:LengthColumnType<T,S>>reference(other: LengthColumn<T,S>, block: LengthColumnConfiguration<T,S>.() -> Unit) = other.copyConfiguration(null, this).add(block)
  protected fun <T:Any, S:NumericColumnType<T,S>>reference(other: NumericColumn<T,S>, block: NumberColumnConfiguration<T,S>.() -> Unit) = other.copyConfiguration(null, this).add(block)


  /* Otherwise, the various types need to be distinguished. The different subtypes of column are needed for overload resolution */
  protected fun <T:Any, S:DecimalColumnType<T,S>>reference(newName:String, other: DecimalColumn<T,S>, block: DecimalColumnConfiguration<T,S>.() -> Unit) = other.copyConfiguration(newName, this).add(block)
  protected fun <T:Any, S:LengthCharColumnType<T,S>>reference(newName:String, other: LengthCharColumn<T,S>, block: LengthCharColumnConfiguration<T,S>.() -> Unit) = other.copyConfiguration(newName, this).add(block)
  protected fun <T:Any, S:CharColumnType<T,S>>reference(newName:String, other: CharColumn<T,S>, block: CharColumnConfiguration<T,S>.() -> Unit) = other.copyConfiguration(newName, this).add(block)
  protected fun <T:Any, S:SimpleColumnType<T,S>>reference(newName:String, other: SimpleColumn<T,S>, block: NormalColumnConfiguration<T,S>.() -> Unit) = other.copyConfiguration(newName, this).add(block)
  protected fun <T:Any, S:LengthColumnType<T,S>>reference(newName:String, other: LengthColumn<T,S>, block: LengthColumnConfiguration<T,S>.() -> Unit) = other.copyConfiguration(newName, this).add(block)
  protected fun <T:Any, S:NumericColumnType<T,S>>reference(newName:String, other: NumericColumn<T,S>, block: NumberColumnConfiguration<T,S>.() -> Unit) = other.copyConfiguration(newName, this).add(block)

  protected fun INDEX(col1: ColumnRef<*, *>, vararg cols: ColumnRef<*,*>) { __indices.add(mutableListOf(resolve(col1)).apply { addAll(resolve(cols)) })}
  protected fun UNIQUE(col1: ColumnRef<*,*>, vararg cols: ColumnRef<*,*>) { __uniqueKeys.add(mutableListOf(resolve(col1)).apply { addAll(resolve(cols)) })}
  protected fun PRIMARY_KEY(col1: ColumnRef<*,*>, vararg cols: ColumnRef<*,*>) { __primaryKey = mutableListOf(resolve(col1)).apply { addAll(resolve(cols)) }}

  class __FOREIGN_KEY__6<T1:Any, S1: BaseColumnType<T1,S1>, T2:Any, S2: BaseColumnType<T2,S2>, T3:Any, S3: BaseColumnType<T3,S3>, T4:Any, S4: BaseColumnType<T4,S4>, T5:Any, S5: BaseColumnType<T5,S5>, T6:Any, S6: BaseColumnType<T6,S6>>(val table: MutableTable, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>, val col3:ColumnRef<T3, S3>, val col4:ColumnRef<T4, S4>, val col5:ColumnRef<T5, S5>, val col6:ColumnRef<T6, S6>) {
    fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>, ref3:ColumnRef<T3, S3>, ref4:ColumnRef<T4, S4>, ref5:ColumnRef<T5, S5>, ref6:ColumnRef<T6, S6>) {
      (table.__foreignKeys).add(ForeignKey(listOf(col1, col2, col3, col4, col5, col6), ref1.table, listOf(ref1, ref2, ref3, ref4, ref5, ref6).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, S1: BaseColumnType<T1,S1>, T2:Any, S2: BaseColumnType<T2,S2>, T3:Any, S3: BaseColumnType<T3,S3>, T4:Any, S4: BaseColumnType<T4,S4>, T5:Any, S5: BaseColumnType<T5,S5>, T6:Any, S6: BaseColumnType<T6,S6>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>, col3:ColumnRef<T3, S3>, col4: ColumnRef<T4, S4>, col5:ColumnRef<T5, S5>, col6:ColumnRef<T6, S6>) =
        __FOREIGN_KEY__6(this, col1,col2,col3,col4,col5,col6)


  class __FOREIGN_KEY__5<T1:Any, S1: BaseColumnType<T1,S1>, T2:Any, S2: BaseColumnType<T2,S2>, T3:Any, S3: BaseColumnType<T3,S3>, T4:Any, S4: BaseColumnType<T4,S4>, T5:Any, S5: BaseColumnType<T5,S5>>(val table: MutableTable, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>, val col3:ColumnRef<T3, S3>, val col4:ColumnRef<T4, S4>, val col5:ColumnRef<T5, S5>) {
    fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>, ref3:ColumnRef<T3, S3>, ref4:ColumnRef<T4, S4>, ref5:ColumnRef<T5, S5>) {
      (table.__foreignKeys).add(ForeignKey(listOf(col1, col2, col3, col4, col5), ref1.table, listOf(ref1, ref2, ref3, ref4, ref5).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, S1: BaseColumnType<T1,S1>, T2:Any, S2: BaseColumnType<T2,S2>, T3:Any, S3: BaseColumnType<T3,S3>, T4:Any, S4: BaseColumnType<T4,S4>, T5:Any, S5: BaseColumnType<T5,S5>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>, col3:ColumnRef<T3, S3>, col4: ColumnRef<T4, S4>, col5:ColumnRef<T5, S5>) =
        __FOREIGN_KEY__5(this, col1,col2,col3,col4,col5)

  class __FOREIGN_KEY__4<T1:Any, S1: BaseColumnType<T1,S1>, T2:Any, S2: BaseColumnType<T2,S2>, T3:Any, S3: BaseColumnType<T3,S3>, T4:Any, S4: BaseColumnType<T4,S4>>(val table: MutableTable, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>, val col3:ColumnRef<T3, S3>, val col4:ColumnRef<T4, S4>) {
    fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>, ref3:ColumnRef<T3, S3>, ref4:ColumnRef<T4, S4>) {
      (table.__foreignKeys).add(ForeignKey(listOf(col1, col2, col3, col4), ref1.table, listOf(ref1, ref2, ref3, ref4)))
    }
  }

  inline fun <T1:Any, S1: BaseColumnType<T1,S1>, T2:Any, S2: BaseColumnType<T2,S2>, T3:Any, S3: BaseColumnType<T3,S3>, T4:Any, S4: BaseColumnType<T4,S4>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>, col3:ColumnRef<T3, S3>, col4: ColumnRef<T4, S4>) =
        __FOREIGN_KEY__4(this, col1,col2,col3,col4)

  class __FOREIGN_KEY__3<T1:Any, S1: BaseColumnType<T1,S1>, T2:Any, S2: BaseColumnType<T2,S2>, T3:Any, S3: BaseColumnType<T3,S3>>(val table: MutableTable, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>, val col3:ColumnRef<T3, S3>) {
    fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>, ref3:ColumnRef<T3, S3>) {
      (table.__foreignKeys).add(ForeignKey(listOf(col1, col2, col3), ref1.table, listOf(ref1, ref2, ref3).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, S1: BaseColumnType<T1,S1>, T2:Any, S2: BaseColumnType<T2,S2>, T3:Any, S3: BaseColumnType<T3,S3>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>, col3:ColumnRef<T3, S3>) =
        __FOREIGN_KEY__3(this, col1,col2,col3)

  class __FOREIGN_KEY__2<T1:Any, S1: BaseColumnType<T1,S1>, T2:Any, S2: BaseColumnType<T2,S2>>(val table: MutableTable, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>) {
    fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>) {
      (table.__foreignKeys).add(ForeignKey(listOf(col1, col2), ref1.table, listOf(ref1, ref2).apply { require(ref2.table==ref1.table) }))
    }
  }

  inline fun <T1:Any, S1: BaseColumnType<T1,S1>, T2:Any, S2: BaseColumnType<T2,S2>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>) =
        __FOREIGN_KEY__2(this, col1,col2)

  class __FOREIGN_KEY__1<T1:Any, S1: BaseColumnType<T1,S1>>(val table: MutableTable, val col1:ColumnRef<T1, S1>) {
    fun REFERENCES(ref1:ColumnRef<T1, S1>) {
      (table.__foreignKeys).add(ForeignKey(listOf(col1), ref1.table, listOf(ref1)))
    }
  }

  inline fun <T1:Any, S1: BaseColumnType<T1,S1>> FOREIGN_KEY(col1: ColumnRef<T1, S1>) =
        __FOREIGN_KEY__1(this, col1)


  // @formatter:on
}
