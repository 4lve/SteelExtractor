package com.steelextractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.steelextractor.SteelExtractor
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.core.registries.Registries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.Property
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
                propsJson.add(getConstantName(BlockStateProperties::class.java, prop))
            }
            blockJson.add("properties", propsJson)

            val defaultProps = JsonArray()

            val state = block.defaultBlockState();
            for (prop in block.stateDefinition.properties) {
                val comparableValue = state.getValue(prop)
                val valueString = (prop as Property<Comparable<*>>).getName(comparableValue as Comparable<*>)

                val prefixedValueString = when (comparableValue) {
                    is Boolean -> "bool_$valueString"
                    is Enum<*> -> {
                        val fullClassName = comparableValue.javaClass.name // e.g., "net.minecraft.core.Direction$Axis$2"

                        // 1. Get substring after the last dot (package name)
                        //    Result: "Direction$Axis$2"
                        var classNamePart = fullClassName.substringAfterLast('.', "")

                        // 2. Remove any trailing anonymous class identifiers (e.g., "$2", "$1")
                        //    Result for "Direction$Axis$2": "Direction$Axis"
                        //    Result for "RedstoneSide": "RedstoneSide"
                        val anonymousClassRegex = "\\$\\d+$".toRegex() // Matches "$1", "$2", etc. at the end
                        classNamePart = classNamePart.replace(anonymousClassRegex, "")

                        // 3. If a '$' remains, take the part after the last '$'
                        //    Result for "Direction$Axis": "Axis"
                        //    Result for "RedstoneSide": "RedstoneSide"
                        val finalClassName = classNamePart.substringAfterLast('$', classNamePart) // Second 'classNamePart' is default if no '$'

                        "enum_${finalClassName}_$valueString"
                    }
                    is Number -> "int_$valueString"   // Catches Integer, Long, etc.
                    else -> "unknown_$valueString"    // Fallback for any other types
                }
                defaultProps.add(prefixedValueString)
            }

            blockJson.add("default_properties", defaultProps)

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