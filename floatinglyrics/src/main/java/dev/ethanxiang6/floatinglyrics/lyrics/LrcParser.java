package dev.ethanxiang6.floatinglyrics.lyrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the common {@code [mm:ss.xx] text} LRC format, including repeated stamps and the offset tag. */
public final class LrcParser {

	private static final Pattern STAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
	private static final Pattern OFFSET = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*]", Pattern.CASE_INSENSITIVE);
	private static final Pattern WORD_STAMP = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");

	private LrcParser() {
	}

	public static List<LyricLine> parse(String lrc) {
		List<LyricLine> out = new ArrayList<>();
		if (lrc == null || lrc.isBlank()) {
			return out;
		}
		long offsetMs = 0;
		for (String raw : lrc.split("\\R")) {
			String line = raw.strip();
			if (line.isEmpty()) {
				continue;
			}
			Matcher offset = OFFSET.matcher(line);
			if (offset.find()) {
				offsetMs = Long.parseLong(offset.group(1));
				continue;
			}
			Matcher m = STAMP.matcher(line);
			List<Long> times = new ArrayList<>();
			int end = 0;
			while (m.find(end) && m.start() == end) {
				times.add(toMs(m));
				end = m.end();
			}
			if (times.isEmpty()) {
				continue;
			}
			String text = WORD_STAMP.matcher(line.substring(end)).replaceAll("").strip();
			for (long t : times) {
				// A positive offset means the lyrics should appear earlier.
				out.add(new LyricLine(Math.max(0L, t - offsetMs), text));
			}
		}
		out.sort(Comparator.comparingLong(LyricLine::timeMs));
		return out;
	}

	private static long toMs(Matcher m) {
		long minutes = Long.parseLong(m.group(1));
		long seconds = Long.parseLong(m.group(2));
		long millis = 0;
		String frac = m.group(3);
		if (frac != null) {
			// "5" is tenths, "50" is hundredths, "500" is milliseconds: all mean 500 ms.
			millis = Long.parseLong((frac + "00").substring(0, 3));
		}
		return minutes * 60_000L + seconds * 1_000L + millis;
	}
}
