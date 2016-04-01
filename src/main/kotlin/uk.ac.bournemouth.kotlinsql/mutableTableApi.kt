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
 *      init {
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
@Suppress("NOTHING_TO_INLINE")
abstract class MutableTable constructor(override val _name: String,
                                                override val _extra: String?) : AbstractTable() {

  override val _cols: List<Column<*, *>> = mutableListOf()
  var __primaryKey: List<Column<*,*>>? = null
  override val _primaryKey: List<Column<*, *>>?
    get() = __primaryKey
  override val _foreignKeys: List<ForeignKey> = mutableListOf()
  override val _uniqueKeys: List<List<Column<*, *>>> = mutableListOf()
  override val _indices: List<List<Column<*, *>>> = mutableListOf()


  inline fun <V:Any, S: ColumnType<V,S>,T:ColumnConfiguration<V, S>> T.add(block: T.() ->Unit): Table.FieldAccessor<V, S> = ColumnImpl(apply(block)).apply { (_cols as MutableList<Column<*,*>>).add(this)}.let{ NamedFieldAccessor(it.name, it.type)}

  inline fun BIT(name:String, block: ColumnConfiguration<Boolean, ColumnType.BIT_T>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.BIT_T).add(block)
  inline fun BIT(name:String, length:Int, block: ColumnConfiguration<Array<Boolean>, ColumnType.BITFIELD_T>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.BITFIELD_T).add(block)
  inline fun TINYINT(name:String, block: NumberColumnConfiguration<Byte, ColumnType.TINYINT_T>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.TINYINT_T).add(block)
  inline fun SMALLINT(name:String, block: NumberColumnConfiguration<Short, ColumnType.SMALLINT_T>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.SMALLINT_T).add(block)
  inline fun MEDIUMINT(name:String, block: NumberColumnConfiguration<Int, ColumnType.MEDIUMINT_T>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.MEDIUMINT_T).add(block)
  inline fun INT(name:String, block: NumberColumnConfiguration<Int, ColumnType.INT_T>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.INT_T).add(block)
  inline fun BIGINT(name:String, block: NumberColumnConfiguration<Long, ColumnType.BIGINT_T>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.BIGINT_T).add(block)
  inline fun FLOAT(name:String, block: NumberColumnConfiguration<Float, ColumnType.FLOAT_T>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.FLOAT_T).add(block)
  inline fun DOUBLE(name:String, block: NumberColumnConfiguration<Double, ColumnType.DOUBLE_T>.() -> Unit) = NumberColumnConfiguration(this, name, ColumnType.DOUBLE_T).add(block)
  inline fun DECIMAL(name:String, precision:Int=-1, scale:Int=-1, block: DecimalColumnConfiguration<BigDecimal, ColumnType.DECIMAL_T>.() -> Unit) = DecimalColumnConfiguration(this, name, ColumnType.DECIMAL_T, precision, scale).add(block)
  inline fun NUMERIC(name:String, precision:Int=-1, scale:Int=-1, block: DecimalColumnConfiguration<BigDecimal, ColumnType.NUMERIC_T>.() -> Unit) = DecimalColumnConfiguration(this, name, ColumnType.NUMERIC_T, precision, scale).add(block)
  inline fun DATE(name:String, block: ColumnConfiguration<java.sql.Date, ColumnType.DATE_T>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.DATE_T).add(block)
  inline fun TIME(name:String, block: ColumnConfiguration<java.sql.Time, ColumnType.TIME_T>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.TIME_T).add(block)
  inline fun TIMESTAMP(name:String, block: ColumnConfiguration<java.sql.Timestamp, ColumnType.TIMESTAMP_T>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.TIMESTAMP_T).add(block)
  inline fun DATETIME(name:String, block: ColumnConfiguration<java.sql.Timestamp, ColumnType.DATETIME_T>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.DATETIME_T).add(block)
  inline fun YEAR(name:String, block: ColumnConfiguration<java.sql.Date, ColumnType.YEAR_T>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.YEAR_T).add(block)
  inline fun CHAR(name:String, length:Int = -1, block: LengthCharColumnConfiguration<String, ColumnType.CHAR_T>.() -> Unit) = LengthCharColumnConfiguration(this, name, ColumnType.CHAR_T, length).add(block)
  inline fun VARCHAR(name:String, length:Int, block: LengthCharColumnConfiguration<String, ColumnType.VARCHAR_T>.() -> Unit) = LengthCharColumnConfiguration(this, name, ColumnType.VARCHAR_T, length).add(block)
  inline fun BINARY(name:String, length:Int, block: LengthColumnConfiguration<ByteArray, ColumnType.BINARY_T>.() -> Unit) = SimpleLengthColumnConfiguration(this, name, ColumnType.BINARY_T, length).add(block)
  inline fun VARBINARY(name:String, length:Int, block: LengthColumnConfiguration<ByteArray, ColumnType.VARBINARY_T>.() -> Unit) = SimpleLengthColumnConfiguration(this, name, ColumnType.VARBINARY_T, length).add(block)
  inline fun TINYBLOB(name:String, block: ColumnConfiguration<ByteArray, ColumnType.TINYBLOB_T>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.TINYBLOB_T).add(block)
  inline fun BLOB(name:String, block: ColumnConfiguration<ByteArray, ColumnType.BLOB_T>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.BLOB_T).add(block)
  inline fun MEDIUMBLOB(name:String, block: ColumnConfiguration<ByteArray, ColumnType.MEDIUMBLOB_T>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.MEDIUMBLOB_T).add(block)
  inline fun LONGBLOB(name:String, block: ColumnConfiguration<ByteArray, ColumnType.LONGBLOB_T>.() -> Unit) = ColumnConfiguration(this, name, ColumnType.LONGBLOB_T).add(block)
  inline fun TINYTEXT(name:String, block: CharColumnConfiguration<String, ColumnType.TINYTEXT_T>.() -> Unit) = CharColumnConfiguration(this, name, ColumnType.TINYTEXT_T).add(block)
  inline fun TEXT(name:String, block: CharColumnConfiguration<String, ColumnType.TEXT_T>.() -> Unit) = CharColumnConfiguration(this, name, ColumnType.TEXT_T).add(block)
  inline fun MEDIUMTEXT(name:String, block: CharColumnConfiguration<String, ColumnType.MEDIUMTEXT_T>.() -> Unit) = CharColumnConfiguration(this, name, ColumnType.MEDIUMTEXT_T).add(block)
  inline fun LONGTEXT(name:String, block: CharColumnConfiguration<String, ColumnType.LONGTEXT_T>.() -> Unit) = CharColumnConfiguration(this, name, ColumnType.LONGTEXT_T).add(block)

  /* Versions without configuration closure */
  inline fun BIT(name:String) = ColumnConfiguration(this, name, ColumnType.BIT_T).add({})
  inline fun BIT(name:String, length:Int) = ColumnConfiguration(this, name, ColumnType.BITFIELD_T).add({})
  inline fun TINYINT(name:String) = NumberColumnConfiguration(this, name, ColumnType.TINYINT_T).add({})
  inline fun SMALLINT(name:String) = NumberColumnConfiguration(this, name, ColumnType.SMALLINT_T).add({})
  inline fun MEDIUMINT(name:String) = NumberColumnConfiguration(this, name, ColumnType.MEDIUMINT_T).add({})
  inline fun INT(name:String) = NumberColumnConfiguration(this, name, ColumnType.INT_T).add({})
  inline fun BIGINT(name:String) = NumberColumnConfiguration(this, name, ColumnType.BIGINT_T).add({})
  inline fun FLOAT(name:String) = NumberColumnConfiguration(this, name, ColumnType.FLOAT_T).add({})
  inline fun DOUBLE(name:String) = NumberColumnConfiguration(this, name, ColumnType.DOUBLE_T).add({})
  inline fun DECIMAL(name:String, precision:Int=-1, scale:Int=-1) = DecimalColumnConfiguration(this, name, ColumnType.DECIMAL_T, precision, scale).add({})
  inline fun NUMERIC(name:String, precision:Int=-1, scale:Int=-1) = DecimalColumnConfiguration(this, name, ColumnType.NUMERIC_T, precision, scale).add({})
  inline fun DATE(name:String) = ColumnConfiguration(this, name, ColumnType.DATE_T).add({})
  inline fun TIME(name:String) = ColumnConfiguration(this, name, ColumnType.TIME_T).add({})
  inline fun TIMESTAMP(name:String) = ColumnConfiguration(this, name, ColumnType.TIMESTAMP_T).add({})
  inline fun DATETIME(name:String) = ColumnConfiguration(this, name, ColumnType.DATETIME_T).add({})
  inline fun YEAR(name:String) = ColumnConfiguration(this, name, ColumnType.YEAR_T).add({})
  inline fun CHAR(name:String, length:Int = -1) = LengthCharColumnConfiguration(this, name, ColumnType.CHAR_T, length).add({})
  inline fun VARCHAR(name:String, length:Int) = LengthCharColumnConfiguration(this, name, ColumnType.VARCHAR_T, length).add({})
  inline fun BINARY(name:String, length:Int) = SimpleLengthColumnConfiguration(this, name, ColumnType.BINARY_T, length).add({})
  inline fun VARBINARY(name:String, length:Int) = SimpleLengthColumnConfiguration(this, name, ColumnType.VARBINARY_T, length).add({})
  inline fun TINYBLOB(name:String) = ColumnConfiguration(this, name, ColumnType.TINYBLOB_T).add({})
  inline fun BLOB(name:String) = ColumnConfiguration(this, name, ColumnType.BLOB_T).add({})
  inline fun MEDIUMBLOB(name:String) = ColumnConfiguration(this, name, ColumnType.MEDIUMBLOB_T).add({})
  inline fun LONGBLOB(name:String) = ColumnConfiguration(this, name, ColumnType.LONGBLOB_T).add({})
  inline fun TINYTEXT(name:String) = CharColumnConfiguration(this, name, ColumnType.TINYTEXT_T).add({})
  inline fun TEXT(name:String) = CharColumnConfiguration(this, name, ColumnType.TEXT_T).add({})
  inline fun MEDIUMTEXT(name:String) = CharColumnConfiguration(this, name, ColumnType.MEDIUMTEXT_T).add({})
  inline fun LONGTEXT(name:String) = CharColumnConfiguration(this, name, ColumnType.LONGTEXT_T).add({})


  inline fun INDEX(col1: ColumnRef<*,*>, vararg cols: ColumnRef<*,*>) { (_indices as MutableList<List<Column<*,*>>>).add(mutableListOf(resolve(col1)).apply { addAll(resolve(cols)) })}
  inline fun UNIQUE(col1: ColumnRef<*,*>, vararg cols: ColumnRef<*,*>) { (_uniqueKeys as MutableList<List<Column<*,*>>>).add(mutableListOf(resolve(col1)).apply { addAll(resolve(cols)) })}
  inline fun PRIMARY_KEY(col1: ColumnRef<*,*>, vararg cols: ColumnRef<*,*>) { __primaryKey = mutableListOf(resolve(col1)).apply { addAll(resolve(cols)) }}

  class __FOREIGN_KEY__6<T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>, T4:Any, S4:ColumnType<T4,S4>, T5:Any, S5:ColumnType<T5,S5>, T6:Any, S6:ColumnType<T6,S6>>(val table: MutableTable, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>, val col3:ColumnRef<T3, S3>, val col4:ColumnRef<T4, S4>, val col5:ColumnRef<T5, S5>, val col6:ColumnRef<T6, S6>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>, ref3:ColumnRef<T3, S3>, ref4:ColumnRef<T4, S4>, ref5:ColumnRef<T5, S5>, ref6:ColumnRef<T6, S6>) {
      (table._foreignKeys as MutableList<ForeignKey>).add(ForeignKey(listOf(col1, col2, col3, col4, col5, col6), ref1.table, listOf(ref1, ref2, ref3, ref4, ref5, ref6).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>, T4:Any, S4:ColumnType<T4,S4>, T5:Any, S5:ColumnType<T5,S5>, T6:Any, S6:ColumnType<T6,S6>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>, col3:ColumnRef<T3, S3>, col4: ColumnRef<T4, S4>, col5:ColumnRef<T5, S5>, col6:ColumnRef<T6, S6>) =
        __FOREIGN_KEY__6(this, col1,col2,col3,col4,col5,col6)


  class __FOREIGN_KEY__5<T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>, T4:Any, S4:ColumnType<T4,S4>, T5:Any, S5:ColumnType<T5,S5>>(val table: MutableTable, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>, val col3:ColumnRef<T3, S3>, val col4:ColumnRef<T4, S4>, val col5:ColumnRef<T5, S5>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>, ref3:ColumnRef<T3, S3>, ref4:ColumnRef<T4, S4>, ref5:ColumnRef<T5, S5>) {
      (table._foreignKeys as MutableList<ForeignKey>).add(ForeignKey(listOf(col1, col2, col3, col4, col5), ref1.table, listOf(ref1, ref2, ref3, ref4, ref5).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>, T4:Any, S4:ColumnType<T4,S4>, T5:Any, S5:ColumnType<T5,S5>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>, col3:ColumnRef<T3, S3>, col4: ColumnRef<T4, S4>, col5:ColumnRef<T5, S5>) =
        __FOREIGN_KEY__5(this, col1,col2,col3,col4,col5)

  class __FOREIGN_KEY__4<T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>, T4:Any, S4:ColumnType<T4,S4>>(val table: MutableTable, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>, val col3:ColumnRef<T3, S3>, val col4:ColumnRef<T4, S4>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>, ref3:ColumnRef<T3, S3>, ref4:ColumnRef<T4, S4>) {
      (table._foreignKeys as MutableList<ForeignKey>).add(ForeignKey(listOf(col1, col2, col3, col4), ref1.table, listOf(ref1, ref2, ref3, ref4)))
    }
  }

  inline fun <T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>, T4:Any, S4:ColumnType<T4,S4>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>, col3:ColumnRef<T3, S3>, col4: ColumnRef<T4, S4>) =
        __FOREIGN_KEY__4(this, col1,col2,col3,col4)

  class __FOREIGN_KEY__3<T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>>(val table: MutableTable, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>, val col3:ColumnRef<T3, S3>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>, ref3:ColumnRef<T3, S3>) {
      (table._foreignKeys as MutableList<ForeignKey>).add(ForeignKey(listOf(col1, col2, col3), ref1.table, listOf(ref1, ref2, ref3).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>, T3:Any, S3:ColumnType<T3,S3>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>, col3:ColumnRef<T3, S3>) =
        __FOREIGN_KEY__3(this, col1,col2,col3)

  class __FOREIGN_KEY__2<T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>>(val table: MutableTable, val col1:ColumnRef<T1, S1>, val col2:ColumnRef<T2, S2>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1>, ref2:ColumnRef<T2, S2>) {
      (table._foreignKeys as MutableList<ForeignKey>).add(ForeignKey(listOf(col1, col2), ref1.table, listOf(ref1, ref2).apply { require(ref2.table==ref1.table) }))
    }
  }

  inline fun <T1:Any, S1:ColumnType<T1,S1>, T2:Any, S2:ColumnType<T2,S2>> FOREIGN_KEY(col1: ColumnRef<T1, S1>, col2:ColumnRef<T2, S2>) =
        __FOREIGN_KEY__2(this, col1,col2)

  class __FOREIGN_KEY__1<T1:Any, S1:ColumnType<T1,S1>>(val table: MutableTable, val col1:ColumnRef<T1, S1>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1>) {
      (table._foreignKeys as MutableList<ForeignKey>).add(ForeignKey(listOf(col1), ref1.table, listOf(ref1)))
    }
  }

  inline fun <T1:Any, S1:ColumnType<T1,S1>> FOREIGN_KEY(col1: ColumnRef<T1, S1>) =
        __FOREIGN_KEY__1(this, col1)


}