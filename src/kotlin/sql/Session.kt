package kotlin.sql

import java.sql.Connection
import java.util.regex.Pattern
import java.util.HashMap
import java.sql.PreparedStatement
import java.util.ArrayList
import kotlin.properties.Delegates

public class Key<T>()
open class UserDataHolder() {
    private val userdata = HashMap<Key<*>, Any?>()

    public fun <T:Any> getOrCreate(key: Key<T>, init: ()->T): T {
        if (userdata.containsKey(key)) {
            return userdata[key] as T
        }

        val new = init()
        userdata[key] = new
        return new
    }
}

class Session (val connection: Connection): UserDataHolder() {
    val identityQuoteString = connection.getMetaData()!!.getIdentifierQuoteString()!!
    val extraNameCharacters = connection.getMetaData()!!.getExtraNameCharacters()!!
    val identifierPattern = Pattern.compile("^[a-zA-Z_][a-zA-Z0-9_.]*$")
    val keywords = arrayListOf("key")
    val logger = CompositeSqlLogger()
    var statementCount: Int = 0
    var duration: Long = 0

    ;{
        logger.addLogger(Log4jSqlLogger())
        Session.threadLocal.set(this)
    }

    val vendor: DatabaseVendor by Delegates.blockingLazy {
        val url = connection.getMetaData()!!.getURL()!!
        when {
            url.startsWith("jdbc:mysql") -> DatabaseVendor.MySql
            url.startsWith("jdbc:oracle") -> DatabaseVendor.Oracle
            url.startsWith("jdbc:sqlserver") -> DatabaseVendor.SQLServer
            url.startsWith("jdbc:postgresql") -> DatabaseVendor.PostgreSQL
            url.startsWith("jdbc:h2") -> DatabaseVendor.H2
            else -> error("Unknown database type $url")
        }
    }

    fun <T> exec(stmt: String, args: List<Pair<ColumnType, Any?>> = listOf(), body: () -> T): T {
        logger.log(stmt, args)
        statementCount++

        val start = System.currentTimeMillis()
        val answer = body()
        duration += (System.currentTimeMillis() - start)

        return answer
    }

    fun createStatements (vararg tables: Table) : List<String> {
        val statements = ArrayList<String>()
        if (tables.size == 0)
            return statements

        val exists = HashMap<Table,Boolean>(tables.size)
        for (table in tables) {
            exists.put(table, table.exists())
            if (exists.get(table)!!)
                continue

            // create table
            val ddl = table.ddl
            statements.add(ddl)

            // create indices
            for (table_index in table.indices) {
                val alterTable = index(table_index.first, table_index.second)
                statements.add(alterTable)
            }
        }

        for (table in tables) {
            if (exists.get(table)!!)
                continue

            // foreign keys
            for (column in table.columns) {
                if (column.referee != null) {
                    val fKDdl = foreignKey(column);
                    statements.add(fKDdl)
                }
            }
        }

        return statements
    }

    fun create(vararg tables: Table) {
        val statements = createStatements(*tables)
        for (statement in statements) {
            exec(statement) {
                connection.createStatement()?.executeUpdate(statement)
            }
        }
    }

    fun drop(vararg tables: Table) {
        if (tables.size > 0) {
            for (table in tables) {
                val ddl = StringBuilder("DROP TABLE ${identity(table)}").toString()
                exec(ddl) {
                    connection.createStatement()?.executeUpdate(ddl)
                }
            }
        }
    }

    private fun needQuotes (identity: String) : Boolean {
        return keywords.contains (identity) || !identifierPattern.matcher(identity).matches()
    }

    private fun quoteIfNecessary (identity: String) : String {
        return (identity.split('.') map {quoteTokenIfNecessary(it)}).makeString(".")
    }

    private fun quoteTokenIfNecessary(token: String) : String {
        return if (needQuotes(token)) "$identityQuoteString$token$identityQuoteString" else token
    }

    fun identity(table: Table): String {
        return quoteIfNecessary(table.tableName)
    }

    fun fullIdentity(column: Column<*>): String {
        return "${quoteIfNecessary(column.table.tableName)}.${quoteIfNecessary(column.name)}"
    }

    fun identity(column: Column<*>): String {
        return quoteIfNecessary(column.name)
    }

    fun foreignKey(reference: Column<*>): String {
        val referee = reference.referee ?: error("$reference does not reference anything")

        return when (vendor) {
            DatabaseVendor.MySql, DatabaseVendor.Oracle,
            DatabaseVendor.SQLServer, DatabaseVendor.PostgreSQL,
            DatabaseVendor.H2 -> {
                "ALTER TABLE ${identity(reference.table)} ADD FOREIGN KEY (${identity(reference)}) REFERENCES ${identity(referee.table)}(${identity(referee)})"
            }
            else -> throw UnsupportedOperationException("Unsupported driver: " + vendor)
        }
    }

    fun index (columns: Array<Column<*>>, isUnique: Boolean): String {
        if (columns.size == 0) error("No columns to create index from")

        val table = columns[0].table
        return when (vendor) {
            DatabaseVendor.MySql, DatabaseVendor.Oracle,
            DatabaseVendor.SQLServer, DatabaseVendor.PostgreSQL,
            DatabaseVendor.H2 -> {
                var alter = StringBuilder()
                val indexType = if (isUnique) "UNIQUE " else ""
                alter.append("CREATE ${indexType}INDEX ${identity(table)}_${columns.map{ identity(it) }.makeString("_")} ON ${identity(table)} (")
                var isFirst = true
                for (c in columns) {
                    if (table != c.table) error("Columns from different tables cannot make index")
                    if (!isFirst) {
                        alter.append(", ")
                    }
                    isFirst = false
                    alter.append(identity(c))
                }
                alter.append(")")
                alter.toString()
            }
            else -> throw UnsupportedOperationException("Unsupported driver: " + vendor)
        }
    }

    fun autoIncrement(column: Column<*>): String {
        return when (vendor) {
            DatabaseVendor.MySql,
            DatabaseVendor.SQLServer,
            DatabaseVendor.H2 -> {
                "AUTO_INCREMENT"
            }
            else -> throw UnsupportedOperationException("Unsupported driver: " + vendor)
        }
    }

    private val statementsCache = HashMap<String, PreparedStatement>()
    fun prepareStatement(sql: String, autoincs: List<String>? = null): PreparedStatement {
        return statementsCache.getOrPut(sql) {
            if (autoincs == null) {
                connection.prepareStatement(sql)!!
            }
            else {
                connection.prepareStatement(sql, autoincs.copyToArray())!!
            }
        }
    }

    fun close() {
        for (stmt in statementsCache.values()) {
            stmt.close()
        }
        Session.threadLocal.set(null)
    }

    class object {
        val threadLocal = ThreadLocal<Session>()

        fun hasSession(): Boolean = threadLocal.get() != null

        fun get(): Session {
            return threadLocal.get() ?: error("No session in context. Use transaction?")
        }
    }
}
