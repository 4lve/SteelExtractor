package com.steelextractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.steelextractor.SteelExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.syncher.EntityDataSerializer
import net.minecraft.network.syncher.EntityDataSerializers
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory
import java.lang.reflect.Modifier

class Entities : SteelExtractor.Extractor {
    private val logger = LoggerFactory.getLogger("steel-extractor-entities")

    // Set of entity types to extract, or null to extract all
    private val entityTypesToExtract: Set<String>? = setOf(
        "player",
    )

    override fun fileName(): String {
        return "entities.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonObject()

        // Extract EntityDataSerializers registration order
        topLevelJson.add("entity_data_serializers", extractEntityDataSerializers())

        // Extract entity types
        topLevelJson.add("entity_types", extractEntityTypes())

        return topLevelJson
    }

    private fun extractEntityDataSerializers(): JsonArray {
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

                logger.info("Serializer: ${field.name} = $id")
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

    private fun extractEntityTypes(): JsonArray {
        val entityTypesArray = JsonArray()

        for (entityType in BuiltInRegistries.ENTITY_TYPE) {
            val key = BuiltInRegistries.ENTITY_TYPE.getKey(entityType)
            val name = key?.path ?: "unknown"

            // Skip if we have a filter and this entity is not in it
            if (entityTypesToExtract != null && name !in entityTypesToExtract) {
                continue
            }

            val entityTypeJson = JsonObject()
            val id = BuiltInRegistries.ENTITY_TYPE.getId(entityType)

            entityTypeJson.addProperty("id", id)
            entityTypeJson.addProperty("name", name)

            // Get tracking range if available
            try {
                entityTypeJson.addProperty("client_tracking_range", entityType.clientTrackingRange())
                entityTypeJson.addProperty("update_interval", entityType.updateInterval())
            } catch (e: Exception) {
                logger.warn("Failed to get tracking info for ${key?.path}: ${e.message}")
            }

            entityTypesArray.add(entityTypeJson)
        }

        return entityTypesArray
    }
}
