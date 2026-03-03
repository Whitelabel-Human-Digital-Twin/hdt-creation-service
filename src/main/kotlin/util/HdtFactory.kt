package com.example.com.util

import io.github.whdt.core.hdt.HumanDigitalTwin
import io.ktor.http.content.*

object HdtFactory {
    suspend fun fromMultipartData(mp: MultiPartData): List<HumanDigitalTwin> {
        mp.forEachPart {
            when(it) {
                is PartData.FormItem -> {

                }
                is PartData.FileItem -> {

                }
                else -> {

                }
            }
        }
        TODO()
    }
}