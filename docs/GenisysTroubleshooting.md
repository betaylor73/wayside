# GENISYS Troubleshooting Guide

This guide covers common issues and diagnostic steps for the GENISYS production runtime.

## Common Issues

### 1. Status is DISCONNECTED (Global)

*   **Cause:** Transport is down or no stations are configured.
*   **Check:** Verify `GenisysTransportEvent.TransportUp` was received. Ensure your `GenisysStationConfig` is not empty.
*   **Logs:** Look for "GENISYS Global State: TRANSPORT_DOWN -> ..."

### 2. Status is DEGRADED

*   **Cause:** One or more stations have transitioned to the `FAILED` phase.
*   **Check:** Look for "Station X: Phase POLL -> FAILED" in the logs.
*   **Possible Reasons:**
    *   Network connectivity issues to that specific station.
    *   Station is not responding within the `GenisysTimingPolicy` timeout.
    *   Incorrect station address configuration.

### 3. Station Stuck in RECALL Phase

*   **Cause:** Station is responding but not providing all required initialization data.
*   **Check:** Verify that the station is correctly implementing the RECALL response.
*   **Diagnostics:** Enable `DEBUG` logging to see raw message exchanges (if using a custom frame logger).

### 4. Spurious Timeouts

*   **Cause:** `GenisysTimingPolicy` is too aggressive for the network latency.
*   **Fix:** Increase `responseTimeout` in the timing policy.

## Diagnostic Steps

1.  **Check Logs:** Use `Slf4jGenisysObservabilitySink` to see state transitions and error events.
2.  **Verify Configuration:** Ensure all station IP/ports are reachable from the host.
3.  **Monitor Activity:** Use `GenisysMonotonicActivityTracker` (via observability hooks) to verify that responses are being received.
4.  **Isolate Stations:** If one station is causing issues, try running with only that station configured to reduce noise.
