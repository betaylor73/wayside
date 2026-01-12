package com.questrail.wayside.protocol.genisys.observability;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * Production implementation of GenisysObservabilitySink that emits logs via SLF4J.
 */
public final class Slf4jGenisysObservabilitySink implements GenisysObservabilitySink {
    private static final Logger log = LoggerFactory.getLogger(Slf4jGenisysObservabilitySink.class);

    @Override
    public void onStateTransition(GenisysStateTransitionEvent event) {
        if (event.isGlobalStateChange()) {
            log.info("GENISYS Global State: {} -> {}",
                event.oldState().globalState(),
                event.newState().globalState());
        }

        for (Integer station : event.affectedStations()) {
            var oldSlave = event.oldState().slaves().get(station);
            var newSlave = event.newState().slaves().get(station);

            if (oldSlave == null || oldSlave.phase() != newSlave.phase()) {
                log.info("Station {}: Phase {} -> {}",
                    station,
                    oldSlave != null ? oldSlave.phase() : "UNKNOWN",
                    newSlave.phase());
            }
        }
    }

    @Override
    public void onProtocolEvent(GenisysProtocolObservabilityEvent event) {
        log.debug("GENISYS Protocol Event: {}", event);
    }

    @Override
    public void onTransportEvent(GenisysTransportObservabilityEvent event) {
        log.info("GENISYS Transport Event: {}", event);
    }

    @Override
    public void onError(GenisysErrorEvent event) {
        log.error("GENISYS Error: {}", event.message(), event.cause());
    }
}
