package com.steelextractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.steelextractor.SteelExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.BlockItem
import org.slf4j.LoggerFactory

class Classes : SteelExtractor.Extractor {
    private val logger = LoggerFactory.getLogger("steel-extractor-block-classes")

    override fun fileName(): String {
        return "classes.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonObject()

        val blocksJson = JsonArray()
        for (block in BuiltInRegistries.BLOCK) {
            val blockJson = JsonObject()
            val name = BuiltInRegistries.BLOCK.getKey(block)?.path ?: "unknown"

            blockJson.addProperty("name", name)
            blockJson.addProperty("class", block.javaClass.simpleName)

            blocksJson.add(blockJson)
        }
        topLevelJson.add("blocks", blocksJson)

        val itemsJson = JsonArray()
        for (item in BuiltInRegistries.ITEM) {
            val itemJson = JsonObject()
            val name = BuiltInRegistries.ITEM.getKey(item)?.path ?: "unknown"

            itemJson.addProperty("name", name)
            itemJson.addProperty("class", item.javaClass.simpleName)

            if (item is BlockItem) {
                itemJson.addProperty("block", BuiltInRegistries.BLOCK.getKey(item.block)?.path)
            }

            itemsJson.add(itemJson)
        }
        topLevelJson.add("items", itemsJson)

        return topLevelJson
    }
}
