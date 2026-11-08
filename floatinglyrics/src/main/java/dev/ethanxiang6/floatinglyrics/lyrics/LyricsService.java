package dev.ethanxiang6.floatinglyrics.lyrics;

import dev.ethanxiang6.floatinglyrics.FloatingLyricsClient;
import dev.ethanxiang6.floatinglyrics.media.Snapshot;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Reacts to song changes, fetches lyrics off-thread, and exposes the lyrics for the current song. */
public final class LyricsService {

	public enum Status { IDLE, LOADING, READY, NOT_FOUND, ERROR }

	private final LrcLibClient client = new LrcLibClient();
	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "FloatingLyrics-fetch");
		t.setDaemon(true);
		return t;
	});

	private volatile String currentKey = "";
	private volatile Snapshot currentSong;
	private volatile LyricsTrack track;
	private volatile Status status = Status.IDLE;

	/** Called from the media thread with each new reading. */
	public void onSnapshot(Optional<Snapshot> snapshot) {
		if (snapshot.isEmpty()) {
			if (!currentKey.isEmpty()) {
				currentKey = "";
				currentSong = null;
				track = null;
				status = Status.IDLE;
			}
			return;
		}
		Snapshot s = snapshot.get();
		if (s.trackKey().equals(currentKey)) {
			return;
		}
		begin(s);
	}

	/** Forces a new lookup for the current song. */
	public void refetch() {
		Snapshot s = currentSong;
		if (s != null) {
			begin(s);
		}
	}

	private void begin(Snapshot s) {
		String key = s.trackKey();
		currentKey = key;
		currentSong = s;
		track = null;
		status = Status.LOADING;
		worker.submit(() -> {
			try {
				Optional<LyricsTrack> result = client.fetch(s);
				if (!key.equals(currentKey)) {
					return; // the song changed while we were waiting
				}
				track = result.orElse(null);
				status = result.isPresent() ? Status.READY : Status.NOT_FOUND;
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} catch (Exception e) {
				FloatingLyricsClient.LOGGER.warn("Lyrics lookup failed for '{}': {}", s.title(), e.getMessage());
				if (key.equals(currentKey)) {
					status = Status.ERROR;
				}
			}
		});
	}

	/** Lyrics for the current song, or {@code null} while loading or when none exist. */
	public LyricsTrack track() {
		return track;
	}

	public Status status() {
		return status;
	}

	public Snapshot song() {
		return currentSong;
	}

	public void shutdown() {
		worker.shutdownNow();
	}
}
