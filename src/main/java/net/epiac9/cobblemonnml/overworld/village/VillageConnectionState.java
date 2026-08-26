package net.epiac9.cobblemonnml.overworld.village;

public enum VillageConnectionState {
    UNSEEN,
    QUEUED,
    BUILDING,
    DEFERRED,
    COMPLETED;

    public boolean isRecoverable() {
        return this == QUEUED
                || this == BUILDING
                || this == DEFERRED;
    }
}
