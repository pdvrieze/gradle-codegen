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

import uk.ac.bournemouth.kotlinsql.*
import java.sql.*
import java.util.*
import java.util.concurrent.Executor
import javax.sql.DataSource

/**
 * Created by pdvrieze on 13/03/16.
 */

inline fun <R> DataSource.connection(db: Database, block: (DBConnection) -> R): R =
    this.getConnection().use {
      return DBConnection(connection, db).let(block)
    }


//inline fun <R> DataSource.connection(username: String, password: String, block: (DBConnection) -> R) = getConnection(username, password).use { connection(it, block) }

class DBConnection constructor(private val connection: Connection, val db: Database) {

  @Deprecated(message = "Do not use, this is there just for transitional purposes", level = DeprecationLevel.WARNING)
  fun __getConnection() = connection

  //    init {
  //        connection.autoCommit = false
  //    }

  fun <R> raw(block: (Connection) -> R): R = block(connection)
  fun <R> use(block: (DBConnection) -> R): R = useHelper({ it.connection.close() }) {
    return transaction(block)
  }

  fun <R> transaction(block: (DBConnection) -> R):R {
    connection.autoCommit=false
    try {
      return block(this).apply { commit() }
    } catch (e:Exception) {
      connection.rollback()
      throw e
    }
  }


  /**
   * @see [Connection.prepareStatement]
   */
  fun <R> prepareStatement(sql: String, block: StatementHelper.() -> R): R = connection.prepareStatement(sql).use { block(StatementHelper(it)) }


  /**
   * @see [Connection.commit]
   */
  fun commit() = connection.commit()

  fun getMetaData() = connection.getMetaData()

  private inline fun prepareCall(sql: String) = connection.prepareCall(sql)

  /** @see [Connection.autoCommit] */
  var autoCommit: Boolean
    get() = connection.autoCommit
    set(value) { connection.autoCommit = value }

  @Throws(SQLException::class)
  fun rollback() = connection.rollback()

  @Throws(SQLException::class)
  fun isClosed(): Boolean = connection.isClosed

  //======================================================================
  // Advanced features:

  @Throws(SQLException::class)
  fun setReadOnly(readOnly: Boolean) = connection.setReadOnly(readOnly)

  @Throws(SQLException::class)
  fun isReadOnly(): Boolean = connection.isReadOnly()

  @Throws(SQLException::class)
  fun setCatalog(catalog: String) = connection.setCatalog(catalog)

  @Throws(SQLException::class)
  fun getCatalog(): String = connection.getCatalog()

  @Deprecated("Don't use this, just use Connection's version", replaceWith = ReplaceWith("Connection.TRANSACTION_NONE", "java.sql.Connection"))
  val TRANSACTION_NONE = Connection.TRANSACTION_NONE

  @Deprecated("Don't use this, just use Connection's version", replaceWith = ReplaceWith("Connection.TRANSACTION_READ_UNCOMMITTED", "java.sql.Connection"))
  val TRANSACTION_READ_UNCOMMITTED = Connection.TRANSACTION_READ_UNCOMMITTED

  @Deprecated("Don't use this, just use Connection's version", replaceWith = ReplaceWith("Connection.TRANSACTION_READ_COMMITTED", "java.sql.Connection"))
  val TRANSACTION_READ_COMMITTED = Connection.TRANSACTION_READ_COMMITTED

  @Deprecated("Don't use this, just use Connection's version", replaceWith = ReplaceWith("Connection.TRANSACTION_REPEATABLE_READ", "java.sql.Connection"))
  val TRANSACTION_REPEATABLE_READ = Connection.TRANSACTION_REPEATABLE_READ

  @Deprecated("Don't use this, just use Connection's version", replaceWith = ReplaceWith("Connection.TRANSACTION_SERIALIZABLE", "java.sql.Connection"))
  val TRANSACTION_SERIALIZABLE = Connection.TRANSACTION_SERIALIZABLE

  @Throws(SQLException::class)
  fun setTransactionIsolation(level: Int) = connection.setTransactionIsolation(level)

  @Throws(SQLException::class)
  fun getTransactionIsolation(): Int = connection.transactionIsolation

  /**
   * Retrieves the first warning reported by calls on this
   * `Connection` object.  If there is more than one
   * warning, subsequent warnings will be chained to the first one
   * and can be retrieved by calling the method
   * `SQLWarning.getNextWarning` on the warning
   * that was retrieved previously.
   *
   * This method may not be
   * called on a closed connection; doing so will cause an
   * `SQLException` to be thrown.

   * Note: Subsequent warnings will be chained to this
   * SQLWarning.

   * @return the first `SQLWarning` object or `null`
   * *         if there are none
   * *
   * @exception SQLException if a database access error occurs or
   * *            this method is called on a closed connection
   * *
   * @see SQLWarning
   */
  val warningsIt: Iterator<SQLWarning> get() = object : AbstractIterator<SQLWarning>() {
    override fun computeNext() {
      val w = connection.warnings
      if (w != null) {
        setNext(w)
      } else {
        done()
      }
    }
  }

  val warnings: Sequence<SQLWarning> get() = object: Sequence<SQLWarning> {
    override fun iterator(): Iterator<SQLWarning> = warningsIt
  }

  /**
   * Clears all warnings reported for this `Connection` object.
   * After a call to this method, the method `getWarnings`
   * returns `null` until a new warning is
   * reported for this `Connection` object.

   * @exception SQLException SQLException if a database access error occurs
   * * or this method is called on a closed connection
   */
  @Throws(SQLException::class)
  fun clearWarnings() = connection.clearWarnings()


  //--------------------------JDBC 2.0-----------------------------

  @Throws(SQLException::class)
  fun <R> prepareStatement(sql: String, resultSetType: Int,
                                  resultSetConcurrency: Int, block: (StatementHelper) -> R): R {
    return connection.prepareStatement(sql, resultSetType, resultSetConcurrency).use { block(StatementHelper(it)) }
  }

  @Throws(SQLException::class)
  fun getTypeMap() = connection.typeMap

  @Throws(SQLException::class)
  fun setTypeMap(map: Map<String, Class<*>>) = connection.setTypeMap(map)

  //--------------------------JDBC 3.0-----------------------------

  enum class Holdability(internal val jdbc:Int) {
    HOLD_CURSORS_OVER_COMMIT(ResultSet.HOLD_CURSORS_OVER_COMMIT),
    CLOSE_CURSORS_AT_COMMIT(ResultSet.CLOSE_CURSORS_AT_COMMIT);
  }

  /**
   * @see [Connection.getHoldability]
   */
  var holdability:Holdability
    get() = when (connection.holdability) {
      ResultSet.HOLD_CURSORS_OVER_COMMIT -> Holdability.HOLD_CURSORS_OVER_COMMIT
      ResultSet.CLOSE_CURSORS_AT_COMMIT -> Holdability.CLOSE_CURSORS_AT_COMMIT
      else -> throw IllegalArgumentException()
    }
    set(value) { connection.holdability = value.jdbc }

  @Throws(SQLException::class)
  fun setHoldability(holdability: Int) = connection.setHoldability(holdability)

  @Throws(SQLException::class)
  fun getHoldability() = connection.holdability

  @Throws(SQLException::class)
  fun setSavepoint(): Savepoint = connection.setSavepoint()

  /**
   * Creates a savepoint with the given name in the current transaction
   * and returns the new `Savepoint` object that represents it.

   *
   *  if setSavepoint is invoked outside of an active transaction, a transaction will be started at this newly created
   * savepoint.

   * @param name a `String` containing the name of the savepoint
   * *
   * @return the new `Savepoint` object
   * *
   * @exception SQLException if a database access error occurs,
   * * this method is called while participating in a distributed transaction,
   * * this method is called on a closed connection
   * *            or this `Connection` object is currently in
   * *            auto-commit mode
   * *
   * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
   * * this method
   * *
   * @see Savepoint

   * @since 1.4
   */
  @Throws(SQLException::class)
  fun setSavepoint(name: String): Savepoint = connection.setSavepoint(name)

  /**
   * Undoes all changes made after the given `Savepoint` object
   * was set.
   *
   * This method should be used only when auto-commit has been disabled.

   * @param savepoint the `Savepoint` object to roll back to
   * *
   * @exception SQLException if a database access error occurs,
   * * this method is called while participating in a distributed transaction,
   * * this method is called on a closed connection,
   * *            the `Savepoint` object is no longer valid,
   * *            or this `Connection` object is currently in
   * *            auto-commit mode
   * *
   * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
   * * this method
   * *
   * @see Savepoint

   * @see .rollback

   * @since 1.4
   */
  @Throws(SQLException::class)
  fun rollback(savepoint: Savepoint) = connection.rollback(savepoint)

  /**
   * Removes the specified `Savepoint`  and subsequent `Savepoint` objects from the current
   * transaction. Any reference to the savepoint after it have been removed
   * will cause an `SQLException` to be thrown.

   * @param savepoint the `Savepoint` object to be removed
   * *
   * @exception SQLException if a database access error occurs, this
   * *  method is called on a closed connection or
   * *            the given `Savepoint` object is not a valid
   * *            savepoint in the current transaction
   * *
   * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
   * * this method
   * *
   * @since 1.4
   */
  @Throws(SQLException::class)
  fun releaseSavepoint(savepoint: Savepoint) = connection.releaseSavepoint(savepoint)

  /**
   * Creates a `PreparedStatement` object that will generate
   * `ResultSet` objects with the given type, concurrency,
   * and holdability.
   *
   * This method is the same as the `prepareStatement` method
   * above, but it allows the default result set
   * type, concurrency, and holdability to be overridden.

   * @param sql a `String` object that is the SQL statement to
   * *            be sent to the database; may contain one or more '?' IN
   * *            parameters
   * *
   * @param resultSetType one of the following `ResultSet`
   * *        constants:
   * *         `ResultSet.TYPE_FORWARD_ONLY`,
   * *         `ResultSet.TYPE_SCROLL_INSENSITIVE`, or
   * *         `ResultSet.TYPE_SCROLL_SENSITIVE`
   * *
   * @param resultSetConcurrency one of the following `ResultSet`
   * *        constants:
   * *         `ResultSet.CONCUR_READ_ONLY` or
   * *         `ResultSet.CONCUR_UPDATABLE`
   * *
   * @param resultSetHoldability one of the following `ResultSet`
   * *        constants:
   * *         `ResultSet.HOLD_CURSORS_OVER_COMMIT` or
   * *         `ResultSet.CLOSE_CURSORS_AT_COMMIT`
   * *
   * @return a new `PreparedStatement` object, containing the
   * *         pre-compiled SQL statement, that will generate
   * *         `ResultSet` objects with the given type,
   * *         concurrency, and holdability
   * *
   * @exception SQLException if a database access error occurs, this
   * * method is called on a closed connection
   * *            or the given parameters are not `ResultSet`
   * *            constants indicating type, concurrency, and holdability
   * *
   * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
   * * this method or this method is not supported for the specified result
   * * set type, result set holdability and result set concurrency.
   * *
   * @see ResultSet

   * @since 1.4
   */
  @Throws(SQLException::class)
  fun <R> prepareStatement(sql: String, resultSetType: Int,
                                  resultSetConcurrency: Int, resultSetHoldability: Int, block: (StatementHelper) -> R): R {
    return connection.prepareStatement(sql, resultSetType, resultSetConcurrency, resultSetHoldability).use { block(StatementHelper(it)) }
  }

  /**
   * Creates a default `PreparedStatement` object that has
   * the capability to retrieve auto-generated keys. The given constant
   * tells the driver whether it should make auto-generated keys
   * available for retrieval.  This parameter is ignored if the SQL statement
   * is not an `INSERT` statement, or an SQL statement able to return
   * auto-generated keys (the list of such statements is vendor-specific).
   *
   * Note: This method is optimized for handling
   * parametric SQL statements that benefit from precompilation. If
   * the driver supports precompilation,
   * the method `prepareStatement` will send
   * the statement to the database for precompilation. Some drivers
   * may not support precompilation. In this case, the statement may
   * not be sent to the database until the `PreparedStatement`
   * object is executed.  This has no direct effect on users; however, it does
   * affect which methods throw certain SQLExceptions.
   *
   * Result sets created using the returned `PreparedStatement`
   * object will by default be type `TYPE_FORWARD_ONLY`
   * and have a concurrency level of `CONCUR_READ_ONLY`.
   * The holdability of the created result sets can be determined by
   * calling [.getHoldability].

   * @param sql an SQL statement that may contain one or more '?' IN
   * *        parameter placeholders
   * *
   * @param autoGeneratedKeys a flag indicating whether auto-generated keys
   * *        should be returned; one of
   * *        `Statement.RETURN_GENERATED_KEYS` or
   * *        `Statement.NO_GENERATED_KEYS`
   * *
   * @return a new `PreparedStatement` object, containing the
   * *         pre-compiled SQL statement, that will have the capability of
   * *         returning auto-generated keys
   * *
   * @exception SQLException if a database access error occurs, this
   * *  method is called on a closed connection
   * *         or the given parameter is not a `Statement`
   * *         constant indicating whether auto-generated keys should be
   * *         returned
   * *
   * @exception SQLFeatureNotSupportedException if the JDBC driver does not support
   * * this method with a constant of Statement.RETURN_GENERATED_KEYS
   * *
   * @since 1.4
   */
  @Throws(SQLException::class)
  fun <R> prepareStatement(sql: String, autoGeneratedKeys: Int, block: (StatementHelper) -> R): R {
    return connection.prepareStatement(sql, autoGeneratedKeys).use { block(StatementHelper(it)) }
  }

  @Throws(SQLException::class)
  fun <R> prepareStatement(sql: String, columnIndexes: IntArray, block: (StatementHelper) -> R): R {
    return connection.prepareStatement(sql, columnIndexes).use { block(StatementHelper(it)) }
  }

  @Throws(SQLException::class)
  fun <R> prepareStatement(sql: String, columnNames: Array<out String>, block: (StatementHelper) -> R): R {
    return connection.prepareStatement(sql, columnNames).use { block(StatementHelper(it)) }
  }

  @Throws(SQLException::class)
  fun createClob(): Clob = connection.createClob()

  @Throws(SQLException::class)
  fun createBlob(): Blob = connection.createBlob()

  @Throws(SQLException::class)
  fun createNClob(): NClob = connection.createNClob()

  @Throws(SQLException::class)
  fun createSQLXML(): SQLXML = connection.createSQLXML()

  @Throws(SQLException::class)
  fun isValid(timeout: Int): Boolean = connection.isValid(timeout)

  @Throws(SQLClientInfoException::class)
  fun setClientInfo(name: String, value: String) = connection.setClientInfo(name, value)

  @Throws(SQLClientInfoException::class)
  fun setClientInfo(properties: Properties) = connection.setClientInfo(properties)

  @Throws(SQLException::class)
  fun getClientInfo(name: String): String = connection.getClientInfo(name)

  @Throws(SQLException::class)
  fun getClientInfo() = connection.clientInfo

  @Throws(SQLException::class)
  fun createArrayOf(typeName: String, elements: Array<Any>) = connection.createArrayOf(typeName, elements)

  @Throws(SQLException::class)
  fun createStruct(typeName: String, attributes: Array<Any>): Struct = connection.createStruct(typeName, attributes)

  //--------------------------JDBC 4.1 -----------------------------

  @Throws(SQLException::class)
  fun setSchema(schema: String) = connection.setSchema(schema)

  @Throws(SQLException::class)
  fun getSchema(): String = connection.schema

  @Throws(SQLException::class)
  fun abort(executor: Executor) = connection.abort(executor)

  @Throws(SQLException::class)
  fun setNetworkTimeout(executor: Executor, milliseconds: Int) = connection.setNetworkTimeout(executor, milliseconds)


  @Throws(SQLException::class)
  fun getNetworkTimeout(): Int = connection.networkTimeout


}


fun String.appendWarnings(warnings: Iterator<SQLWarning>): String {
  val result = StringBuilder().append(this).append(" - \n    ")
  warnings.asSequence().map { "${it.errorCode}: ${it.message}" }.joinTo(result, ",\n    ")
  return result.toString()
}

/**
 * Executes the given [block] function on this resource and then closes it down correctly whether an exception
 * is thrown or not.
 *
 * @param block a function to process this closable resource.
 * @return the result of [block] function on this closable resource.
 */
public inline fun <T : Connection, R> T.use(block: (T) -> R) = useHelper({ it.close() }, block)

public inline fun <T : Connection, R> T.useTransacted(block: (T) -> R): R = useHelper({ it.close() }) {
  it.autoCommit = false
  try {
    val result = block(it)
    it.commit()
    return result
  } catch (e: Exception) {
    it.rollback()
    throw e
  }

}


public inline fun  <T, R> T.useHelper(close: (T) -> Unit, block: (T) -> R): R {
  var closed = false
  try {
    return block(this)
  } catch (e: Exception) {
    closed = true
    try {
      close(this)
    } catch (closeException: Exception) {
      // drop for now.
    }
    throw e
  } finally {
    if (!closed) {
      close(this)
    }
  }
}