package io.github.whdt.crf.importer

import kotlin.test.Test
import kotlin.test.assertEquals

class CrfNameNormalizerTest {

    @Test
    fun `normalizes spaces and casing`() {
        assertEquals("follow_up_12_mesi", CrfNameNormalizer.normalize("Follow up 12 mesi"))
    }

    @Test
    fun `normalizes accented characters`() {
        assertEquals("eta_gestazionale", CrfNameNormalizer.normalize("Età gestazionale"))
    }

    @Test
    fun `collapses repeated separators`() {
        assertEquals("peso_alla_visita", CrfNameNormalizer.normalize("Peso   alla---visita"))
    }

    @Test
    fun `trims leading and trailing separators`() {
        assertEquals("id_paziente", CrfNameNormalizer.normalize("___ID PAZIENTE___"))
    }
}