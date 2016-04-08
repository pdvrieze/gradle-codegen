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

import uk.ac.bournemouth.kotlinsql.AbstractColumnConfiguration.*
import uk.ac.bournemouth.kotlinsql.AbstractColumnConfiguration.AbstractCharColumnConfiguration.CharColumnConfiguration
import uk.ac.bournemouth.kotlinsql.AbstractColumnConfiguration.AbstractCharColumnConfiguration.LengthCharColumnConfiguration
import uk.ac.bournemouth.kotlinsql.AbstractColumnConfiguration.AbstractNumberColumnConfiguration.DecimalColumnConfiguration
import uk.ac.bournemouth.kotlinsql.AbstractColumnConfiguration.AbstractNumberColumnConfiguration.NumberColumnConfiguration
import uk.ac.bournemouth.kotlinsql.ColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.CharColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.DecimalColumnType.DECIMAL_T
import uk.ac.bournemouth.kotlinsql.ColumnType.DecimalColumnType.NUMERIC_T
import uk.ac.bournemouth.kotlinsql.ColumnType.LengthCharColumnType.CHAR_T
import uk.ac.bournemouth.kotlinsql.ColumnType.LengthCharColumnType.VARCHAR_T
import uk.ac.bournemouth.kotlinsql.ColumnType.LengthColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.NumericColumnType.*
import uk.ac.bournemouth.kotlinsql.ColumnType.SimpleColumnType.*
import uk.ac.bournemouth.util.kotlin.sql.DBConnection
import uk.ac.bournemouth.util.kotlin.sql.connection
import java.math.BigDecimal
import java.sql.Date
import java.sql.ResultSet
import java.sql.Time
import java.sql.Timestamp
import java.util.*
import javax.sql.DataSource
import kotlin.reflect.KClass
import kotlin.reflect.KProperty

/**
 * A reference to a column.
 *
 * @param table The table the column is a part of
 * @param name The name of the column
 * @param type The [IColumnType] of the column.
 */
interface ColumnRef<T:Any, S: IColumnType<T, S, C>, C:Column<T,S,C>> {
  val table: TableRef
  val name:String
  val type: S
}

class ColsetRef(val table:TableRef, val columns:List<out ColumnRef<*,*,*>>) {
  constructor(table:TableRef, col1: ColumnRef<*,*,*>, vararg cols:ColumnRef<*,*,*>): this(table, mutableListOf(col1).apply { addAll(cols) })
}

interface BoundedType {
  val maxLen:Int
}

interface IColumnType<T:Any, S: IColumnType<T, S, C>, C:Column<T,S,C>> {
  val typeName:String
  val type: KClass<T>

  fun cast(column: Column<*,*,*>): Column<T, S, C>
  fun cast(value: Any): T
  /**
   * Cast the given value to the type of the column. Null values are fine, incompatible values will
   * throw a ClassCastException
   */
  fun maybeCast(value: Any?): T?

  fun newConfiguration(owner: Table, refColumn: C): AbstractColumnConfiguration<T,S, C, out Any>

  fun fromResultSet(rs: ResultSet, pos: Int): T?
}

sealed class ColumnType<T:Any, S: ColumnType<T, S, C>, C:Column<T,S,C>>(override val typeName:String, override val type: KClass<T>): IColumnType<T,S,C> {

  @Suppress("UNCHECKED_CAST")
  override fun cast(column: Column<*,*,*>): C {
    if (column.type.typeName == typeName) {
      return column as C
    } else {
      throw TypeCastException("The given column is not of the correct type")
    }
  }

  override fun maybeCast(value: Any?): T? {
    return if (value!=null) type.java.cast(value) else null
  }

  override fun cast(value:Any): T {
    return type.java.cast(value)
  }

  // @formatter:off
  interface INumericColumnType<T:Any, S:INumericColumnType<T,S,C>, C:INumericColumn<T,S,C>>: IColumnType<T,S,C>

  sealed class NumericColumnType<T:Any, S: NumericColumnType<T, S>>(typeName: String, type: KClass<T>):ColumnType<T,S, NumericColumn<T,S>>(typeName, type), INumericColumnType<T,S, NumericColumn<T,S>> {
    object TINYINT_T   : NumericColumnType<Byte, TINYINT_T>("BIGINT", Byte::class){
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getByte(pos).let { if (rs.wasNull()) null else it }
    }
    object SMALLINT_T  : NumericColumnType<Short, SMALLINT_T>("SMALLINT", Short::class){
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getShort(pos).let { if (rs.wasNull()) null else it }
    }
    object MEDIUMINT_T : NumericColumnType<Int, MEDIUMINT_T>("MEDIUMINT", Int::class){
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getInt(pos).let { if (rs.wasNull()) null else it }
    }
    object INT_T       : NumericColumnType<Int, INT_T>("INT", Int::class){
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getInt(pos).let { if (rs.wasNull()) null else it }
    }
    object BIGINT_T    : NumericColumnType<Long, BIGINT_T>("BIGINT", Long::class){
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getLong(pos).let { if (rs.wasNull()) null else it }
    }

    object FLOAT_T     : NumericColumnType<Float, FLOAT_T>("FLOAT", Float::class){
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getFloat(pos).let { if (rs.wasNull()) null else it }
    }
    object DOUBLE_T    : NumericColumnType<Double, DOUBLE_T>("DOUBLE", Double::class){
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getDouble(pos).let { if (rs.wasNull()) null else it }
    }

    override fun newConfiguration(owner: Table, refColumn: NumericColumn<T,S>)=
          NumberColumnConfiguration(owner, refColumn.name, this as S)

  }

  sealed class DecimalColumnType<S:DecimalColumnType<S>>(typeName: String, type: KClass<BigDecimal>):ColumnType<BigDecimal,S, DecimalColumn<S>>(typeName, type), INumericColumnType<BigDecimal,S, DecimalColumn<S>> {
    override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getBigDecimal(pos)

    object DECIMAL_T   : DecimalColumnType<DECIMAL_T>("DECIMAL", BigDecimal::class)
    object NUMERIC_T   : DecimalColumnType<NUMERIC_T>("NUMERIC", BigDecimal::class)

    override fun newConfiguration(owner: Table, refColumn: DecimalColumn<S>):DecimalColumnConfiguration<S> {
      refColumn as DecimalColumn<S>
      return DecimalColumnConfiguration(owner, refColumn.name, this as S, refColumn.precision, refColumn.scale)
    }
  }

  sealed class SimpleColumnType<T:Any, S:SimpleColumnType<T,S>>(typeName: String, type: KClass<T>):ColumnType<T,S, SimpleColumn<T,S>>(typeName, type) {

    object BIT_T       : SimpleColumnType<Boolean, BIT_T>("BIT", Boolean::class), BoundedType {
      override val maxLen = 64
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getBoolean(pos).let { if (rs.wasNull()) null else it }
    }

    object DATE_T      : SimpleColumnType<java.sql.Date, DATE_T>("DATE", java.sql.Date::class){
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getDate(pos)
    }
    object TIME_T      : SimpleColumnType<java.sql.Time, TIME_T>("TIME", java.sql.Time::class){
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getTime(pos)
    }
    object TIMESTAMP_T : SimpleColumnType<java.sql.Timestamp, TIMESTAMP_T>("TIMESTAMP", java.sql.Timestamp::class){
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getTimestamp(pos)
    }
    object DATETIME_T  : SimpleColumnType<java.sql.Timestamp, DATETIME_T>("TIMESTAMP", java.sql.Timestamp::class){
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getTimestamp(pos)
    }
    object YEAR_T      : SimpleColumnType<java.sql.Date, YEAR_T>("YEAR", java.sql.Date::class){
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getDate(pos)
    }

    object TINYBLOB_T  : SimpleColumnType<ByteArray, TINYBLOB_T>("TINYBLOB", ByteArray::class), BoundedType {
      override val maxLen = 255
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getBytes(pos)
    }
    object BLOB_T      : SimpleColumnType<ByteArray, BLOB_T>("BLOB", ByteArray::class), BoundedType {
      override val maxLen = 0xffff
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getBytes(pos)
    }
    object MEDIUMBLOB_T: SimpleColumnType<ByteArray, MEDIUMBLOB_T>("MEDIUMBLOB", ByteArray::class), BoundedType {
      override val maxLen = 0xffffff
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getBytes(pos)
    }
    object LONGBLOB_T  : SimpleColumnType<ByteArray, LONGBLOB_T>("LONGBLOB", ByteArray::class), BoundedType {
      override val maxLen = Int.MAX_VALUE /*Actually it would be more*/
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getBytes(pos)
    }

    override fun newConfiguration(owner: Table, refColumn: SimpleColumn<T,S>) =
          NormalColumnConfiguration(owner, refColumn.name, this as S)

  }

  interface ICharColumnType<S:ICharColumnType<S, C>, C:ICharColumn<String,S, C>>: IColumnType<String,S,C>

  sealed class CharColumnType<S:CharColumnType<S>>(typeName: String, type: KClass<String>):ColumnType<String,S, CharColumn<S>>(typeName, type), ICharColumnType<S, CharColumn<S>> {
    override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getString(pos)

    object TINYTEXT_T  : CharColumnType<TINYTEXT_T>("TINYTEXT", String::class), BoundedType { override val maxLen:Int get() = 255 }
    object TEXT_T      : CharColumnType<TEXT_T>("TEXT", String::class), BoundedType { override val maxLen:Int get() = 0xffff }
    object MEDIUMTEXT_T: CharColumnType<MEDIUMTEXT_T>("MEDIUMTEXT", String::class), BoundedType { override val maxLen:Int get() = 0xffffff }
    object LONGTEXT_T  : CharColumnType<LONGTEXT_T>("LONGTEXT", String::class), BoundedType { override val maxLen:Int get() = Int.MAX_VALUE /*Actually it would be more*/}

    @Suppress("UNCHECKED_CAST")
    override fun newConfiguration(owner: Table, refColumn: CharColumn<S>) =
          CharColumnConfiguration(owner, refColumn.name, this as S)

  }

  interface ILengthColumnType<T:Any, S:ILengthColumnType<T,S, C>, C:ILengthColumn<T,S,C>>: IColumnType<T,S,C>

  sealed class LengthColumnType<T:Any, S:LengthColumnType<T,S>>(typeName: String, type: KClass<T>):ColumnType<T,S, LengthColumn<T,S>>(typeName, type), ILengthColumnType<T,S, LengthColumn<T,S>> {

    object BITFIELD_T  : LengthColumnType<BooleanArray, BITFIELD_T>("BIT", BooleanArray::class) {
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getArray(pos) as BooleanArray

    }

    object BINARY_T    : LengthColumnType<ByteArray, BINARY_T>("BINARY", ByteArray::class), BoundedType {
      override val maxLen = 255
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getBytes(pos)
    }
    object VARBINARY_T : LengthColumnType<ByteArray, VARBINARY_T>("VARBINARY", ByteArray::class), BoundedType {
      override val maxLen = 0xffff
      override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getBytes(pos)
    }

    @Suppress("UNCHECKED_CAST")
    override fun newConfiguration(owner: Table, refColumn: LengthColumn<T,S>) =
          LengthColumnConfiguration(owner, refColumn.name, this as S, refColumn.length)

  }

  sealed class LengthCharColumnType<S:LengthCharColumnType<S>>(typeName: String, type: KClass<String>):ColumnType<String,S, LengthCharColumn<S>>(typeName, type), ILengthColumnType<String,S,LengthCharColumn<S>>, ICharColumnType<S,LengthCharColumn<S>> {

    object CHAR_T      : LengthCharColumnType<CHAR_T>("CHAR", String::class), BoundedType { override val maxLen:Int get() = 255 }
    object VARCHAR_T   : LengthCharColumnType<VARCHAR_T>("VARCHAR", String::class), BoundedType { override val maxLen:Int get() = 0xffff }

    override fun newConfiguration(owner: Table, refColumn: LengthCharColumn<S>) =
          LengthCharColumnConfiguration(owner, refColumn.name, this as S, (refColumn as LengthCharColumn).length)

    override fun fromResultSet(rs: ResultSet, pos: Int) = rs.getString(pos)

  }




  /*
  ENUM(value1,value2,value3,...) [CHARACTER SET charset_name] [COLLATE collation_name]
  SET(value1,value2,value3,...) [CHARACTER SET charset_name] [COLLATE collation_name]
   */
  // @formatter:on
}


interface Column<T:Any, S: IColumnType<T, S,C>, C:Column<T,S,C>>: ColumnRef<T,S,C> {
  fun ref(): ColumnRef<T,S,C>
  val notnull: Boolean?
  val unique: Boolean
  val autoincrement: Boolean
  val default: T?
  val comment:String?
  val columnFormat: AbstractColumnConfiguration.ColumnFormat?
  val storageFormat: AbstractColumnConfiguration.StorageFormat?
  val references:ColsetRef?

  fun toDDL(): CharSequence

  fun copyConfiguration(newName:String? = null, owner: Table): AbstractColumnConfiguration<T,S,C, Any>
}

interface SimpleColumn<T:Any, S: SimpleColumnType<T, S>>: Column<T,S, SimpleColumn<T,S>> {
  override fun copyConfiguration(newName:String?, owner: Table): NormalColumnConfiguration<T,S>
}

interface Foo<T:Any,S: IColumnType<T,S,C>,C:Column<T,S,C>>: Column<T,S,C>

interface INumericColumn<T:Any, S: INumericColumnType<T, S, C>,C:INumericColumn<T,S,C>>: Column<T,S,C> {
  val unsigned: Boolean
  val zerofill: Boolean
  val displayLength: Int
}

interface NumericColumn<T:Any, S: NumericColumnType<T, S>>: INumericColumn<T,S, NumericColumn<T,S>> {
  override fun copyConfiguration(newName:String?, owner: Table): NumberColumnConfiguration<T,S>
}

interface ICharColumn<T:Any, S: ICharColumnType<S, C>, C:ICharColumn<String,S,C>>: Column<String, S, C> {
  val charset: String?
  val collation: String?
  val binary: Boolean

}

interface CharColumn<S: CharColumnType<S>>: ICharColumn<String, S, CharColumn<S>> {
  override fun copyConfiguration(newName:String?, owner: Table): CharColumnConfiguration<S>
}


interface LengthCharColumn<S: LengthCharColumnType<S>>: ICharColumn<String, S, LengthCharColumn<S>>, ILengthColumn<String, S, LengthCharColumn<S>> {
  override fun copyConfiguration(newName:String?, owner: Table): LengthCharColumnConfiguration<S>
}


interface DecimalColumn<S: DecimalColumnType<S>>: INumericColumn<BigDecimal, S, DecimalColumn<S>> {
  val precision:Int
  val scale:Int
  override fun copyConfiguration(newName:String?, owner: Table): DecimalColumnConfiguration<S>
}


interface ILengthColumn<T:Any, S: ILengthColumnType<T, S, C>, C: ILengthColumn<T,S,C>>: Column<T, S, C> {
  val length:Int
}

interface LengthColumn<T:Any, S:LengthColumnType<T,S>>: ILengthColumn<T,S, LengthColumn<T,S>> {
  override fun copyConfiguration(newName:String?, owner: Table): LengthColumnConfiguration<T,S>
}

class ForeignKey constructor(private val fromCols:List<ColumnRef<*,*,*>>, private val toTable:TableRef, private val toCols:List<ColumnRef<*,*,*>>) {
  internal fun toDDL(): CharSequence {
    val transform: (ColumnRef<*,*,*>) -> CharSequence = { it.name }
    val result = fromCols.joinTo(StringBuilder(), "`, `", "FOREIGN KEY (`", "`) REFERENCES ", transform = transform)
    result.append(toTable._name)
    return toCols.joinTo(result, "`, `", "`)", transform = transform)
  }
}

/**
 * The main class that caries a lot of the load for the class.
 */
@Suppress("NOTHING_TO_INLINE")
class TableConfiguration(override val _name:String, val extra:String?=null):TableRef {

  val cols = mutableListOf<Column<*, *,*>>()
  var primaryKey: List<ColumnRef<*,*,*>>? = null
  val foreignKeys = mutableListOf<ForeignKey>()
  val uniqueKeys = mutableListOf<List<ColumnRef<*,*,*>>>()
  val indices = mutableListOf<List<ColumnRef<*,*,*>>>()

  inline fun <T :Any, S: IColumnType<T,S,C>, C:Column<T,S,C>, CONF_T : AbstractColumnConfiguration<T, S, C, CONF_T>> CONF_T.add(block: CONF_T.() ->Unit):ColumnRef<T,S,C> {
    val newColumn:C = apply(block).newColumn()
    cols.add(newColumn)
    return newColumn.ref()
  }

  // @formatter:off
  /* Versions with configuration closure. */
  fun BIT(name:String, block: NormalColumnConfiguration<Boolean, BIT_T>.() -> Unit) = NormalColumnConfiguration( this, name, BIT_T).add(block)
  fun BIT(name:String, length:Int, block: BaseLengthColumnConfiguration<BooleanArray, BITFIELD_T, LengthColumn<BooleanArray,BITFIELD_T>>.() -> Unit) = LengthColumnConfiguration( this, name, BITFIELD_T, length).add( block)
  fun TINYINT(name: String, block: NumberColumnConfiguration<Byte, TINYINT_T>.() -> Unit) = NumberColumnConfiguration( this, name, TINYINT_T).add( block)
  fun SMALLINT(name: String, block: NumberColumnConfiguration<Short, SMALLINT_T>.() -> Unit) = NumberColumnConfiguration( this, name, SMALLINT_T).add( block)
  fun MEDIUMINT(name: String, block: NumberColumnConfiguration<Int, MEDIUMINT_T>.() -> Unit) = NumberColumnConfiguration( this, name, MEDIUMINT_T).add( block)
  fun INT(name: String, block: NumberColumnConfiguration<Int, INT_T>.() -> Unit) = AbstractColumnConfiguration.AbstractNumberColumnConfiguration.NumberColumnConfiguration( this, name, INT_T).add( block)
  fun BIGINT(name: String, block: NumberColumnConfiguration<Long, BIGINT_T>.() -> Unit) = NumberColumnConfiguration( this, name, BIGINT_T).add( block)
  fun FLOAT(name: String, block: NumberColumnConfiguration<Float, FLOAT_T>.() -> Unit) = NumberColumnConfiguration( this, name, FLOAT_T).add( block)
  fun DOUBLE(name: String, block: NumberColumnConfiguration<Double, DOUBLE_T>.() -> Unit) = NumberColumnConfiguration( this, name, DOUBLE_T).add( block)
  fun DECIMAL(name: String, precision: Int = -1, scale: Int = -1, block: DecimalColumnConfiguration<DECIMAL_T>.() -> Unit) = DecimalColumnConfiguration( this, name, DECIMAL_T, precision, scale).add( block)
  fun NUMERIC(name: String, precision: Int = -1, scale: Int = -1, block: DecimalColumnConfiguration<NUMERIC_T>.() -> Unit) = DecimalColumnConfiguration( this, name, NUMERIC_T, precision, scale).add( block)
  fun DATE(name: String, block: NormalColumnConfiguration<Date, DATE_T>.() -> Unit) = NormalColumnConfiguration( this, name, DATE_T).add( block)
  fun TIME(name: String, block: NormalColumnConfiguration<Time, TIME_T>.() -> Unit) = NormalColumnConfiguration( this, name, TIME_T).add( block)
  fun TIMESTAMP(name: String, block: NormalColumnConfiguration<Timestamp, TIMESTAMP_T>.() -> Unit) = NormalColumnConfiguration( this, name, TIMESTAMP_T).add( block)
  fun DATETIME(name: String, block: NormalColumnConfiguration<Timestamp, DATETIME_T>.() -> Unit) = NormalColumnConfiguration( this, name, DATETIME_T).add( block)
  fun YEAR(name: String, block: NormalColumnConfiguration<Date, YEAR_T>.() -> Unit) = NormalColumnConfiguration( this, name, YEAR_T).add( block)
  fun CHAR(name: String, length: Int = -1, block: LengthCharColumnConfiguration<CHAR_T>.() -> Unit) = LengthCharColumnConfiguration( this, name, CHAR_T, length).add( block)
  fun VARCHAR(name: String, length: Int, block: LengthCharColumnConfiguration<VARCHAR_T>.() -> Unit) = LengthCharColumnConfiguration( this, name, VARCHAR_T, length).add( block)
  fun BINARY(name: String, length: Int, block: BaseLengthColumnConfiguration<ByteArray, BINARY_T, LengthColumn<ByteArray, BINARY_T>>.() -> Unit) = LengthColumnConfiguration( this, name, BINARY_T, length).add( block)
  fun VARBINARY(name: String, length: Int, block: BaseLengthColumnConfiguration<ByteArray, VARBINARY_T, LengthColumn<ByteArray, VARBINARY_T>>.() -> Unit) = LengthColumnConfiguration( this, name, VARBINARY_T, length).add( block)
  fun TINYBLOB(name: String, block: NormalColumnConfiguration<ByteArray, TINYBLOB_T>.() -> Unit) = NormalColumnConfiguration( this, name, TINYBLOB_T).add( block)
  fun BLOB(name: String, block: NormalColumnConfiguration<ByteArray, BLOB_T>.() -> Unit) = NormalColumnConfiguration( this, name, BLOB_T).add( block)
  fun MEDIUMBLOB(name: String, block: NormalColumnConfiguration<ByteArray, MEDIUMBLOB_T>.() -> Unit) = NormalColumnConfiguration( this, name, MEDIUMBLOB_T).add( block)
  fun LONGBLOB(name: String, block: NormalColumnConfiguration<ByteArray, LONGBLOB_T>.() -> Unit) = NormalColumnConfiguration( this, name, LONGBLOB_T).add( block)
  fun TINYTEXT(name: String, block: CharColumnConfiguration<TINYTEXT_T>.() -> Unit) = CharColumnConfiguration( this, name, TINYTEXT_T).add( block)
  fun TEXT(name: String, block: CharColumnConfiguration<TEXT_T>.() -> Unit) = CharColumnConfiguration( this, name, TEXT_T).add( block)
  fun MEDIUMTEXT(name: String, block: CharColumnConfiguration<MEDIUMTEXT_T>.() -> Unit) = CharColumnConfiguration( this, name, MEDIUMTEXT_T).add( block)
  fun LONGTEXT(name: String, block: CharColumnConfiguration<LONGTEXT_T>.() -> Unit) = CharColumnConfiguration( this, name, LONGTEXT_T).add( block)

  /* Versions without configuration closure */
  fun BIT(name: String) = NormalColumnConfiguration(this, name, BIT_T).add({})
  fun BIT(name:String, length:Int) = LengthColumnConfiguration(this, name, BITFIELD_T, length).add({})
  fun TINYINT(name: String) = NumberColumnConfiguration(this, name, TINYINT_T).add({})
  fun SMALLINT(name:String) = NumberColumnConfiguration(this, name, SMALLINT_T).add({})
  fun MEDIUMINT(name:String) = NumberColumnConfiguration(this, name, MEDIUMINT_T).add({})
  fun INT(name: String) = NumberColumnConfiguration(this, name, INT_T).add({})
  fun BIGINT(name:String) = NumberColumnConfiguration(this, name, BIGINT_T).add({})
  fun FLOAT(name:String) = NumberColumnConfiguration(this, name, FLOAT_T).add({})
  fun DOUBLE(name:String) = NumberColumnConfiguration(this, name, DOUBLE_T).add({})
  fun DECIMAL(name:String, precision:Int=-1, scale:Int=-1) = DecimalColumnConfiguration(this, name, DECIMAL_T, precision, scale).add({})
  fun NUMERIC(name: String, precision: Int = -1, scale: Int = -1) = DecimalColumnConfiguration(this, name, NUMERIC_T, precision, scale).add({})
  fun DATE(name: String) = NormalColumnConfiguration(this, name, DATE_T).add({})
  fun TIME(name:String) = NormalColumnConfiguration(this, name, TIME_T).add({})
  fun TIMESTAMP(name:String) = NormalColumnConfiguration(this, name, TIMESTAMP_T).add({})
  fun DATETIME(name: String) = NormalColumnConfiguration(this, name, DATETIME_T).add({})
  fun YEAR(name:String) = NormalColumnConfiguration(this, name, YEAR_T).add({})
  fun CHAR(name:String, length:Int = -1) = LengthCharColumnConfiguration(this, name, CHAR_T, length).add({})
  fun VARCHAR(name: String, length: Int) = LengthCharColumnConfiguration(this, name, VARCHAR_T, length).add({})
  fun BINARY(name: String, length: Int) = LengthColumnConfiguration(this, name, BINARY_T, length).add({})
  fun VARBINARY(name: String, length: Int) = LengthColumnConfiguration(this, name, VARBINARY_T, length).add({})
  fun TINYBLOB(name: String) = NormalColumnConfiguration(this, name, TINYBLOB_T).add({})
  fun BLOB(name:String) = NormalColumnConfiguration(this, name, BLOB_T).add({})
  fun MEDIUMBLOB(name:String) = NormalColumnConfiguration(this, name, MEDIUMBLOB_T).add({})
  fun LONGBLOB(name: String) = NormalColumnConfiguration(this, name, LONGBLOB_T).add({})
  fun TINYTEXT(name:String) = CharColumnConfiguration(this, name, TINYTEXT_T).add({})
  fun TEXT(name:String) = CharColumnConfiguration(this, name, TEXT_T).add({})
  fun MEDIUMTEXT(name:String) = CharColumnConfiguration(this, name, MEDIUMTEXT_T).add({})
  fun LONGTEXT(name: String) = CharColumnConfiguration(this, name, LONGTEXT_T).add({})

  fun INDEX(col1: ColumnRef<*,*,*>, vararg cols: ColumnRef<*,*,*>) { indices.add(mutableListOf(col1).apply { addAll(cols) })}
  fun UNIQUE(col1: ColumnRef<*,*,*>, vararg cols: ColumnRef<*,*,*>) { uniqueKeys.add(mutableListOf(col1).apply { addAll(cols) })}
  fun PRIMARY_KEY(col1: ColumnRef<*,*,*>, vararg cols: ColumnRef<*,*,*>) { primaryKey = mutableListOf(col1).apply { addAll(cols) }}

  class __FOREIGN_KEY__6<T1:Any, S1: IColumnType<T1,S1,C1>, C1:Column<T1,S1,C1>,
                         T2:Any, S2: IColumnType<T2,S2,C2>, C2: Column<T2, S2, C2>,
                         T3:Any, S3: IColumnType<T3,S3,C3>, C3:Column<T3,S3,C3>,
                         T4:Any, S4: IColumnType<T4,S4,C4>, C4:Column<T4,S4,C4>,
                         T5:Any, S5: IColumnType<T5,S5,C5>, C5:Column<T5,S5,C5>,
                         T6:Any, S6: IColumnType<T6,S6,C6>, C6:Column<T6,S6,C6>>(val configuration:TableConfiguration,
                                                                                    val col1:ColumnRef<T1, S1, C1>, 
                                                                                    val col2:ColumnRef<T2, S2, C2>, 
                                                                                    val col3:ColumnRef<T3, S3, C3>, 
                                                                                    val col4:ColumnRef<T4, S4, C4>, 
                                                                                    val col5:ColumnRef<T5, S5, C5>, 
                                                                                    val col6:ColumnRef<T6, S6, C6>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1, C1>,
                          ref2:ColumnRef<T2, S2, C2>,
                          ref3:ColumnRef<T3, S3, C3>,
                          ref4:ColumnRef<T4, S4, C4>,
                          ref5:ColumnRef<T5, S5, C5>,
                          ref6:ColumnRef<T6, S6, C6>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2, col3, col4, col5, col6), ref1.table, listOf(ref1, ref2, ref3, ref4, ref5, ref6).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, S1: IColumnType<T1,S1,C1>, C1:Column<T1,S1,C1>,
              T2:Any, S2: IColumnType<T2,S2,C2>, C2:Column<T2,S2,C2>,
              T3:Any, S3: IColumnType<T3,S3,C3>, C3:Column<T3,S3,C3>,
              T4:Any, S4: IColumnType<T4,S4,C4>, C4:Column<T4,S4,C4>,
              T5:Any, S5: IColumnType<T5,S5,C5>, C5:Column<T5,S5,C5>,
              T6:Any, S6: IColumnType<T6,S6,C6>, C6:Column<T6,S6,C6>> FOREIGN_KEY(col1: ColumnRef<T1, S1, C1>, col2:ColumnRef<T2, S2, C2>, col3:ColumnRef<T3, S3, C3>, col4: ColumnRef<T4, S4, C4>, col5:ColumnRef<T5, S5, C5>, col6:ColumnRef<T6, S6, C6>) =
      __FOREIGN_KEY__6(this, col1,col2,col3,col4,col5,col6)


  class __FOREIGN_KEY__5<T1:Any, S1: IColumnType<T1,S1,C1>, C1:Column<T1,S1,C1>,
                         T2:Any, S2: IColumnType<T2,S2,C2>, C2:Column<T2,S2,C2>,
                         T3:Any, S3: IColumnType<T3,S3,C3>, C3:Column<T3,S3,C3>,
                         T4:Any, S4: IColumnType<T4,S4,C4>, C4:Column<T4,S4,C4>,
                         T5:Any, S5: IColumnType<T5,S5,C5>, C5:Column<T5,S5,C5>>(val configuration:TableConfiguration, val col1:ColumnRef<T1, S1, C1>, val col2:ColumnRef<T2, S2, C2>, val col3:ColumnRef<T3, S3, C3>, val col4:ColumnRef<T4, S4, C4>, val col5:ColumnRef<T5, S5, C5>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1, C1>, ref2:ColumnRef<T2, S2, C2>, ref3:ColumnRef<T3, S3, C3>, ref4:ColumnRef<T4, S4, C4>, ref5:ColumnRef<T5, S5, C5>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2, col3, col4, col5), ref1.table, listOf(ref1, ref2, ref3, ref4, ref5).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, S1: IColumnType<T1,S1,C1>, C1:Column<T1,S1,C1>,
              T2:Any, S2: IColumnType<T2,S2,C2>, C2:Column<T2,S2,C2>,
              T3:Any, S3: IColumnType<T3,S3,C3>, C3:Column<T3,S3,C3>,
              T4:Any, S4: IColumnType<T4,S4,C4>, C4:Column<T4,S4,C4>,
              T5:Any, S5: IColumnType<T5,S5,C5>, C5:Column<T5,S5,C5>> FOREIGN_KEY(col1: ColumnRef<T1, S1, C1>, col2:ColumnRef<T2, S2, C2>, col3:ColumnRef<T3, S3, C3>, col4: ColumnRef<T4, S4, C4>, col5:ColumnRef<T5, S5, C5>) =
      __FOREIGN_KEY__5(this, col1,col2,col3,col4,col5)

  class __FOREIGN_KEY__4<T1:Any, S1: IColumnType<T1,S1,C1>, C1:Column<T1,S1,C1>,
                         T2:Any, S2: IColumnType<T2,S2,C2>, C2:Column<T2,S2,C2>,
                         T3:Any, S3: IColumnType<T3,S3,C3>, C3:Column<T3,S3,C3>,
                         T4:Any, S4: IColumnType<T4,S4,C4>, C4:Column<T4,S4,C4>>(val configuration:TableConfiguration, val col1:ColumnRef<T1, S1, C1>, val col2:ColumnRef<T2, S2, C2>, val col3:ColumnRef<T3, S3, C3>, val col4:ColumnRef<T4, S4, C4>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1, C1>, ref2:ColumnRef<T2, S2, C2>, ref3:ColumnRef<T3, S3, C3>, ref4:ColumnRef<T4, S4, C4>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2, col3, col4), ref1.table, listOf(ref1, ref2, ref3, ref4)))
    }
  }

  inline fun <T1:Any, S1: IColumnType<T1,S1,C1>, C1:Column<T1,S1,C1>,
              T2:Any, S2: IColumnType<T2,S2,C2>, C2:Column<T2,S2,C2>,
              T3:Any, S3: IColumnType<T3,S3,C3>, C3:Column<T3,S3,C3>,
              T4:Any, S4: IColumnType<T4,S4,C4>, C4:Column<T4,S4,C4>> FOREIGN_KEY(col1: ColumnRef<T1, S1, C1>, col2:ColumnRef<T2, S2, C2>, col3:ColumnRef<T3, S3, C3>, col4: ColumnRef<T4, S4, C4>) =
      __FOREIGN_KEY__4(this, col1,col2,col3,col4)

  class __FOREIGN_KEY__3<T1:Any, S1: IColumnType<T1,S1,C1>, C1:Column<T1,S1,C1>,
                         T2:Any, S2: IColumnType<T2,S2,C2>, C2:Column<T2,S2,C2>,
                         T3:Any, S3: IColumnType<T3,S3,C3>, C3:Column<T3,S3,C3>>(val configuration:TableConfiguration, val col1:ColumnRef<T1, S1, C1>, val col2:ColumnRef<T2, S2, C2>, val col3:ColumnRef<T3, S3, C3>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1, C1>, ref2:ColumnRef<T2, S2, C2>, ref3:ColumnRef<T3, S3, C3>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2, col3), ref1.table, listOf(ref1, ref2, ref3).apply { forEach { require(it.table==ref1.table) } }))
    }
  }

  inline fun <T1:Any, S1: IColumnType<T1,S1,C1>, C1:Column<T1,S1,C1>,
              T2:Any, S2: IColumnType<T2,S2,C2>, C2:Column<T2,S2,C2>,
              T3:Any, S3: IColumnType<T3,S3,C3>, C3:Column<T3,S3,C3>> FOREIGN_KEY(col1: ColumnRef<T1, S1, C1>, col2:ColumnRef<T2, S2, C2>, col3:ColumnRef<T3, S3, C3>) =
      __FOREIGN_KEY__3(this, col1,col2,col3)

  class __FOREIGN_KEY__2<T1:Any, S1: IColumnType<T1,S1,C1>, C1:Column<T1,S1,C1>,
 T2:Any, S2: IColumnType<T2,S2,C2>, C2:Column<T2,S2,C2>>(val configuration:TableConfiguration, val col1:ColumnRef<T1, S1, C1>, val col2:ColumnRef<T2, S2, C2>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1, C1>, ref2:ColumnRef<T2, S2, C2>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1, col2), ref1.table, listOf(ref1, ref2).apply { require(ref2.table==ref1.table) }))
    }
  }

  inline fun <T1:Any, S1: IColumnType<T1,S1,C1>, C1:Column<T1,S1,C1>,
 T2:Any, S2: IColumnType<T2,S2,C2>, C2:Column<T2,S2,C2>> FOREIGN_KEY(col1: ColumnRef<T1, S1, C1>, col2:ColumnRef<T2, S2, C2>) =
      __FOREIGN_KEY__2(this, col1,col2)

  class __FOREIGN_KEY__1<T1:Any, S1: IColumnType<T1,S1,C1>, C1:Column<T1,S1,C1>>(val configuration:TableConfiguration, val col1:ColumnRef<T1, S1, C1>) {
    inline fun REFERENCES(ref1:ColumnRef<T1, S1, C1>) {
      configuration.foreignKeys.add(ForeignKey(listOf(col1), ref1.table, listOf(ref1)))
    }
  }

  inline fun <T1:Any, S1: IColumnType<T1,S1,C1>, C1:Column<T1,S1,C1>> FOREIGN_KEY(col1: ColumnRef<T1, S1, C1>) =
      __FOREIGN_KEY__1(this, col1)
  // @formatter:on

}

class DatabaseConfiguration {

  private class __AnonymousTable(name:String, extra: String?, block:TableConfiguration.()->Unit): ImmutableTable(name, extra, block)

  val tables = mutableListOf<ImmutableTable>()

  fun table(name:String, extra: String? = null, block:TableConfiguration.()->Unit):TableRef {
    return __AnonymousTable(name, extra, block)
  }

  inline fun table(t: ImmutableTable):TableRef {
    tables.add(t); return t.ref()
  }

}

/**
 * A base class for table declarations. Users of the code are expected to use this with a configuration closure to create
 * database tables. for typed columns to be available they need to be declared using `by [type]` or `by [name]`.
 *
 * A sample use is:
 * ```
 *    object peopleTable:[ImmutableTable]("people", "ENGINE=InnoDB CHARSET=utf8", {
 *      val firstname = [VARCHAR]("firstname", 50)
 *      val familyname = [VARCHAR]("familyname", 30) { NOT_NULL }
 *      [DATE]("birthdate")
 *      [PRIMARY_KEY](firstname, familyname)
 *    }) {
 *      val firstname by [type]([VARCHAR_T])
 *      val surname by [name]("familyname", [VARCHAR_T])
 *      val birthdate by [type]([DATE_T])
 *    }
 * ```
 *
 * Note that the definition of typed fields through delegates is mainly to aid programmatic access. Values within the
 * configurator body are only visible there, and mainly serve to make definition of keys easy. Values that are not used
 * (such as birthdate) do not need to be stored. Their mere declaration adds them to the table configuration.
 *
 * @property _cols The list of columns in the table.
 * @property _primaryKey The primary key of the table (if defined)
 * @property _foreignKeys The foreign keys defined on the table.
 * @property _uniqueKeys Unique keys / uniqueness constraints defined on the table
 * @property _indices Additional indices defined on the table
 * @property _extra Extra table configuration to be appended after the definition. This contains information such as the
 *                  engine or charset to use.
 */
abstract class ImmutableTable private constructor(override val _name: String,
                                                  override val _cols: List<Column<*,*,*>>,
                                                  override val _primaryKey: List<Column<*,*,*>>?,
                                                  override val _foreignKeys: List<ForeignKey>,
                                                  override val _uniqueKeys: List<List<Column<*,*,*>>>,
                                                  override val _indices: List<List<Column<*,*,*>>>,
                                                  override val _extra: String?) : AbstractTable() {

  private constructor(c: TableConfiguration):this(c._name, c.cols, c.primaryKey?.let {c.cols.resolveAll(it)}, c.foreignKeys, c.uniqueKeys.map({c.cols.resolveAll(it)}), c.indices.map({c.cols.resolveAll(it)}), c.extra)

  /**
   * The main use of this class is through inheriting this constructor.
   */
  constructor(name:String, extra: String? = null, block: TableConfiguration.()->Unit): this(
        TableConfiguration(name, extra).apply(block)  )

  protected fun <T:Any, S: ColumnType<T, S, C>, C:Column<T,S,C>> type(type: ColumnType<T, S, C>) = TypeFieldAccessor<T, S, C>(
        type)

}

