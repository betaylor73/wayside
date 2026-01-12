package com.questrail.wayside.protocol.genisys.config;

import com.questrail.wayside.protocol.genisys.internal.exec.GenisysTimingPolicy;

import java.util.Objects;

/**
 * Aggregated configuration for the GENISYS production runtime.
 */
public record GenisysRuntimeConfig(
    GenisysStationConfig stations,
    GenisysTimingPolicy timingPolicy,
    boolean securePolls,
    boolean controlCheckbackEnabled
) {
    public GenisysRuntimeConfig {
        Objects.requireNonNull(stations, "stations");
        Objects.requireNonNull(timingPolicy, "timingPolicy");
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private GenisysStationConfig stations;
        private GenisysTimingPolicy timingPolicy = GenisysTimingPolicy.defaults();
        private boolean securePolls = true;
        private boolean controlCheckbackEnabled = true;

        public Builder withStations(GenisysStationConfig stations) {
            this.stations = stations;
            return this;
        }

        public Builder withTimingPolicy(GenisysTimingPolicy timingPolicy) {
            this.timingPolicy = timingPolicy;
            return this;
        }

        public Builder withSecurePolls(boolean securePolls) {
            this.securePolls = securePolls;
            return this;
        }

        public Builder withControlCheckbackEnabled(boolean enabled) {
            this.controlCheckbackEnabled = enabled;
            return this;
        }

        public GenisysRuntimeConfig build() {
            return new GenisysRuntimeConfig(stations, timingPolicy, securePolls, controlCheckbackEnabled);
        }
    }
}
