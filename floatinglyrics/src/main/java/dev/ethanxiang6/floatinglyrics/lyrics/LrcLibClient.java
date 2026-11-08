package dev.ethanxiang6.floatinglyrics.lyrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.ethanxiang6.floatinglyrics.media.Snapshot;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Fetches synced lyrics from LRCLIB (https://lrclib.net), a free community lyrics database that needs
 * no account or API key. Lyrics are fetched at runtime and kept in memory only.
 */
public final class LrcLibClient {

	private static final String BASE = "https://lrclib.net/api/";
	private static final String USER_AGENT = "FloatingLyrics/0.1 (Fabric client mod)";
	private static final Pattern TRAILING_NOISE = Pattern.compile(
			"(?i)\\s*([(\\[][^)\\]]*[)\\]]|-\\s*(remaster(ed)?|live|single|radio|mono|stereo|\\d{4}).*)$");

	private final HttpClient http = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(6))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();

	/** Looks up synced lyrics for the track. Blocking; call from a worker thread. */
	public Optional<LyricsTrack> fetch(Snapshot track) throws IOException, InterruptedException {
		Optional<LyricsTrack> exact = get(track.title(), track);
		if (exact.isPresent()) {
			return exact;
		}
		String cleaned = cleanTitle(track.title());
		if (!cleaned.equalsIgnoreCase(track.title())) {
			Optional<LyricsTrack> viaCleaned = get(cleaned, track);
			if (viaCleaned.isPresent()) {
				return viaCleaned;
			}
		}
		return search(cleaned, track);
	}

	/** Drops "(feat. X)", "- Remastered 2011" and similar suffixes that hurt matching. */
	public static String cleanTitle(String title) {
		String cleaned = TRAILING_NOISE.matcher(title).replaceFirst("").strip();
		return cleaned.isEmpty() ? title : cleaned;
	}

	private Optional<LyricsTrack> get(String title, Snapshot track) throws IOException, InterruptedException {
		StringBuilder url = new StringBuilder(BASE).append("get?track_name=").append(enc(title))
				.append("&artist_name=").append(enc(track.artist()));
		if (!track.album().isBlank()) {
			url.append("&album_name=").append(enc(track.album()));
		}
		if (track.durationMs() > 0) {
			url.append("&duration=").append(Math.round(track.durationMs() / 1000.0));
		}
		String body = request(url.toString());
		if (body == null) {
			return Optional.empty();
		}
		return toTrack(JsonParser.parseString(body), track);
	}

	private Optional<LyricsTrack> search(String title, Snapshot track) throws IOException, InterruptedException {
		String url = BASE + "search?track_name=" + enc(title) + "&artist_name=" + enc(track.artist());
		String body = request(url);
		if (body == null) {
			return Optional.empty();
		}
		JsonElement root = JsonParser.parseString(body);
		if (!root.isJsonArray()) {
			return Optional.empty();
		}
		JsonArray results = root.getAsJsonArray();
		JsonObject best = null;
		double bestGap = Double.MAX_VALUE;
		for (JsonElement e : results) {
			JsonObject o = e.getAsJsonObject();
			if (!hasSynced(o)) {
				continue;
			}
			double gap = track.durationMs() > 0 && o.has("duration") && !o.get("duration").isJsonNull()
					? Math.abs(o.get("duration").getAsDouble() - track.durationMs() / 1000.0)
					: 0.0;
			if (gap < bestGap) {
				bestGap = gap;
				best = o;
			}
		}
		// Reject matches whose length is far off: usually a different recording.
		if (best == null || bestGap > 6.0) {
			return Optional.empty();
		}
		return toTrack(best, track);
	}

	private String request(String url) throws IOException, InterruptedException {
		HttpRequest req = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(10))
				.header("User-Agent", USER_AGENT)
				.GET()
				.build();
		HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		if (res.statusCode() == 404) {
			return null;
		}
		if (res.statusCode() / 100 != 2) {
			throw new IOException("LRCLIB returned HTTP " + res.statusCode());
		}
		return res.body();
	}

	private static Optional<LyricsTrack> toTrack(JsonElement element, Snapshot track) {
		if (!element.isJsonObject()) {
			return Optional.empty();
		}
		JsonObject o = element.getAsJsonObject();
		if (!hasSynced(o)) {
			return Optional.empty();
		}
		var lines = LrcParser.parse(o.get("syncedLyrics").getAsString());
		if (lines.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new LyricsTrack(track.title(), track.artist(), lines));
	}

	private static boolean hasSynced(JsonObject o) {
		return o.has("syncedLyrics") && !o.get("syncedLyrics").isJsonNull()
				&& !o.get("syncedLyrics").getAsString().isBlank();
	}

	private static String enc(String s) {
		return URLEncoder.encode(s, StandardCharsets.UTF_8);
	}
}
