package com.steelextractor

import com.google.gson.GsonBuilder
import com.google.gson.JsonElement
import com.steelextractor.extractors.Blocks
import com.steelextractor.extractors.Entities
import com.steelextractor.extractors.Items
import com.steelextractor.extractors.MenuTypes
import com.steelextractor.extractors.Packets
import kotlinx.io.IOException
import net.fabricmc.api.ModInitializer
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.MinecraftServer
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.entity.vault.VaultState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.slf4j.LoggerFactory
import java.io.FileWriter
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.system.measureTimeMillis

object SteelExtractor : ModInitializer {
    private val logger = LoggerFactory.getLogger("steel-extractor")

    override fun onInitialize() {
        // This code runs as soon as Minecraft is in a mod-load-ready state.
        // However, some things (like resources) may still be uninitialized.
        // Proceed with mild caution.
        logger.info("Hello Fabric world!")


        val test = BuiltInRegistries.DATA_COMPONENT_TYPE.byId(0)
        logger.info(test.toString())

        val test2 = BuiltInRegistries.FLUID.byId(2)
        logger.info(test2.toString())

        val extractors = arrayOf(
            Blocks(),
            Items(),
            Packets(),
            MenuTypes(),
            Entities()
        )

        val outputDirectory: Path
        try {
            outputDirectory = Files.createDirectories(Paths.get("steel_extractor_output"))
        } catch (e: IOException) {
            logger.info("Failed to create output directory.", e)
            return
        }

        val gson = GsonBuilder().disableHtmlEscaping().setPrettyPrinting().create()

        ServerLifecycleEvents.SERVER_STARTED.register(ServerLifecycleEvents.ServerStarted { server: MinecraftServer ->
            val timeInMillis = measureTimeMillis {
                for (ext in extractors) {
                    try {
                        val out = outputDirectory.resolve(ext.fileName())
                        val fileWriter = FileWriter(out.toFile(), StandardCharsets.UTF_8)
                        gson.toJson(ext.extract(server), fileWriter)
                        fileWriter.close()
                        logger.info("Wrote " + out.toAbsolutePath())
                    } catch (e: java.lang.Exception) {
                        logger.error(("Extractor for \"" + ext.fileName()) + "\" failed.", e)
                    }
                }
            }
            logger.info("Done, took ${timeInMillis}ms")
        })

    }

    interface Extractor {
        fun fileName(): String

        @Throws(Exception::class)
        fun extract(server: MinecraftServer): JsonElement
    }
}
