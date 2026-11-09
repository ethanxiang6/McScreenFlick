# Floating Lyrics

A client-side Fabric mod for Minecraft 26.2 that shows synced lyrics for whatever song is playing on your computer, floating on screen. The previous line fades out, the current line grows in, and the next line waits underneath. Everything drifts a little so it feels like it is hovering.

This is an independent mod. It does not use any code from River Client or any other client.

## How it works

The mod reads the now playing track from your operating system, looks up time synced lyrics on [LRCLIB](https://lrclib.net) (free, no account or key), and draws the line that matches the current playback position. Lyrics are fetched when a song starts and kept in memory only. Nothing is bundled with the mod.

On Linux it reads any MPRIS player (Spotify, Firefox, mpv, VLC) through `playerctl`, so install that first:

    sudo dnf install playerctl      # Fedora
    sudo apt install playerctl      # Debian, Ubuntu

On Windows it uses the system media session through PowerShell, so there is nothing to install. macOS is not supported yet.

## Install

Build it (see below) or grab the jar from the GitHub Actions artifact, then drop it in your `mods` folder next to Fabric API. It needs Java 25, Fabric Loader 0.19.5 or newer, and Fabric API for 26.2.

## Commands

`/floatinglyrics` also works as `/lyrics`.

    /lyrics toggle              turn the overlay on or off
    /lyrics status              show the current song and lyric lookup state
    /lyrics refresh             look the lyrics up again
    /lyrics offset <ms>         shift timing, positive shows lines earlier (-10000 to 10000)
    /lyrics scale <0.5-4>       size of the current line
    /lyrics pos <x> <y>         position as fractions of the screen, 0 to 1
    /lyrics bob <pixels>        strength of the floating motion, 0 turns it off
    /lyrics player <name>       Linux only, pick one MPRIS player such as spotify, or auto to go back to automatic

Browsers (Firefox, Chrome, Chromium, Brave, Edge and similar) are skipped by default, so a YouTube or Twitch tab will not take over from Spotify. The list is `ignoredPlayers` in the config file. Naming a player with `/lyrics player` overrides it.

When no lyrics can be shown, a small status line says why (no player found, looking up, not found, lookup failed). Set `showStatus` to false in the config to hide it.

Settings are saved to `config/floatinglyrics.json`, which you can also edit by hand.

## Build

    ./gradlew build

The jar ends up in `build/libs`. The included GitHub Actions workflow builds on every push and uploads the jar as an artifact.

## Layout

    media/     now playing readers (playerctl on Linux, PowerShell on Windows)
    lyrics/    LRCLIB client, LRC parser, song change handling
    render/    LyricsHud, the only file that draws
    config/    JSON settings

If a Minecraft update changes the drawing API, `render/LyricsHud.java` is the one file to fix.

## License

MIT
