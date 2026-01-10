package com.steelextractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.steelextractor.SteelExtractor
import net.minecraft.network.syncher.EntityDataSerializer
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.lang.reflect.Modifier

class EntityDataSerializersExtractor : SteelExtractor.Extractor {
    private val logger = LoggerFactory.getLogger("steel-extractor-entity-data-serializers")

    override fun fileName(): String {
        return "entity_data_serializers.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val serializersArray = JsonArray()

        // Get all public static final fields from EntityDataSerializers
        val fields = EntityDataSerializers::class.java.declaredFields
            .filter { field ->
                Modifier.isPublic(field.modifiers) &&
                Modifier.isStatic(field.modifiers) &&
                Modifier.isFinal(field.modifiers) &&
                EntityDataSerializer::class.java.isAssignableFrom(field.type)
            }

        // For each serializer field, get its ID
        for (field in fields) {
            try {
                val serializer = field.get(null) as EntityDataSerializer<*>
                val id = EntityDataSerializers.getSerializedId(serializer)

                val serializerJson = JsonObject()
                serializerJson.addProperty("name", field.name)
                serializerJson.addProperty("id", id)
                serializersArray.add(serializerJson)
            } catch (e: Exception) {
                logger.warn("Failed to extract serializer ${field.name}: ${e.message}")
            }
        }

        // Sort by ID to get registration order
        val sortedArray = JsonArray()
        serializersArray.sortedBy { it.asJsonObject.get("id").asInt }
            .forEach { sortedArray.add(it) }

        return sortedArray
    }
}
