package com.ethanxiang6.screenflick;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import net.fabricmc.loader.api.FabricLoader;

/**
 * Settings stored in config/screenflick.json. Edit the file and restart the game.
 */
public final class FlickConfig {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	/** How far past the target's center the crosshair is flicked, in degrees. */
	public float flickAngle = 45.0f;

	/** "W_TAP" releases W after the hit. "S_TAP" releases W and taps S after the hit. */
	public String tapMode = "W_TAP";

	/** How many game ticks (1 tick = 50 ms) the tap lasts. */
	public int tapTicks = 2;

	/** Restart sprinting once the tap ends if you are still holding W. */
	public boolean autoResprint = true;

	/** Only targets within this many degrees of your crosshair (left or right) are picked. */
	public float targetCone = 70.0f;

	/** Show what each press did in the action bar. */
	public boolean showStatus = true;

	private static FlickConfig instance = new FlickConfig();

	public static FlickConfig get() {
		return instance;
	}

	public boolean isSTap() {
		return "S_TAP".equalsIgnoreCase(tapMode) || "S".equalsIgnoreCase(tapMode);
	}

	static void load() {
		Path path = FabricLoader.getInstance().getConfigDir().resolve("screenflick.json");

		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path)) {
				FlickConfig loaded = GSON.fromJson(reader, FlickConfig.class);
				if (loaded != null) {
					instance = loaded;
				}
			} catch (Exception e) {
				System.err.println("[screenflick] Could not read " + path + ", using defaults: " + e);
			}
		}

		instance.flickAngle = Math.max(0.0f, Math.min(180.0f, instance.flickAngle));
		instance.tapTicks = Math.max(0, Math.min(20, instance.tapTicks));
		instance.targetCone = Math.max(1.0f, Math.min(180.0f, instance.targetCone));
		if (instance.tapMode == null) {
			instance.tapMode = "W_TAP";
		}

		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path)) {
				GSON.toJson(instance, writer);
			}
		} catch (IOException e) {
			System.err.println("[screenflick] Could not write " + path + ": " + e);
		}
	}
}
