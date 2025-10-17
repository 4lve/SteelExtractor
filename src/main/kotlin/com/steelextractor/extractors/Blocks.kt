package com.steelextractor.extractors

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.steelextractor.SteelExtractor
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.resources.ResourceKey
import net.minecraft.server.MinecraftServer
import net.minecraft.world.flag.FeatureFlagSet
import net.minecraft.world.level.EmptyBlockGetter
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.SoundType
import net.minecraft.world.level.block.state.BlockBehaviour
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument
import net.minecraft.world.level.block.state.properties.Property
import net.minecraft.world.level.material.PushReaction
import net.minecraft.world.phys.AABB
import org.slf4j.LoggerFactory
import java.lang.reflect.Field
import java.util.*
import kotlin.reflect.full.declaredMemberProperties
import kotlin.reflect.jvm.isAccessible

class Blocks : SteelExtractor.Extractor {
    private val logger = LoggerFactory.getLogger("steel-extractor-blocks")
    private val shapes: LinkedHashMap<AABB, Int> = LinkedHashMap()


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

    /**
     * Reads the value of a private field from an object using Java Reflection.
     *
     * @param obj The object instance from which to read the private field.
     * @param fieldName The name of the private field to read.
     * @return The value of the private field, or null if the field is not found
     *         or an access error occurs.
     * @throws IllegalArgumentException if the provided object or fieldName is null or empty.
     */
    inline fun <reified T : Any> getPrivateFieldValue(obj: Any, fieldName: String): T? {
        require(fieldName.isNotBlank()) { "Field name cannot be blank." }

        return try {
            val field: Field = obj.javaClass.getDeclaredField(fieldName)
            field.isAccessible = true // Make the private field accessible
            field.get(obj) as T? // Cast to the expected type T
        } catch (e: NoSuchFieldException) {
            println("Error: Private field '$fieldName' not found in class ${obj.javaClass.simpleName}. ${e.message}")
            null
        } catch (e: IllegalAccessException) {
            println("Error: Cannot access private field '$fieldName' in class ${obj.javaClass.simpleName}. ${e.message}")
            null
        } catch (e: ClassCastException) {
            println("Error: Cannot cast private field '$fieldName' to expected type ${T::class.simpleName}. ${e.message}")
            null
        }
    }

    fun createBlockStatesJson(block: Block): JsonObject {
        val statesContainerJson = JsonObject()

        val possibleStates = block.stateDefinition.possibleStates

        if (possibleStates.isEmpty()) {
            statesContainerJson.add("default", JsonArray())
            statesContainerJson.add("overwrites", JsonArray())
            return statesContainerJson
        }

        // --- NEW LOGIC: Find the most frequent collision shape ---
        val collisionShapeCounts = LinkedHashMap<List<AABB>, Int>()
        val collisionShapeMap = LinkedHashMap<List<AABB>, JsonArray>() // To store pre-calculated JsonArray for shapes

        for (state in possibleStates) {
            val collisionShapeAabbs = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()
            val currentShapeJsonArray = JsonArray()
            for (box in collisionShapeAabbs) {
                val idx = shapes.putIfAbsent(box, shapes.size)
                currentShapeJsonArray.add(Objects.requireNonNullElseGet(idx) { shapes.size - 1 })
            }

            // A List<AABB> needs proper equals/hashCode for map keys
            // Fortunately, List and AABB (assuming it's the Minecraft AABB)
            // generally implement equals/hashCode correctly for value comparison.
            collisionShapeCounts.merge(collisionShapeAabbs, 1, Int::plus)
            collisionShapeMap.putIfAbsent(collisionShapeAabbs, currentShapeJsonArray)
        }

        // Find the most frequent collision shape
        val mostFrequentShapeEntry = collisionShapeCounts.maxByOrNull { it.value }

        val defaultCollisionShapeAabbs: List<AABB>
        val defaultCollisionShapeIdxs: JsonArray

        if (mostFrequentShapeEntry != null) {
            defaultCollisionShapeAabbs = mostFrequentShapeEntry.key
            defaultCollisionShapeIdxs = collisionShapeMap[defaultCollisionShapeAabbs]!!
        } else {
            // Should not happen if possibleStates is not empty, but as a fallback
            defaultCollisionShapeAabbs = emptyList()
            defaultCollisionShapeIdxs = JsonArray()
        }
        // --- END NEW LOGIC ---

        statesContainerJson.add("default", defaultCollisionShapeIdxs)


        // 2. Build the overwrites array
        val overwritesArray = JsonArray()

        for (i in 0 until possibleStates.size) {
            val state = possibleStates[i]
            val offset = i

            val currentStateCollisionShapeAabbs = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).toAabbs()

            // Check if the current state's collision shapes differ from the chosen default
            val differsFromDefault = if (currentStateCollisionShapeAabbs.size != defaultCollisionShapeAabbs.size) {
                true
            } else {
                !currentStateCollisionShapeAabbs.zip(defaultCollisionShapeAabbs).all { (current, default) ->
                    current == default
                }
            }

            if (differsFromDefault) {
                val overwriteStateJson = JsonObject()
                // Retrieve the already calculated JsonArray for this specific shape from our map
                val collisionShapeIdxsJson = collisionShapeMap[currentStateCollisionShapeAabbs]
                    ?: run {
                        // This case should ideally not happen if every shape was put into collisionShapeMap
                        // but as a fallback, generate it again
                        logger.error("Collision shape not found in map for state offset $offset. Recalculating.")
                        val tempArray = JsonArray()
                        for (box in currentStateCollisionShapeAabbs) {
                            val idx = shapes.putIfAbsent(box, shapes.size)
                            tempArray.add(Objects.requireNonNullElseGet(idx) { shapes.size - 1 })
                        }
                        tempArray
                    }


                overwriteStateJson.addProperty("offset", offset)
                overwriteStateJson.add("collision_shapes", collisionShapeIdxsJson)
                overwritesArray.add(overwriteStateJson)
            }
        }

        statesContainerJson.add("overwrites", overwritesArray)
        return statesContainerJson
    }

    override fun extract(server: MinecraftServer): JsonElement {
        val topLevelJson = JsonObject()

        val blocksJson = JsonArray()


        for (block in BuiltInRegistries.BLOCK) {
            val blockJson = JsonObject()
            blockJson.addProperty("id", BuiltInRegistries.BLOCK.getId(block))
            blockJson.addProperty("name", BuiltInRegistries.BLOCK.getKey(block).path)


            val behaviourProps = (block as BlockBehaviour).properties()




            // Add the differing BlockBehaviour.Properties to blockJson
            val behaviourJson = JsonObject()
            behaviourJson.addProperty("hasCollision", getPrivateFieldValue<Boolean>(behaviourProps, "hasCollision"))
            behaviourJson.addProperty("canOcclude", getPrivateFieldValue<Boolean>(behaviourProps, "canOcclude"))

            behaviourJson.addProperty("explosionResistance", getPrivateFieldValue<Float>(behaviourProps, "explosionResistance"))
            behaviourJson.addProperty("isRandomlyTicking", getPrivateFieldValue<Boolean>(behaviourProps, "isRandomlyTicking"))

            behaviourJson.addProperty("forceSolidOff", getPrivateFieldValue<Boolean>(behaviourProps, "forceSolidOff"))
            behaviourJson.addProperty("forceSolidOn", getPrivateFieldValue<Boolean>(behaviourProps, "forceSolidOn"))

            behaviourJson.addProperty("pushReaction", getPrivateFieldValue<PushReaction>(behaviourProps, "pushReaction").toString())


            //val soundType = getPrivateFieldValue<SoundType>(behaviourProps, "soundType")
            //behaviourJson.addProperty("soundType", soundType?.breakSound?.location?.toString()) // Assuming you want the enum name

            behaviourJson.addProperty("friction", getPrivateFieldValue<Float>(behaviourProps, "friction"))
            behaviourJson.addProperty("speedFactor", getPrivateFieldValue<Float>(behaviourProps, "speedFactor"))
            behaviourJson.addProperty("jumpFactor", getPrivateFieldValue<Float>(behaviourProps, "jumpFactor"))
            behaviourJson.addProperty("dynamicShape", getPrivateFieldValue<Boolean>(behaviourProps, "dynamicShape"))

            behaviourJson.addProperty("destroyTime", getPrivateFieldValue<Float>(behaviourProps, "destroyTime"))
            behaviourJson.addProperty("explosionResistance", getPrivateFieldValue<Float>(behaviourProps, "explosionResistance"))
            behaviourJson.addProperty("ignitedByLava", getPrivateFieldValue<Boolean>(behaviourProps, "ignitedByLava"))

            behaviourJson.addProperty("liquid", getPrivateFieldValue<Boolean>(behaviourProps, "liquid"))
            behaviourJson.addProperty("isAir", getPrivateFieldValue<Boolean>(behaviourProps, "isAir"))
            behaviourJson.addProperty("isRedstoneConductor", getPrivateFieldValue<Boolean>(behaviourProps, "isRedstoneConductor"))
            behaviourJson.addProperty("isSuffocating", getPrivateFieldValue<Boolean>(behaviourProps, "isSuffocating"))
            behaviourJson.addProperty("requiresCorrectToolForDrops", getPrivateFieldValue<Boolean>(behaviourProps, "requiresCorrectToolForDrops"))
            behaviourJson.addProperty("instrument", getPrivateFieldValue<NoteBlockInstrument>(behaviourProps, "instrument").toString())
            behaviourJson.addProperty("replaceable", getPrivateFieldValue<Boolean>(behaviourProps, "replaceable"))


            val statesStructureJson = createBlockStatesJson(block)
            blockJson.add("collisions", statesStructureJson)

            // Only add if there are actual differences
            if (behaviourJson.size() > 0) {
                blockJson.add("behavior_properties", behaviourJson)
            }

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

        val shapesJson = JsonArray()
        for (shape in shapes.keys) {
            val shapeJson = JsonObject()
            val min = JsonArray()
            min.add(shape.minX)
            min.add(shape.minY)
            min.add(shape.minZ)
            val max = JsonArray()
            max.add(shape.maxX)
            max.add(shape.maxY)
            max.add(shape.maxZ)
            shapeJson.add("min", min)
            shapeJson.add("max", max)
            shapesJson.add(shapeJson)
        }

        topLevelJson.add("shapes", shapesJson)

        val blockEntitiesJson = JsonArray()
        for (blockEntity in BuiltInRegistries.BLOCK_ENTITY_TYPE) {
            blockEntitiesJson.add(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(blockEntity)!!.path)
        }

        topLevelJson.add("block_entity_types", blockEntitiesJson)
        topLevelJson.add("blocks", blocksJson)

        return topLevelJson
    }
}