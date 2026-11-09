package dev.ethanxiang6.floatinglyrics;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import dev.ethanxiang6.floatinglyrics.config.LyricsConfig;
import dev.ethanxiang6.floatinglyrics.lyrics.LyricsTrack;
import dev.ethanxiang6.floatinglyrics.media.Snapshot;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.network.chat.Component;

/** /floatinglyrics (alias /lyrics): quick controls that save straight to the config file. */
final class LyricsCommands {

	private LyricsCommands() {
	}

	static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
		dispatcher.register(build("floatinglyrics"));
		dispatcher.register(build("lyrics"));
	}

	private static LiteralArgumentBuilder<FabricClientCommandSource> build(String root) {
		return ClientCommands.literal(root)
				.then(ClientCommands.literal("toggle").executes(c -> {
					LyricsConfig cfg = FloatingLyricsClient.config();
					cfg.enabled = !cfg.enabled;
					cfg.save();
					return say(c, "Lyrics " + (cfg.enabled ? "on" : "off"));
				}))
				.then(ClientCommands.literal("refresh").executes(c -> {
					FloatingLyricsClient.lyrics().refetch();
					return say(c, "Looking up lyrics again");
				}))
				.then(ClientCommands.literal("status").executes(c -> status(c)))
				.then(ClientCommands.literal("offset")
						.then(ClientCommands.argument("ms", LongArgumentType.longArg(-10_000, 10_000))
								.executes(c -> {
									LyricsConfig cfg = FloatingLyricsClient.config();
									cfg.offsetMs = LongArgumentType.getLong(c, "ms");
									cfg.save();
									return say(c, "Timing offset " + cfg.offsetMs + " ms (positive shows lines earlier)");
								})))
				.then(ClientCommands.literal("scale")
						.then(ClientCommands.argument("size", FloatArgumentType.floatArg(0.5f, 4f))
								.executes(c -> {
									LyricsConfig cfg = FloatingLyricsClient.config();
									cfg.scale = FloatArgumentType.getFloat(c, "size");
									cfg.save();
									return say(c, "Scale " + cfg.scale);
								})))
				.then(ClientCommands.literal("pos")
						.then(ClientCommands.argument("x", FloatArgumentType.floatArg(0f, 1f))
								.then(ClientCommands.argument("y", FloatArgumentType.floatArg(0f, 1f))
										.executes(c -> {
											LyricsConfig cfg = FloatingLyricsClient.config();
											cfg.anchorX = FloatArgumentType.getFloat(c, "x");
											cfg.anchorY = FloatArgumentType.getFloat(c, "y");
											cfg.save();
											return say(c, "Position " + cfg.anchorX + ", " + cfg.anchorY);
										}))))
				.then(ClientCommands.literal("bob")
						.then(ClientCommands.argument("pixels", FloatArgumentType.floatArg(0f, 10f))
								.executes(c -> {
									LyricsConfig cfg = FloatingLyricsClient.config();
									cfg.bobPixels = FloatArgumentType.getFloat(c, "pixels");
									cfg.save();
									return say(c, "Float motion " + cfg.bobPixels + " px");
								})))
				.then(ClientCommands.literal("player")
						.then(ClientCommands.argument("name", StringArgumentType.word())
								.executes(c -> {
									LyricsConfig cfg = FloatingLyricsClient.config();
									String name = StringArgumentType.getString(c, "name");
									boolean auto = name.equalsIgnoreCase("auto") || name.equalsIgnoreCase("any");
									cfg.preferredPlayer = auto ? "" : name;
									cfg.save();
									return say(c, auto
											? "Player: automatic (browsers are skipped)"
											: "Preferred player: " + cfg.preferredPlayer);
								})));
	}

	private static int status(CommandContext<FabricClientCommandSource> c) {
		Snapshot song = FloatingLyricsClient.lyrics().song();
		if (song == null) {
			return say(c, "Nothing is playing");
		}
		LyricsTrack track = FloatingLyricsClient.lyrics().track();
		String lines = track == null ? "" : " (" + track.lines().size() + " lines)";
		return say(c, song.artist() + " - " + song.title() + " | lyrics: "
				+ FloatingLyricsClient.lyrics().status() + lines);
	}

	private static int say(CommandContext<FabricClientCommandSource> c, String message) {
		c.getSource().sendFeedback(Component.literal(message));
		return 1;
	}
}
