package com.app.noiselevelmonitor;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Клас для управління логуванням шуму
 */
public class NoiseLogManager {
    private static final String TAG = "NoiseLogManager";
    
    // Ключі для SharedPreferences
    public static final String PREF_LOGGING_ENABLED = "logging_enabled";
    public static final String PREF_SAMPLE_FREQUENCY = "logging_sample_frequency";
    public static final String PREF_RETENTION_PERIOD = "logging_retention_period";
    public static final String PREF_LOGGING_PAUSED = "logging_paused";
    
    // Варіанти частоти вимірювань у мілісекундах
    public static final long[] SAMPLE_FREQUENCIES = {500, 1000, 5000, 10000};
    public static final int DEFAULT_SAMPLE_FREQUENCY_INDEX = 1; // 1 секунда
    
    // Варіанти періоду збереження у мілісекундах
    public static final long[] RETENTION_PERIODS = {
            60000,      // 1 хвилина
            300000,     // 5 хвилин
            600000,     // 10 хвилин
            3600000,    // 1 година
            7200000,    // 2 години
            10800000,   // 3 години
            18000000,   // 5 годин
            36000000,   // 10 годин
            86400000    // 24 години
    };
    public static final int DEFAULT_RETENTION_PERIOD_INDEX = 5; // 3 години
    
    // Одиничний екземпляр класу
    private static NoiseLogManager instance;
    
    // Черга для зберігання логів (потокобезпечна)
    private final ConcurrentLinkedQueue<NoiseLogEntry> logEntries = new ConcurrentLinkedQueue<>();
    
    // Налаштування
    private boolean loggingEnabled = false;
    private boolean loggingPaused = false;
    private long sampleFrequencyMs = SAMPLE_FREQUENCIES[DEFAULT_SAMPLE_FREQUENCY_INDEX];
    private long retentionPeriodMs = RETENTION_PERIODS[DEFAULT_RETENTION_PERIOD_INDEX];
    private long lastLogTimestamp = 0;
    
    // Контекст додатку
    private final Context context;
    
    /**
     * Приватний конструктор для Singleton
     */
    private NoiseLogManager(Context context) {
        this.context = context.getApplicationContext();
        loadSettings();
    }
    
    /**
     * Отримати екземпляр менеджера логів
     */
    public static synchronized NoiseLogManager getInstance(Context context) {
        if (instance == null) {
            instance = new NoiseLogManager(context);
        }
        return instance;
    }
    
    /**
     * Завантажити налаштування з SharedPreferences
     */
    private void loadSettings() {
        try {
            SharedPreferences prefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
            loggingEnabled = prefs.getBoolean(PREF_LOGGING_ENABLED, false);
            loggingPaused = prefs.getBoolean(PREF_LOGGING_PAUSED, false);
            
            int sampleFrequencyIndex = prefs.getInt(PREF_SAMPLE_FREQUENCY, DEFAULT_SAMPLE_FREQUENCY_INDEX);
            if (sampleFrequencyIndex >= 0 && sampleFrequencyIndex < SAMPLE_FREQUENCIES.length) {
                sampleFrequencyMs = SAMPLE_FREQUENCIES[sampleFrequencyIndex];
            }
            
            int retentionPeriodIndex = prefs.getInt(PREF_RETENTION_PERIOD, DEFAULT_RETENTION_PERIOD_INDEX);
            if (retentionPeriodIndex >= 0 && retentionPeriodIndex < RETENTION_PERIODS.length) {
                retentionPeriodMs = RETENTION_PERIODS[retentionPeriodIndex];
            }
            
            Log.d(TAG, "Налаштування логування завантажено: enabled=" + loggingEnabled + 
                    ", paused=" + loggingPaused +
                    ", sampleFrequency=" + sampleFrequencyMs + "ms" + 
                    ", retentionPeriod=" + retentionPeriodMs + "ms");
        } catch (Exception e) {
            Log.e(TAG, "Помилка при завантаженні налаштувань логування", e);
            resetToDefaultSettings();
        }
    }
    
    /**
     * Скинути налаштування до значень за замовчуванням
     */
    private void resetToDefaultSettings() {
        loggingEnabled = false;
        loggingPaused = false;
        sampleFrequencyMs = SAMPLE_FREQUENCIES[DEFAULT_SAMPLE_FREQUENCY_INDEX];
        retentionPeriodMs = RETENTION_PERIODS[DEFAULT_RETENTION_PERIOD_INDEX];
        saveSettings();
    }
    
    /**
     * Зберегти налаштування у SharedPreferences
     */
    private void saveSettings() {
        try {
            SharedPreferences.Editor editor = context.getSharedPreferences(
                    Constants.PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putBoolean(PREF_LOGGING_ENABLED, loggingEnabled);
            editor.putBoolean(PREF_LOGGING_PAUSED, loggingPaused);
            
            // Знаходимо індекси для частоти та періоду
            int sampleFrequencyIndex = DEFAULT_SAMPLE_FREQUENCY_INDEX;
            for (int i = 0; i < SAMPLE_FREQUENCIES.length; i++) {
                if (SAMPLE_FREQUENCIES[i] == sampleFrequencyMs) {
                    sampleFrequencyIndex = i;
                    break;
                }
            }
            
            int retentionPeriodIndex = DEFAULT_RETENTION_PERIOD_INDEX;
            for (int i = 0; i < RETENTION_PERIODS.length; i++) {
                if (RETENTION_PERIODS[i] == retentionPeriodMs) {
                    retentionPeriodIndex = i;
                    break;
                }
            }
            
            editor.putInt(PREF_SAMPLE_FREQUENCY, sampleFrequencyIndex);
            editor.putInt(PREF_RETENTION_PERIOD, retentionPeriodIndex);
            editor.apply();
            
            // Якщо логування вимкнено, очищаємо всі записи
            if (!loggingEnabled) {
                clearAllLogs();
            }
            
            Log.d(TAG, "Налаштування логування збережено: enabled=" + loggingEnabled + 
                    ", paused=" + loggingPaused +
                    ", sampleFrequency=" + sampleFrequencyMs + "ms" + 
                    ", retentionPeriod=" + retentionPeriodMs + "ms");
        } catch (Exception e) {
            Log.e(TAG, "Помилка при збереженні налаштувань логування", e);
        }
    }
    
    /**
     * Увімкнути або вимкнути логування
     */
    public void setLoggingEnabled(boolean enabled) {
        if (this.loggingEnabled != enabled) {
            this.loggingEnabled = enabled;
            saveSettings();
        }
    }
    
    /**
     * Встановити частоту вимірювань за індексом
     */
    public void setSampleFrequencyIndex(int index) {
        if (index >= 0 && index < SAMPLE_FREQUENCIES.length) {
            this.sampleFrequencyMs = SAMPLE_FREQUENCIES[index];
            saveSettings();
        }
    }
    
    /**
     * Встановити період збереження за індексом
     */
    public void setRetentionPeriodIndex(int index) {
        if (index >= 0 && index < RETENTION_PERIODS.length) {
            this.retentionPeriodMs = RETENTION_PERIODS[index];
            saveSettings();
            cleanupOldEntries();
        }
    }
    
    /**
     * Перевірити, чи увімкнено логування
     */
    public boolean isLoggingEnabled() {
        return loggingEnabled;
    }
    
    /**
     * Отримати індекс поточної частоти вимірювань
     */
    public int getSampleFrequencyIndex() {
        for (int i = 0; i < SAMPLE_FREQUENCIES.length; i++) {
            if (SAMPLE_FREQUENCIES[i] == sampleFrequencyMs) {
                return i;
            }
        }
        return DEFAULT_SAMPLE_FREQUENCY_INDEX;
    }
    
    /**
     * Отримати індекс поточного періоду збереження
     */
    public int getRetentionPeriodIndex() {
        for (int i = 0; i < RETENTION_PERIODS.length; i++) {
            if (RETENTION_PERIODS[i] == retentionPeriodMs) {
                return i;
            }
        }
        return DEFAULT_RETENTION_PERIOD_INDEX;
    }
    
    /**
     * Встановити стан паузи логування
     * @param paused true для паузи, false для продовження
     */
    public void setLoggingPaused(boolean paused) {
        if (loggingPaused != paused) {
            loggingPaused = paused;
            saveSettings();
            Log.d(TAG, "Стан паузи логування змінено: " + paused);
        }
    }
    
    /**
     * Перевірити, чи зараз на паузі логування
     * @return true якщо логування на паузі
     */
    public boolean isLoggingPaused() {
        return loggingPaused;
    }
    
    /**
     * Додати запис у логи
     */
    public void logNoiseLevel(double noiseLevel) {
        // Перевіряємо, чи ввімкнено логування і чи не на паузі
        if (!loggingEnabled || loggingPaused) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        // Перевіряємо, чи минув інтервал між вимірюваннями
        if (currentTime - lastLogTimestamp < sampleFrequencyMs) {
            return;
        }
        
        // Додаємо новий запис
        logEntries.add(new NoiseLogEntry(currentTime, noiseLevel));
        lastLogTimestamp = currentTime;
        
        // Очищуємо старі записи
        cleanupOldEntries();
    }
    
    /**
     * Видалити записи, старіші за період збереження
     */
    private void cleanupOldEntries() {
        long cutoffTime = System.currentTimeMillis() - retentionPeriodMs;
        
        // Видаляємо старі записи (сумісний метод для API 21+)
        Iterator<NoiseLogEntry> iterator = logEntries.iterator();
        while (iterator.hasNext()) {
            NoiseLogEntry entry = iterator.next();
            if (entry.getTimestamp() < cutoffTime) {
                iterator.remove();
            }
        }
    }
    
    /**
     * Отримати всі записи логу, відсортовані за часом (від найновіших до найстаріших)
     */
    public List<NoiseLogEntry> getAllLogs() {
        cleanupOldEntries();
        
        List<NoiseLogEntry> entryList = new ArrayList<>(logEntries);
        Collections.sort(entryList, (e1, e2) -> Long.compare(e2.getTimestamp(), e1.getTimestamp()));
        
        return entryList;
    }
    
    /**
     * Очистити всі записи логу
     */
    public void clearAllLogs() {
        logEntries.clear();
        Log.d(TAG, "Всі логи очищено");
    }
    
    /**
     * Примусово зберегти логи без їх очищення (для використання при закритті додатку)
     */
    public void forcePreserveLogs() {
        try {
            // Зберігаємо всі налаштування без виклику saveSettings(), 
            // щоб уникнути очищення логів при loggingEnabled=false
            SharedPreferences.Editor editor = context.getSharedPreferences(
                    Constants.PREFS_NAME, Context.MODE_PRIVATE).edit();
            editor.putBoolean(PREF_LOGGING_ENABLED, loggingEnabled);
            editor.putBoolean(PREF_LOGGING_PAUSED, loggingPaused);
            
            // Знаходимо індекси для частоти та періоду
            int sampleFrequencyIndex = getSampleFrequencyIndex();
            int retentionPeriodIndex = getRetentionPeriodIndex();
            
            editor.putInt(PREF_SAMPLE_FREQUENCY, sampleFrequencyIndex);
            editor.putInt(PREF_RETENTION_PERIOD, retentionPeriodIndex);
            editor.apply();
            
            Log.d(TAG, "Примусове збереження налаштувань логування без очищення логів. Стан логування: " +
                    "enabled=" + loggingEnabled + ", paused=" + loggingPaused +
                    ", збережено " + logEntries.size() + " записів");
        } catch (Exception e) {
            Log.e(TAG, "Помилка при примусовому збереженні налаштувань логування", e);
        }
    }
    
    /**
     * Отримати поточну частоту вимірювань у мілісекундах
     */
    public long getSampleFrequencyMs() {
        return sampleFrequencyMs;
    }
    
    /**
     * Отримати поточний період збереження у мілісекундах
     */
    public long getRetentionPeriodMs() {
        return retentionPeriodMs;
    }
} 