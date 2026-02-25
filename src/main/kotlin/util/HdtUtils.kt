package com.example.com.util

import io.github.whdt.core.hdt.HumanDigitalTwin
import io.github.whdt.core.hdt.interfaces.digital.HttpDigitalInterface
import io.github.whdt.core.hdt.interfaces.digital.MqttDigitalInterface
import io.github.whdt.core.hdt.interfaces.physical.MqttPhysicalInterface
import io.github.whdt.core.hdt.model.Model
import io.github.whdt.core.hdt.model.id.HdtId
import io.github.whdt.core.hdt.model.property.Property

object HdtUtils {
    fun hdtFrom(id: String, properties: List<Property>): HumanDigitalTwin {
        val hdtId = HdtId(id)
        val pI = MqttPhysicalInterface(
            hdtId = hdtId,
        )
        val dI = MqttDigitalInterface(
            hdtId = hdtId,
        )
        val model = Model(properties)
        return HumanDigitalTwin(
            hdtId = hdtId,
            models = listOf(model),
            physicalInterfaces = listOf(pI),
            digitalInterfaces = listOf(dI),
        )
    }
    fun setupHdt(hdt: HumanDigitalTwin): HumanDigitalTwin {
        HdtRegistry.register(hdt.hdtId.id)
        if(hdt.digitalInterfaces.any { it !is HttpDigitalInterface }) {
            val httpDI = HttpDigitalInterface(
                hdtId = hdt.hdtId,
                host = HdtRegistry.HDT_HTTP_HOST,
                port = HdtRegistry.getPort(hdt.hdtId.id)!!,
                id = "${hdt.hdtId}-http-di",
            )
            return hdt.copy(digitalInterfaces = hdt.digitalInterfaces + httpDI)
        }
        return hdt
    }
}