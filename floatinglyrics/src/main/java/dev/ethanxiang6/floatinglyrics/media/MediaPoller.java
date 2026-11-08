package dev.ethanxiang6.floatinglyrics.media;

import dev.ethanxiang6.floatinglyrics.FloatingLyricsClient;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** Owns the OS specific provider and keeps the latest reading available to the render thread. */
public final class MediaPoller {

	private final MediaProvider provider;
	private final Consumer<Optional<Snapshot>> listener;
	private volatile Snapshot latest;

	public MediaPoller(Supplier<String> preferredPlayer, Consumer<Optional<Snapshot>> listener) {
		this.provider = createProvider(preferredPlayer);
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

	private static MediaProvider createProvider(Supplier<String> preferredPlayer) {
		String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
		if (os.contains("linux")) {
			return new PlayerctlProvider(preferredPlayer);
		}
		if (os.contains("win")) {
			return new WindowsMediaProvider();
		}
		return null;
	}
}
