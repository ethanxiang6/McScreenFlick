package dev.ethanxiang6.floatinglyrics.media;

/**
 * One reading of the system media session. Positions are measured at {@code sampledAtNanos}
 * and extrapolated forward while the track is playing.
 */
public record Snapshot(
		String title,
		String artist,
		String album,
		long durationMs,
		long positionMs,
		boolean playing,
		long sampledAtNanos
) {

	/** Playback position in milliseconds at the given {@link System#nanoTime()} instant. */
	public long positionAt(long nowNanos) {
		if (!playing) {
			return positionMs;
		}
		long elapsedMs = Math.max(0L, (nowNanos - sampledAtNanos) / 1_000_000L);
		long pos = positionMs + elapsedMs;
		return durationMs > 0 ? Math.min(pos, durationMs) : pos;
	}

	/** Identity of the track, ignoring position and play state. Used to detect song changes. */
	public String trackKey() {
		return artist.toLowerCase() + "\u0000" + title.toLowerCase() + "\u0000" + (durationMs / 1000L);
	}

	/**
	 * Parses a tab separated line: status, position, duration, artist, title, album.
	 * Position and duration are multiplied by {@code unitToMs} (0.001 for microseconds, 1 for milliseconds).
	 * Returns {@code null} for lines that are not a usable track.
	 */
	public static Snapshot parseTabLine(String line, double unitToMs, long sampledAtNanos) {
		if (line == null) {
			return null;
		}
		String[] f = line.split("\t", -1);
		if (f.length < 6) {
			return null;
		}
		String status = f[0].trim();
		boolean playing = status.equalsIgnoreCase("Playing");
		boolean paused = status.equalsIgnoreCase("Paused");
		if (!playing && !paused) {
			return null;
		}
		String title = f[4].trim();
		if (title.isEmpty()) {
			return null;
		}
		return new Snapshot(
				title,
				f[3].trim(),
				f[5].trim(),
				toMs(f[2], unitToMs),
				toMs(f[1], unitToMs),
				playing,
				sampledAtNanos
		);
	}

	private static long toMs(String raw, double unitToMs) {
		try {
			return Math.max(0L, (long) (Double.parseDouble(raw.trim()) * unitToMs));
		} catch (NumberFormatException e) {
			return 0L;
		}
	}
}
