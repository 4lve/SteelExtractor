package com.steelextractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import com.steelextractor.SteelExtractor
import net.minecraft.core.registries.Registries
import net.minecraft.resources.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.storage.loot.LootTable
import org.slf4j.LoggerFactory

class LootTables : SteelExtractor.Extractor {
    private val logger = LoggerFactory.getLogger("steel-extractor-loot-tables")

    override fun fileName(): String {
        return "loot_tables.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonObject()
        val lootTablesJson = JsonArray()

        val registryAccess = server.reloadableRegistries().lookup()
        val lootTableRegistry = registryAccess.lookupOrThrow(Registries.LOOT_TABLE)
        val ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess())

        val elements = lootTableRegistry.listElements().iterator()
        while (elements.hasNext()) {
            val holder = elements.next()
            val lootTableJson = JsonObject()
            val key = holder.key() ?: continue
            val lootTable = holder.value()

            lootTableJson.addProperty("name", key.identifier().toString())

            // Encode the loot table to JSON using its codec
            val encodedResult = LootTable.DIRECT_CODEC.encodeStart(ops, lootTable)

            if (encodedResult.isSuccess) {
                lootTableJson.add("data", encodedResult.getOrThrow())
            } else {
                logger.warn("Failed to encode loot table ${key}: ${encodedResult.error()}")
                lootTableJson.addProperty("error", encodedResult.error().get().message())
            }

            lootTablesJson.add(lootTableJson)
        }

        topLevelJson.add("loot_tables", lootTablesJson)

        return topLevelJson
    }
}
