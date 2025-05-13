package org.jetbrains.exposed.v1.tests.shared

import java.io.PrintWriter
import java.sql.Connection
import java.util.logging.Logger
import javax.sql.DataSource

internal open class DataSourceStub : DataSource {
    override fun setLogWriter(out: PrintWriter?): Unit = throw NotImplementedError()
    override fun getParentLogger(): Logger {
        throw NotImplementedError()
    }

    override fun setLoginTimeout(seconds: Int) {
        throw NotImplementedError()
    }

    override fun isWrapperFor(iface: Class<*>?): Boolean {
        throw NotImplementedError()
    }

    override fun getLogWriter(): PrintWriter {
        throw NotImplementedError()
    }

    override fun <T : Any?> unwrap(iface: Class<T>?): T {
        throw NotImplementedError()
    }

    override fun getConnection(): Connection {
        throw NotImplementedError()
    }

    override fun getConnection(username: String?, password: String?): Connection {
        throw NotImplementedError()
    }

    override fun getLoginTimeout(): Int {
        throw NotImplementedError()
    }
}
