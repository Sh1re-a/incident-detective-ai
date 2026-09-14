package dev.shirwac.incidentdetective.generated;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.util.Objects;
import java.util.function.LongSupplier;

/** Resolves an optional caller seed without hiding the chosen replay value. */
@Component
public final class GeneratedCaseSeedResolver {

    private final LongSupplier serverSeeds;

    public GeneratedCaseSeedResolver() {
        this(secureSeedSupplier());
    }

    GeneratedCaseSeedResolver(LongSupplier serverSeeds) {
        this.serverSeeds = Objects.requireNonNull(
                serverSeeds,
                "serverSeeds must not be null"
        );
    }

    public GeneratedCaseSeed resolve(Long requestedSeed) {
        if (requestedSeed != null) {
            return GeneratedCaseSeed.explicit(requestedSeed.longValue());
        }
        return GeneratedCaseSeed.serverGenerated(serverSeeds.getAsLong());
    }

    private static LongSupplier secureSeedSupplier() {
        SecureRandom random = new SecureRandom();
        return random::nextLong;
    }
}
