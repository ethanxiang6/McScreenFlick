package dev.ethanxiang6.floatinglyrics.media;

import java.util.Optional;
import java.util.function.Consumer;

/** A source of "what is playing right now" for one operating system. */
public interface MediaProvider {

	/** Starts delivering readings to {@code sink}; an empty value means nothing is playing. */
	void start(Consumer<Optional<Snapshot>> sink);

	void stop();

	String name();
}
