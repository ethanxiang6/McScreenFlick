package dev.ethanxiang6.floatinglyrics.lyrics;

import java.util.List;

/** Time synced lyrics for one song, sorted by time. */
public record LyricsTrack(String title, String artist, List<LyricLine> lines) {

	/** Index of the last line whose time is at or before {@code positionMs}, or -1 before the first line. */
	public int indexAt(long positionMs) {
		int lo = 0;
		int hi = lines.size() - 1;
		int found = -1;
		while (lo <= hi) {
			int mid = (lo + hi) >>> 1;
			if (lines.get(mid).timeMs() <= positionMs) {
				found = mid;
				lo = mid + 1;
			} else {
				hi = mid - 1;
			}
		}
		return found;
	}

	public boolean isEmpty() {
		return lines.isEmpty();
	}
}
