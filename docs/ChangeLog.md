# 0.2.0
Feature:
* Added the ability to temporarily strip a tables default scope via the `table.stripDefaultScope()` method.

# 0.1.0
Feature:
* Added the ability to set a defaultScope on a table as follows:
    ```kotlin
    object table : Table() {
        val tenantId = uuid("tenant_id")
        override val defaultScope = Op.build { tenantId eq currentTenantId() }
    }
    ```
