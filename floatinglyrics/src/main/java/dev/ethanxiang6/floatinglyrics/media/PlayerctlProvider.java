package dev.ethanxiang6.floatinglyrics.media;

import dev.ethanxiang6.floatinglyrics.FloatingLyricsClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Linux: reads any MPRIS player (Spotify, Firefox, mpv, VLC, ...) through the {@code playerctl} command.
 * Install it with your package manager, for example {@code sudo dnf install playerctl}.
 */
public final class PlayerctlProvider implements MediaProvider {

	// playerctl reports position and length in microseconds in format strings.
	private static final String FORMAT =
			"{{status}}\t{{position}}\t{{mpris:length}}\t{{artist}}\t{{title}}\t{{album}}";

	private final Supplier<String> preferredPlayer;
	private ScheduledExecutorService executor;
	private boolean warnedMissing;

	public PlayerctlProvider(Supplier<String> preferredPlayer) {
		this.preferredPlayer = preferredPlayer;
	}

	@Override
	public void start(Consumer<Optional<Snapshot>> sink) {
		executor = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "FloatingLyrics-playerctl");
			t.setDaemon(true);
			return t;
		});
		executor.scheduleWithFixedDelay(() -> {
			try {
				sink.accept(poll());
			} catch (Throwable t) {
				FloatingLyricsClient.LOGGER.debug("playerctl poll failed", t);
			}
		}, 0, 1000, TimeUnit.MILLISECONDS);
	}

	@Override
	public void stop() {
		if (executor != null) {
			executor.shutdownNow();
		}
	}

	@Override
	public String name() {
		return "playerctl (MPRIS)";
	}

	private Optional<Snapshot> poll() {
		List<String> cmd = new ArrayList<>();
		cmd.add("playerctl");
		String preferred = preferredPlayer.get();
		if (preferred != null && !preferred.isBlank()) {
			cmd.add("--player=" + preferred.trim());
		} else {
			cmd.add("--all-players");
		}
		cmd.add("metadata");
		cmd.add("--format");
		cmd.add(FORMAT);

		List<String> lines = new ArrayList<>();
		Process process = null;
		try {
			process = new ProcessBuilder(cmd).redirectErrorStream(false).start();
			process.getErrorStream().close();
			try (BufferedReader reader = new BufferedReader(
					new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
				String line;
				while ((line = reader.readLine()) != null) {
					lines.add(line);
				}
			}
			if (!process.waitFor(2, TimeUnit.SECONDS)) {
				process.destroyForcibly();
			}
		} catch (IOException e) {
			if (!warnedMissing) {
				warnedMissing = true;
				FloatingLyricsClient.LOGGER.warn(
						"Could not run 'playerctl'. Install it to enable lyrics on Linux. ({})", e.getMessage());
			}
			return Optional.empty();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return Optional.empty();
		} finally {
			if (process != null && process.isAlive()) {
				process.destroyForcibly();
			}
		}
		return pick(lines, System.nanoTime());
	}

	/** Chooses the first playing player, otherwise the first paused one. Package-private for tests. */
	static Optional<Snapshot> pick(List<String> lines, long nowNanos) {
		Snapshot paused = null;
		for (String line : lines) {
			Snapshot s = Snapshot.parseTabLine(line, 0.001, nowNanos);
			if (s == null) {
				continue;
			}
			if (s.playing()) {
				return Optional.of(s);
			}
			if (paused == null) {
				paused = s;
			}
		}
		return Optional.ofNullable(paused);
	}
}
