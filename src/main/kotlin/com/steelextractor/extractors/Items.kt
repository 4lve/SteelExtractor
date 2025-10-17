package com.steelextractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.mojang.serialization.JsonOps
import com.steelextractor.SteelExtractor
import net.minecraft.core.component.DataComponentMap
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.RegistryOps
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.BlockItem
import net.minecraft.world.item.DoubleHighBlockItem
import net.minecraft.world.item.PlaceOnWaterBlockItem
import net.minecraft.world.item.ScaffoldingBlockItem
import org.slf4j.LoggerFactory

class Items : SteelExtractor.Extractor {
    private val logger = LoggerFactory.getLogger("steel-extractor-items")

    override fun fileName(): String {
        return "items.json"
    }

    fun getConstantName(clazz: Class<*>, value: Any?): String? {
        for (f in clazz.getFields()) {          // only public fields
            try {
                // we expect a static final constant, so no instance needed
                val fieldValue = f.get(null)
                if (fieldValue === value) {           // reference equality is what we want
                    return f.getName()
                }
            } catch (e: IllegalAccessException) {
                // shouldn't happen with getFields(), but ignore it just in case
            }
        }
        return null // no match found
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonObject()

        val itemsJson = JsonArray()


        for (item in BuiltInRegistries.ITEM) {
            val itemJson = JsonObject()


            itemJson.addProperty("id", BuiltInRegistries.ITEM.getId(item))
            itemJson.addProperty("name", BuiltInRegistries.ITEM.getKey(item).path)

            if (item is BlockItem) {
                itemJson.addProperty("blockItem", BuiltInRegistries.BLOCK.getKey(item.block).path)
            }

            val temp = DataComponentMap.CODEC.encodeStart(
                RegistryOps.create(JsonOps.INSTANCE, server.registryAccess()),
                item.components()
            ).getOrThrow()

            itemJson.add("components", temp)

            val isDouble = item is DoubleHighBlockItem
            val isScaffolding = item is ScaffoldingBlockItem
            val isWaterPlacable = item is PlaceOnWaterBlockItem

            itemJson.addProperty("isDouble", isDouble)
            itemJson.addProperty("isScaffolding", isScaffolding)
            itemJson.addProperty("isWaterPlacable", isWaterPlacable)


            itemsJson.add(itemJson)
        }


        topLevelJson.add("items", itemsJson)

        return topLevelJson
    }
}