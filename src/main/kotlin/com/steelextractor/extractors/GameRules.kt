package com.steelextractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.steelextractor.SteelExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import org.slf4j.LoggerFactory

class GameRulesExtractor : SteelExtractor.Extractor {
    private val logger = LoggerFactory.getLogger("steel-extractor-gamerules")

    override fun fileName(): String {
        return "game_rules.json"
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonObject()
        val gameRulesJson = JsonArray()

        for (gamerule in BuiltInRegistries.GAME_RULE) {
            val entry = JsonObject()
            entry.addProperty("name", gamerule.toString())
            entry.addProperty("type", gamerule.gameRuleType().toString())
            entry.addProperty("default", gamerule.defaultValue().toString())
            entry.addProperty("category", gamerule.category().id.toString())
            gameRulesJson.add(entry)
        }


        topLevelJson.add("game_rules", gameRulesJson)

        return topLevelJson
    }
}
