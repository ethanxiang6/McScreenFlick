package dev.ethanxiang6.floatinglyrics.media;

import dev.ethanxiang6.floatinglyrics.FloatingLyricsClient;

import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns the OS specific provider and keeps the latest reading available to the render thread. */
public final class MediaPoller {

	private final MediaProvider provider;
	private final Consumer<Optional<Snapshot>> listener;
	private volatile Snapshot latest;

	public MediaPoller(Supplier<String> preferredPlayer, Supplier<? extends Collection<String>> ignoredPlayers,
					   Consumer<Optional<Snapshot>> listener) {
		this.provider = createProvider(preferredPlayer, ignoredPlayers);
		this.listener = listener;
	}

	public void start() {
		if (provider == null) {
			FloatingLyricsClient.LOGGER.warn("No media provider for this operating system; lyrics are disabled.");
			return;
		}
		FloatingLyricsClient.LOGGER.info("Reading now playing from: {}", provider.name());
		provider.start(opt -> {
			latest = opt.orElse(null);
			listener.accept(opt);
		});
	}

	public void stop() {
		if (provider != null) {
			provider.stop();
		}
	}

	/** Latest reading, or {@code null} when nothing is playing. */
	public Snapshot latest() {
		return latest;
	}

	private static MediaProvider createProvider(Supplier<String> preferredPlayer,
												Supplier<? extends Collection<String>> ignoredPlayers) {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("linux")) {
			return new PlayerctlProvider(preferredPlayer, ignoredPlayers);
		}
		if (os.contains("win")) {
			return new WindowsMediaProvider(ignoredPlayers);
		}
		return null;
	}
}
