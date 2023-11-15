package org.jetbrains.exposed.sql.functions

import org.jetbrains.exposed.sql.ArrayColumnType
import org.jetbrains.exposed.sql.CustomFunction
import org.jetbrains.exposed.sql.arrayParam

abstract class AllAnyFunction<T>(functionName: String, array: Array<T>) : CustomFunction<T>(functionName, ArrayColumnType, arrayParam(array))
class AllFunction<T>(array: Array<T>) : AllAnyFunction<T>("ALL", array)
class AnyFunction<T>(array: Array<T>) : AllAnyFunction<T>("ANY", array)
