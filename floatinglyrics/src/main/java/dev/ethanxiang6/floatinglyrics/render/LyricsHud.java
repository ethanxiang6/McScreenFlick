package dev.ethanxiang6.floatinglyrics.render;

import dev.ethanxiang6.floatinglyrics.FloatingLyricsClient;
import dev.ethanxiang6.floatinglyrics.config.LyricsConfig;
import dev.ethanxiang6.floatinglyrics.lyrics.LyricLine;
import dev.ethanxiang6.floatinglyrics.lyrics.LyricsTrack;
import dev.ethanxiang6.floatinglyrics.media.Snapshot;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the lyrics as a small floating stack: previous line fading out, current line growing in,
 * next line waiting underneath. All Minecraft drawing calls live in this file so a future API
 * change only needs fixing here.
 */
public final class LyricsHud {

	/** Height of one text row in unscaled GUI units (vanilla font). */
	private static final int ROW_H = 9;
	private static final double LINE_GAP_PX = 6.0;
	private static final long FADE_IN_MS = 300;
	private static final long TAIL_FADE_MS = 450;
	private static final long HOLD_MS = 9_000;
	private static final long NEXT_LEAD_MS = 10_000;
	private static final float DIM_ALPHA = 0.38f;
	private static final float DIM_SCALE = 0.72f;

	private static LyricsTrack lastTrack;
	private static int lastIdx = Integer.MIN_VALUE;
	private static long changeNanos;

	private LyricsHud() {
	}

	public static void render(GuiGraphicsExtractor g, DeltaTracker delta) {
		LyricsConfig cfg = FloatingLyricsClient.config();
		if (cfg == null || !cfg.enabled) {
			return;
		}
		Snapshot song = FloatingLyricsClient.media().latest();
		LyricsTrack track = FloatingLyricsClient.lyrics().track();
		if (song == null || track == null || track.isEmpty()) {
			lastTrack = null;
			renderStatus(g, cfg, song);
			return;
		}

		long now = System.nanoTime();
		long pos = song.positionAt(now) + cfg.offsetMs;
		int idx = track.indexAt(pos);
		if (track != lastTrack || idx != lastIdx) {
			lastTrack = track;
			lastIdx = idx;
			changeNanos = now;
		}

		List<LyricLine> lines = track.lines();
		int size = lines.size();
		String curText = idx >= 0 ? lines.get(idx).text() : "";
		String prevText = idx - 1 >= 0 ? lines.get(idx - 1).text() : "";
		int nextIdx = idx + 1;
		String nextText = nextIdx < size ? lines.get(nextIdx).text() : "";

		double sinceChangeMs = (now - changeNanos) / 1_000_000.0;
		float e = easeOutCubic(clamp01(sinceChangeMs / FADE_IN_MS));

		// Current line: fade out near its end so long instrumentals clear the screen.
		float curTail = 1f;
		if (idx >= 0) {
			long start = lines.get(idx).timeMs();
			long until = start + HOLD_MS;
			if (nextIdx < size) {
				until = Math.min(until, lines.get(nextIdx).timeMs());
			}
			curTail = clamp01((until - pos) / (double) TAIL_FADE_MS);
		}

		float curAlpha = (DIM_ALPHA + (1f - DIM_ALPHA) * e) * curTail;
		float prevAlpha = (1f - (1f - DIM_ALPHA) * e);
		if (curText.isEmpty()) {
			prevAlpha *= 1f - clamp01(sinceChangeMs / 600.0);
		}
		float nextAlpha = 0f;
		if (nextIdx < size) {
			long untilNext = lines.get(nextIdx).timeMs() - pos;
			nextAlpha = DIM_ALPHA * clamp01((NEXT_LEAD_MS - untilNext) / 800.0);
		}
		if (!cfg.showPrevious) {
			prevAlpha = 0f;
		}
		if (!cfg.showNext) {
			nextAlpha = 0f;
		}

		float base = cfg.scale;
		float dim = base * DIM_SCALE;
		float curScale = lerp(dim, base, e);
		float prevScale = lerp(base, dim, e);

		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int guiW = mc.getWindow().getGuiScaledWidth();
		int guiH = mc.getWindow().getGuiScaledHeight();
		double maxWidthPx = guiW * 0.62;

		List<String> curRows = wrap(font, curText, maxWidthPx / curScale);
		List<String> prevRows = wrap(font, prevText, maxWidthPx / prevScale);
		List<String> nextRows = wrap(font, nextText, maxWidthPx / dim);

		double curH = (curRows.isEmpty() ? ROW_H : blockUnits(curRows.size())) * curScale;
		double prevH = blockUnits(prevRows.size()) * prevScale;

		double cx = guiW * cfg.anchorX;
		double curTop = guiH * cfg.anchorY - curH / 2.0;
		// New lines glide up into place.
		double slide = (1.0 - e) * (ROW_H * base + LINE_GAP_PX);
		double prevTop = curTop - LINE_GAP_PX - prevH + slide;
		double curTopS = curTop + slide;
		double nextTop = curTop + curH + LINE_GAP_PX + slide;

		double t = now / 1_000_000_000.0;
		draw(g, font, prevRows, cx, prevTop, prevScale, cfg.dimColor, prevAlpha, t, -1, cfg.bobPixels);
		draw(g, font, curRows, cx, curTopS, curScale, cfg.currentColor, curAlpha, t, 0, cfg.bobPixels);
		draw(g, font, nextRows, cx, nextTop, dim, cfg.dimColor, nextAlpha, t, 1, cfg.bobPixels);
	}

	/** One small line explaining why there are no lyrics, so a silent overlay is never a mystery. */
	private static void renderStatus(GuiGraphicsExtractor g, LyricsConfig cfg, Snapshot song) {
		if (!cfg.showStatus) {
			return;
		}
		String msg = null;
		if (song == null) {
			// Only nag right after startup; later, silence just means nothing is playing.
			if (FloatingLyricsClient.uptimeSeconds() < 25) {
				msg = "Floating Lyrics: no player detected (is playerctl installed? Is Spotify playing?)";
			}
		} else {
			switch (FloatingLyricsClient.lyrics().status()) {
				case LOADING -> msg = "Looking up lyrics: " + song.title();
				case NOT_FOUND -> msg = "No synced lyrics found: " + song.artist() + " - " + song.title();
				case ERROR -> msg = "Lyrics lookup failed (check your connection)";
				default -> {
				}
			}
		}
		if (msg == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		int guiW = mc.getWindow().getGuiScaledWidth();
		int guiH = mc.getWindow().getGuiScaledHeight();
		int maxW = (int) (guiW * 0.9);
		while (msg.length() > 4 && font.width(msg) > maxW) {
			msg = msg.substring(0, msg.length() - 4) + "...";
		}
		int argb = (0xCC << 24) | (cfg.dimColor & 0xFFFFFF);
		int x = (int) (guiW * cfg.anchorX) - font.width(msg) / 2;
		g.text(font, msg, x, (int) (guiH * cfg.anchorY), argb, true);
	}

	private static void draw(GuiGraphicsExtractor g, Font font, List<String> rows, double cx, double top,
							 float scale, int rgb, float alpha, double t, int slot, float bob) {
		if (rows.isEmpty() || alpha <= 0.02f) {
			return;
		}
		int argb = (Math.round(clamp01(alpha) * 255f) << 24) | (rgb & 0xFFFFFF);
		double dx = Math.cos(t * 0.8 + slot * 1.3) * bob * 0.6;
		double dy = Math.sin(t * 1.4 + slot * 0.9) * bob;
		for (int i = 0; i < rows.size(); i++) {
			String row = rows.get(i);
			int w = font.width(row);
			double y = top + i * (ROW_H + 1) * scale + dy;
			g.pose().pushMatrix();
			g.pose().translate((float) (cx + dx), (float) y);
			g.pose().scale(scale, scale);
			g.text(font, row, -(w / 2), 0, argb, true);
			g.pose().popMatrix();
		}
	}

	private static double blockUnits(int rows) {
		return rows <= 0 ? 0 : rows * ROW_H + (rows - 1);
	}

	/** Word wraps to {@code maxUnits}; falls back to character splits for text without spaces. */
	static List<String> wrap(Font font, String text, double maxUnits) {
		List<String> rows = new ArrayList<>();
		if (text == null || text.isBlank()) {
			return rows;
		}
		StringBuilder row = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = row.isEmpty() ? word : row + " " + word;
			if (font.width(candidate) <= maxUnits) {
				row.setLength(0);
				row.append(candidate);
				continue;
			}
			if (!row.isEmpty()) {
				rows.add(row.toString());
				row.setLength(0);
			}
			if (font.width(word) <= maxUnits) {
				row.append(word);
			} else {
				for (int i = 0; i < word.length(); ) {
					int cp = word.codePointAt(i);
					String ch = new String(Character.toChars(cp));
					if (!row.isEmpty() && font.width(row + ch) > maxUnits) {
						rows.add(row.toString());
						row.setLength(0);
					}
					row.append(ch);
					i += Character.charCount(cp);
				}
			}
		}
		if (!row.isEmpty()) {
			rows.add(row.toString());
		}
		return rows;
	}

	private static float clamp01(double v) {
		return (float) Math.max(0.0, Math.min(1.0, v));
	}

	private static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}

	private static float easeOutCubic(float x) {
		float inv = 1f - x;
		return 1f - inv * inv * inv;
	}
}
