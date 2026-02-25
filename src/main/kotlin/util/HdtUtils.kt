package com.example.com.util

import io.github.whdt.core.hdt.HumanDigitalTwin
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
}