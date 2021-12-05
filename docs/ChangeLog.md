# 0.2.2
- Updated `idTable.batchUpdate` to return the number of updated rows.

# 0.2.1
- Opened up `IdTableWithDefaultScopeStriped` for extension
- Updated `README.md`

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
