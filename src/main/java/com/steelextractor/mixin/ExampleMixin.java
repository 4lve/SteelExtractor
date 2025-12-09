package com.steelextractor.mixin;

import net.minecraft.server.level.ServerChunkCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(ServerChunkCache.class)
public class ExampleMixin {
	@Unique
	private static final Logger LOGGER = LoggerFactory.getLogger("SteelExtractor/ChunkTiming");

	@Unique
	private long tickStartTime;

	@Inject(at = @At("HEAD"), method = "tick")
	private void onTickStart(BooleanSupplier hasTimeLeft, boolean tickChunks, CallbackInfo info) {
		this.tickStartTime = System.nanoTime();
	}

	@Inject(at = @At("RETURN"), method = "tick")
	private void onTickEnd(BooleanSupplier hasTimeLeft, boolean tickChunks, CallbackInfo info) {
		long duration = System.nanoTime() - this.tickStartTime;
		double durationMs = duration / 1_000_000.0;
		if (durationMs > 2) {
			LOGGER.warn("ServerChunkCache.tick() took " + String.format("%.3f", durationMs) + " ms");
		}
	}
}