package dev.ethanxiang6.floatinglyrics.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** User settings, stored as JSON in the Fabric config folder and edited with /floatinglyrics commands. */
public final class LyricsConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	public boolean enabled = true;

	/** Horizontal centre of the lyrics as a fraction of the screen width (0 left, 1 right). */
	public float anchorX = 0.5f;
	/** Vertical centre of the current line as a fraction of the screen height (0 top, 1 bottom). */
	public float anchorY = 0.22f;
	/** Size multiplier for the current line. */
	public float scale = 1.6f;
	/** How far the floating motion drifts, in GUI pixels. 0 turns it off. */
	public float bobPixels = 2.0f;
	public boolean showPrevious = true;
	public boolean showNext = true;
	/** Shifts lyrics timing. Positive shows lines earlier, negative later. */
	public long offsetMs = 0;
	/** Optional MPRIS player name for Linux (for example "spotify"). Empty means any player. */
	public String preferredPlayer = "";
	/** RGB colour of the current line. */
	public int currentColor = 0xFFFFFF;
	/** RGB colour of the surrounding lines. */
	public int dimColor = 0xB8C4FF;

	private transient Path path;

	public static LyricsConfig load(Path file) {
		LyricsConfig cfg = null;
		if (Files.isRegularFile(file)) {
			try {
				cfg = GSON.fromJson(Files.readString(file, StandardCharsets.UTF_8), LyricsConfig.class);
			} catch (Exception ignored) {
				// fall through to defaults on a broken file
			}
		}
		if (cfg == null) {
			cfg = new LyricsConfig();
		}
		cfg.path = file;
		cfg.clamp();
		cfg.save();
		return cfg;
	}

	public void save() {
		if (path == null) {
			return;
		}
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(this), StandardCharsets.UTF_8);
		} catch (IOException ignored) {
			// config is best effort
		}
	}

	public void clamp() {
		anchorX = Math.max(0f, Math.min(1f, anchorX));
		anchorY = Math.max(0f, Math.min(1f, anchorY));
		scale = Math.max(0.5f, Math.min(4f, scale));
		bobPixels = Math.max(0f, Math.min(10f, bobPixels));
		if (preferredPlayer == null) {
			preferredPlayer = "";
		}
	}
}
