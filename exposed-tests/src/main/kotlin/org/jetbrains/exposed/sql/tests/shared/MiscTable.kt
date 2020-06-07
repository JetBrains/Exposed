package org.jetbrains.exposed.sql.tests.shared

import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.statements.InsertStatement
import java.math.BigDecimal
import kotlin.test.assertEquals

open class MiscTable : Table() {
    val by = byte("by")
    val byn = byte("byn").nullable()

    val sm = short("sm")
    val smn = short("smn").nullable()

    val n = integer("n")
    val nn = integer("nn").nullable()

    val e = enumeration("e", E::class)
    val en = enumeration("en", E::class).nullable()

    val es = enumerationByName("es", 5, E::class)
    val esn = enumerationByName("esn", 5, E::class).nullable()

    val c = varchar("c", 4)
    val cn = varchar("cn", 4).nullable()

    val s = varchar("s", 100)
    val sn = varchar("sn", 100).nullable()

    val dc = decimal("dc", 12, 2)
    val dcn = decimal("dcn", 12, 2).nullable()

    val fcn = float("fcn").nullable()
    val dblcn = double("dblcn").nullable()

    val char = char("char").nullable()

    enum class E {
        ONE,
        TWO,
        THREE
    }
}

fun MiscTable.checkRow(
    row: ResultRow,
    by: Byte, byn: Byte?,
    sm: Short, smn: Short?,
    n: Int, nn: Int?, e: MiscTable.E, en: MiscTable.E?,
    es: MiscTable.E, esn: MiscTable.E?,
    c: String, cn: String?, s: String, sn: String?,
    dc: BigDecimal, dcn: BigDecimal?, fcn: Float?, dblcn: Double?
) {
    assertEquals(row[this.by], by)
    assertEquals(row[this.byn], byn)
    assertEquals(row[this.sm], sm)
    assertEquals(row[this.smn], smn)
    assertEquals(row[this.n], n)
    assertEquals(row[this.nn], nn)
    assertEquals(row[this.e], e)
    assertEquals(row[this.en], en)
    assertEquals(row[this.es], es)
    assertEquals(row[this.esn], esn)
    assertEquals(row[this.c], c)
    assertEquals(row[this.cn], cn)
    assertEquals(row[this.s], s)
    assertEquals(row[this.sn], sn)
    assertEquals(row[this.dc], dc)
    assertEquals(row[this.dcn], dcn)
    assertEquals(row[this.fcn], fcn)
    assertEquals(row[this.dblcn], dblcn)
}

fun MiscTable.checkInsert(row: InsertStatement<Number>,
                                by: Byte, byn: Byte?,
                                sm: Short, smn: Short?,
                                n: Int, nn: Int?, e: MiscTable.E, en: MiscTable.E?,
                                es: MiscTable.E, esn: MiscTable.E?, s: String, sn: String?,
                                dc: BigDecimal, dcn: BigDecimal?, fcn: Float?, dblcn: Double?) {
    assertEquals(row[this.by], by)
    assertEquals(row[this.byn], byn)
    assertEquals(row[this.sm], sm)
    assertEquals(row[this.smn], smn)
    assertEquals(row[this.n], n)
    assertEquals(row[this.nn], nn)
    assertEquals(row[this.e], e)
    assertEquals(row[this.en], en)
    assertEquals(row[this.es], es)
    assertEquals(row[this.esn], esn)
    assertEquals(row[this.s], s)
    assertEquals(row[this.sn], sn)
    assertEquals(row[this.dc], dc)
    assertEquals(row[this.dcn], dcn)
    assertEquals(row[this.fcn], fcn)
    assertEquals(row[this.dblcn], dblcn)
}