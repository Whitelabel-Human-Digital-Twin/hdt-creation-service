package io.github.whdt.crf.csv

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CsvSensorReaderTest {

    private fun read(csv: String): ParsedSensorCsv =
        CsvSensorReader().read(csv.byteInputStream(Charsets.UTF_8))

    @Test
    fun `reads header columns verbatim`() {
        val parsed = read(
            """
            sens1_x,sens1_y,sens1_z
            1.0,2.0,3.0
            """.trimIndent()
        )
        assertEquals(listOf("sens1_x", "sens1_y", "sens1_z"), parsed.columns)
    }

    @Test
    fun `reads one frame per data row`() {
        val parsed = read(
            """
            a,b
            1.0,2.0
            3.0,4.0
            5.5,6.5
            """.trimIndent()
        )
        assertEquals(3, parsed.frames.size)
        assertEquals(listOf(1.0, 2.0), parsed.frames[0])
        assertEquals(listOf(3.0, 4.0), parsed.frames[1])
        assertEquals(listOf(5.5, 6.5), parsed.frames[2])
    }

    @Test
    fun `tolerates surrounding whitespace and a trailing blank line`() {
        val parsed = read("a,b\n 1.0 , 2.0 \n3.0,4.0\n\n")
        assertEquals(2, parsed.frames.size)
        assertEquals(listOf(1.0, 2.0), parsed.frames[0])
    }

    @Test
    fun `rejects a non-numeric body cell`() {
        val ex = assertFailsWith<IllegalArgumentException> {
            read(
                """
                a,b
                1.0,oops
                """.trimIndent()
            )
        }
        assertTrue(ex.message!!.contains("oops"))
        assertTrue(ex.message!!.contains("b"))
    }

    @Test
    fun `empty body yields zero frames`() {
        val parsed = read("a,b,c")
        assertEquals(listOf("a", "b", "c"), parsed.columns)
        assertTrue(parsed.frames.isEmpty())
    }
}
