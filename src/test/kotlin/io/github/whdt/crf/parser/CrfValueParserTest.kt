package io.github.whdt.crf.parser

import io.github.whdt.core.hdt.model.property.PropertyValue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class CrfValueParserTest {

    private val parser = CrfValueParser()

    @Test
    fun `parses boolean true`() {
        val value = parser.parse("true")
        assertTrue(value is PropertyValue.BooleanPropertyValue)
        assertEquals(true, value.value)
    }

    @Test
    fun `parses boolean false ignoring case`() {
        val value = parser.parse("FALSE")
        assertTrue(value is PropertyValue.BooleanPropertyValue)
        assertEquals(false, value.value)
    }

    @Test
    fun `parses int`() {
        val value = parser.parse("42")
        assertTrue(value is PropertyValue.IntPropertyValue)
        assertEquals(42, value.value)
    }

    @Test
    fun `parses long`() {
        val value = parser.parse("9999999999")
        assertTrue(value is PropertyValue.LongPropertyValue)
        assertEquals(9999999999L, value.value)
    }

    @Test
    fun `parses double with dot`() {
        val value = parser.parse("3.14")
        assertTrue(value is PropertyValue.DoublePropertyValue)
        assertEquals(3.14, value.value)
    }

    @Test
    fun `parses double with comma`() {
        val value = parser.parse("3,14")
        assertTrue(value is PropertyValue.DoublePropertyValue)
        assertEquals(3.14, value.value)
    }

    @Test
    fun `falls back to string`() {
        val value = parser.parse("N/A")
        assertTrue(value is PropertyValue.StringPropertyValue)
        assertEquals("N/A", value.value)
    }

    @Test
    fun `fails on blank input`() {
        assertFailsWith<IllegalArgumentException> {
            parser.parse("   ")
        }
    }
}