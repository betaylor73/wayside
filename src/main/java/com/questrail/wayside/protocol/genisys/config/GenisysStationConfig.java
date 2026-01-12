package com.questrail.wayside.protocol.genisys.config;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Configuration for GENISYS station addresses.
 * Maps station IDs (0-255) to SocketAddresses.
 */
public final class GenisysStationConfig {
    private final Map<Integer, SocketAddress> stations;

    private GenisysStationConfig(Map<Integer, SocketAddress> stations) {
        this.stations = Collections.unmodifiableMap(new HashMap<>(stations));
    }

    /**
     * Resolves a station ID to its SocketAddress.
     * @throws IllegalArgumentException if the station is unknown
     */
    public SocketAddress resolve(int station) {
        SocketAddress addr = stations.get(station);
        if (addr == null) {
            throw new IllegalArgumentException("Unknown station: " + station);
        }
        return addr;
    }

    /**
     * Returns the set of all configured station IDs.
     */
    public Set<Integer> allStations() {
        return stations.keySet();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final Map<Integer, SocketAddress> stations = new HashMap<>();

        public Builder addStation(int station, SocketAddress address) {
            if (station < 0 || station > 255) {
                throw new IllegalArgumentException("Station must be 0-255");
            }
            stations.put(station, Objects.requireNonNull(address, "address"));
            return this;
        }

        public GenisysStationConfig build() {
            if (stations.isEmpty()) {
                throw new IllegalStateException("At least one station required");
            }
            return new GenisysStationConfig(stations);
        }
    }
}
