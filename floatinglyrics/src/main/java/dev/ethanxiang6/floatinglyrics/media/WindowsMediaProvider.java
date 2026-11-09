package dev.ethanxiang6.floatinglyrics.media;

import dev.ethanxiang6.floatinglyrics.FloatingLyricsClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Windows: asks the system media transport controls (the same source as the volume flyout) through a
 * long running PowerShell loop that prints one line per second. No setup needed.
 */
public final class WindowsMediaProvider implements MediaProvider {

	private static final String SCRIPT = """
			$ErrorActionPreference = 'SilentlyContinue'
			[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
			Add-Type -AssemblyName System.Runtime.WindowsRuntime
			$null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType=WindowsRuntime]
			$asTask = ([System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' })[0]
			function Await($op, $type) {
			  $t = $asTask.MakeGenericMethod($type).Invoke($null, @($op))
			  $null = $t.Wait(5000)
			  $t.Result
			}
			$ignore = @(__IGNORE__)
			$mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
			while ($true) {
			  $line = 'NONE'
			  $s = $null
			  foreach ($c in $mgr.GetSessions()) {
			    $id = "$($c.SourceAppUserModelId)".ToLower()
			    $skip = $false
			    foreach ($i in $ignore) { if ($id.Contains($i)) { $skip = $true } }
			    if ($skip) { continue }
			    $cs = $c.GetPlaybackInfo().PlaybackStatus.ToString()
			    if ($cs -eq 'Playing') { $s = $c; break }
			    if (-not $s -and $cs -eq 'Paused') { $s = $c }
			  }
			  if ($s) {
			    $props = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
			    $tl = $s.GetTimelineProperties()
			    $st = $s.GetPlaybackInfo().PlaybackStatus.ToString()
			    $pos = $tl.Position.TotalMilliseconds
			    if ($st -eq 'Playing') { $pos += ([DateTimeOffset]::UtcNow - $tl.LastUpdatedTime).TotalMilliseconds }
			    $dur = $tl.EndTime.TotalMilliseconds - $tl.StartTime.TotalMilliseconds
			    if ($dur -gt 0 -and $pos -gt $dur) { $pos = $dur }
			    $line = "$st`t$([int64]$pos)`t$([int64]$dur)`t$($props.Artist)`t$($props.Title)`t$($props.AlbumTitle)"
			  }
			  [Console]::Out.WriteLine($line)
			  [Console]::Out.Flush()
			  Start-Sleep -Milliseconds 1000
			}
			""";

	private final Supplier<? extends Collection<String>> ignoredPlayers;
	private volatile boolean running;
	private volatile Process process;
	private Thread thread;

	public WindowsMediaProvider(Supplier<? extends Collection<String>> ignoredPlayers) {
		this.ignoredPlayers = ignoredPlayers;
	}

	/** Builds the PowerShell array body, for example {@code 'chrome','firefox'}. Only safe characters survive. */
	static String ignoreList(Collection<String> ignored) {
		StringBuilder sb = new StringBuilder();
		if (ignored != null) {
			for (String item : ignored) {
				if (item == null) {
					continue;
				}
				String clean = item.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "");
				if (clean.isEmpty()) {
					continue;
				}
				if (!sb.isEmpty()) {
					sb.append(',');
				}
				sb.append('\'').append(clean).append('\'');
			}
		}
		return sb.toString();
	}

	@Override
	public void start(Consumer<Optional<Snapshot>> sink) {
		running = true;
		thread = new Thread(() -> loop(sink), "FloatingLyrics-windows-media");
		thread.setDaemon(true);
		thread.start();
	}

	@Override
	public void stop() {
		running = false;
		Process p = process;
		if (p != null) {
			p.destroyForcibly();
		}
		if (thread != null) {
			thread.interrupt();
		}
	}

	@Override
	public String name() {
		return "Windows media session";
	}

	private void loop(Consumer<Optional<Snapshot>> sink) {
		String script = SCRIPT.replace("__IGNORE__", ignoreList(ignoredPlayers.get()));
		String encoded = Base64.getEncoder().encodeToString(script.getBytes(StandardCharsets.UTF_16LE));
		while (running) {
			try {
				process = new ProcessBuilder(
						"powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
						"-EncodedCommand", encoded)
						.redirectErrorStream(false)
						.start();
				process.getErrorStream().close();
				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while (running && (line = reader.readLine()) != null) {
						Snapshot s = Snapshot.parseTabLine(line, 1.0, System.nanoTime());
						sink.accept(Optional.ofNullable(s));
					}
				}
			} catch (IOException e) {
				FloatingLyricsClient.LOGGER.warn("PowerShell media reader failed: {}", e.getMessage());
			} finally {
				Process p = process;
				if (p != null) {
					p.destroyForcibly();
				}
			}
			if (!running) {
				return;
			}
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				return;
			}
		}
	}
}
