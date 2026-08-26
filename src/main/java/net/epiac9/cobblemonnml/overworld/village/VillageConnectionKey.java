package net.epiac9.cobblemonnml.overworld.village;

import java.util.Objects;
import java.util.UUID;

public record VillageConnectionKey(
        UUID first,
        UUID second
) {
    public VillageConnectionKey {
        Objects.requireNonNull( first, "first" );
        Objects.requireNonNull( second, "second" );

        if (first.equals(second)) {
            throw new IllegalArgumentException(
                    "A village structure cannot connect to itself."
            );
        }

        if (first.compareTo(second) > 0) {
            UUID swap = first;
            first = second;
            second = swap;
        }
    }

    public static VillageConnectionKey of(UUID a, UUID b) {
        return new VillageConnectionKey( a, b );
    }
}
