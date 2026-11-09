package dev.ethanxiang6.floatinglyrics.media;

import dev.ethanxiang6.floatinglyrics.FloatingLyricsClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Linux: reads any MPRIS player (Spotify, mpv, VLC, ...) through the {@code playerctl} command.
 * Browsers are skipped by default so a YouTube or Twitch tab does not steal the lyrics.
 * Install playerctl with your package manager, for example {@code sudo dnf install playerctl}.
 */
public final class PlayerctlProvider implements MediaProvider {

	// playerName first so we can filter, then position and length in microseconds.
	private static final String FORMAT =
			"{{playerName}}\t{{status}}\t{{position}}\t{{mpris:length}}\t{{artist}}\t{{title}}\t{{album}}";

	private final Supplier<String> preferredPlayer;
	private final Supplier<? extends Collection<String>> ignoredPlayers;
	private ScheduledExecutorService executor;
	private boolean warnedMissing;

	public PlayerctlProvider(Supplier<String> preferredPlayer, Supplier<? extends Collection<String>> ignoredPlayers) {
		this.preferredPlayer = preferredPlayer;
		this.ignoredPlayers = ignoredPlayers;
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
		boolean explicit = preferred != null && !preferred.isBlank();
		if (explicit) {
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
		// A player the user named explicitly is never filtered out.
		Collection<String> ignored = explicit ? List.of() : ignoredPlayers.get();
		return pick(lines, System.nanoTime(), ignored);
	}

	/**
	 * Chooses the first playing player, otherwise the first paused one, skipping ignored players.
	 * Each line starts with the player name. Package-private for tests.
	 */
	static Optional<Snapshot> pick(List<String> lines, long nowNanos, Collection<String> ignored) {
		Snapshot paused = null;
		for (String line : lines) {
			int tab = line.indexOf('\t');
			if (tab < 0) {
				continue;
			}
			if (isIgnored(line.substring(0, tab), ignored)) {
				continue;
			}
			Snapshot s = Snapshot.parseTabLine(line.substring(tab + 1), 0.001, nowNanos);
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

	/** Matches the base player name, so "firefox.instance_1_123" is caught by "firefox". */
	static boolean isIgnored(String playerName, Collection<String> ignored) {
		if (ignored == null || ignored.isEmpty()) {
			return false;
		}
		String base = playerName.toLowerCase(Locale.ROOT).trim();
		int dot = base.indexOf('.');
		if (dot > 0) {
			base = base.substring(0, dot);
		}
		for (String item : ignored) {
			if (item != null && base.equals(item.toLowerCase(Locale.ROOT).trim())) {
				return true;
			}
		}
		return false;
	}
}
