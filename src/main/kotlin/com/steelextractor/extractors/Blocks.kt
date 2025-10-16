package com.steelextractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.steelextractor.SteelExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import java.util.*

class Blocks : SteelExtractor.Extractor {

    override fun fileName(): String {
        return "blocks.json"
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

        val blocksJson = JsonArray()

        for (block in BuiltInRegistries.BLOCK) {
            val blockJson = JsonObject()
            blockJson.addProperty("id", BuiltInRegistries.BLOCK.getId(block))
            blockJson.addProperty("name", BuiltInRegistries.BLOCK.getKey(block).path)


            val propsJson = JsonArray()
            for (prop in block.stateDefinition.properties) {
                // Use the hashcode to map to a property later; the property names are not unique
                propsJson.add(getConstantName(BlockStateProperties::class.java, prop))
            }
            blockJson.add("properties", propsJson)

            blocksJson.add(blockJson)
        }

        val blockEntitiesJson = JsonArray()
        for (blockEntity in BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            blockEntitiesJson.add(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity)!!.path)
        }

        topLevelJson.add("block_entity_types", blockEntitiesJson)
        topLevelJson.add("blocks", blocksJson)

        return topLevelJson
    }
}