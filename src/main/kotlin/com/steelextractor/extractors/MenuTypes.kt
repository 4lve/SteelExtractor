package com.steelextractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.steelextractor.SteelExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

class MenuTypes : SteelExtractor.Extractor {
    private val logger = LoggerFactory.getLogger("steel-extractor-menutypes")

    override fun fileName(): String {
        return "menutypes.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val menusJson = JsonArray()

        for (screen in BuiltInRegistries.MENU) {
            menusJson.add(
                BuiltInRegistries.MENU.getKey(screen)!!.path
            )
        }

        return menusJson
    }
}
