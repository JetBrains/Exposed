/** An exception thrown when a database migration fails. */
class ExposedMigrationException(exception: Exception, message: String) : RuntimeException(message, exception)
