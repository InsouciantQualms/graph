/*
 * Insouciant Qualms © 2025 by Sascha Goldsmith is licensed under CC BY 4.0.
 * To view a copy of this license, visit https://creativecommons.org/licenses/by/4.0.
 * To reach the creator, visit https://www.linkedin.com/in/saschagoldsmith.
 */

package dev.iq.graph.persistence.mongodb;

import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.mongo.transitions.Mongod;
import de.flapdoodle.embed.mongo.transitions.RunningMongodProcess;
import de.flapdoodle.reverse.TransitionWalker;
import java.io.File;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import org.junit.jupiter.api.Assumptions;

/**
 * Shared MongoDB test configuration.
 *
 * Note: de.flapdoodle.embed.mongo requires network connectivity to download MongoDB binaries
 * on first use. Once downloaded, the binaries are cached in ~/.embedmongo/
 *
 * To run tests offline, you must first run tests with network connectivity to download
 * the binaries, or manually download them to the cache directory.
 */
public final class MongoTestConfig {

    private MongoTestConfig() {}

    /**
     * Starts MongoDB for testing. Skips tests if MongoDB cannot be started.
     *
     * This method will attempt to start an embedded MongoDB instance. If network
     * connectivity is not available and binaries have not been previously cached,
     * the tests will be skipped.
     */
    public static TransitionWalker.ReachedState<RunningMongodProcess> startMongoDbOrSkip() {
        try {
            // Check if binaries are already cached
            final var userHome = System.getProperty("user.home");
            final var embedMongoDir = new File(userHome, ".embedmongo");
            final var hasCachedBinaries = embedMongoDir.exists() && embedMongoDir.isDirectory();

            // Binaries are downloaded on first use if not cached

            return Mongod.instance().start(Version.Main.V7_0);
        } catch (final Exception e) {
            // Check if the exception is due to network connectivity issues
            var cause = e.getCause();
            while (cause != null) {
                if ((cause instanceof ConnectException) || (cause instanceof SocketTimeoutException)) {
                    final var msg = "Skipping MongoDB tests - network connectivity required to download "
                            + "MongoDB binaries. To run tests offline, first run with network connectivity "
                            + "to cache binaries in ~/.embedmongo/";
                    Assumptions.assumeFalse(true, msg);
                    return null; // Never reached
                }
                cause = cause.getCause();
            }

            // If it's not a network issue, throw the exception
            throw new RuntimeException("Failed to start embedded MongoDB", e);
        }
    }
}
