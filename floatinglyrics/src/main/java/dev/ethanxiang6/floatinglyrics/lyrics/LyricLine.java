package dev.ethanxiang6.floatinglyrics.lyrics;

/** One timed line. An empty {@code text} marks an instrumental gap. */
public record LyricLine(long timeMs, String text) {
}
