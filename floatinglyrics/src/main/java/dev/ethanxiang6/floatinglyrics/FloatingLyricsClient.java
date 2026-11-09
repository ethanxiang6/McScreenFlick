package dev.ethanxiang6.floatinglyrics;

import dev.ethanxiang6.floatinglyrics.config.LyricsConfig;
import dev.ethanxiang6.floatinglyrics.lyrics.LyricsService;
import dev.ethanxiang6.floatinglyrics.media.MediaPoller;
import dev.ethanxiang6.floatinglyrics.render.LyricsHud;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FloatingLyricsClient implements ClientModInitializer {

	public static final String MOD_ID = "floatinglyrics";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final long START_NANOS = System.nanoTime();

	private static LyricsConfig config;
	private static LyricsService lyrics;
	private static MediaPoller media;

	@Override
	public void onInitializeClient() {
		config = LyricsConfig.load(FabricLoader.getInstance().getConfigDir().resolve("floatinglyrics.json"));
		lyrics = new LyricsService();
		media = new MediaPoller(() -> config.preferredPlayer, () -> config.ignoredPlayers, lyrics::onSnapshot);
		media.start();

		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			media.stop();
			lyrics.shutdown();
		});

		HudElementRegistry.attachElementBefore(
				VanillaHudElements.CHAT,
				Identifier.fromNamespaceAndPath(MOD_ID, "lyrics"),
				LyricsHud::render);

		ClientCommandRegistrationCallback.EVENT.register(
				(dispatcher, buildContext) -> LyricsCommands.register(dispatcher));
	}

	/** Seconds since the mod was loaded. */
	public static double uptimeSeconds() {
		return (System.nanoTime() - START_NANOS) / 1_000_000_000.0;
	}

	public static LyricsConfig config() {
		return config;
	}

	public static LyricsService lyrics() {
		return lyrics;
	}

	public static MediaPoller media() {
		return media;
	}
}
