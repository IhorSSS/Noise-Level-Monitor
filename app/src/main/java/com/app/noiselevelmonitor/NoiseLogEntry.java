package com.app.noiselevelmonitor;

/**
 * Клас для представлення одного запису вимірювання шуму
 */
public class NoiseLogEntry {
    private final long timestamp;
    private final double noiseLevel;

    /**
     * Створює новий запис логу шуму
     * @param timestamp час запису (мілісекунди)
     * @param noiseLevel рівень шуму (дБ)
     */
    public NoiseLogEntry(long timestamp, double noiseLevel) {
        this.timestamp = timestamp;
        this.noiseLevel = noiseLevel;
    }

    /**
     * @return час запису у мілісекундах
     */
    public long getTimestamp() {
        return timestamp;
    }

    /**
     * @return рівень шуму в дБ
     */
    public double getNoiseLevel() {
        return noiseLevel;
    }
} 