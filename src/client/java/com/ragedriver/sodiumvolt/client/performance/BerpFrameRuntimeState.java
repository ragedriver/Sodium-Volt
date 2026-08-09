package com.ragedriver.sodiumvolt.client.performance;

final class BerpFrameRuntimeState {
    private final BerpRuntimeSettings reusableSettings = new BerpRuntimeSettings();
    private volatile BerpRuntimeSettings settings = BerpRuntimeSettings.DISABLED;

    BerpRuntimeSettings capture(
            com.ragedriver.sodiumvolt.client.config.VoltPerformanceConfig config,
            boolean collectStatistics
    ) {
        this.requireClosedForCapture();
        return this.reusableSettings.capture(config, collectStatistics);
    }

    BerpRuntimeSettings capture(BerpRuntimeSettings source) {
        this.requireClosedForCapture();
        return this.reusableSettings.capture(source);
    }

    private void requireClosedForCapture() {
        if (BerpHotPathGate.extractionActive()) {
            throw new IllegalStateException("BERP settings cannot change during an extraction frame");
        }
    }

    void activate(BerpRuntimeSettings settings) {
        if (settings == null || !settings.active()) {
            throw new IllegalArgumentException("An active BERP frame requires active settings");
        }
        this.settings = settings;
        BerpHotPathGate.publish(true);
    }

    BerpRuntimeSettings settings() {
        return this.settings;
    }

    void close() {
        BerpHotPathGate.publish(false);
        this.settings = BerpRuntimeSettings.DISABLED;
    }
}
