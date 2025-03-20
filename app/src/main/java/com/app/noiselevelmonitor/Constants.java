package com.app.noiselevelmonitor;

/**
 * Клас з константами для всього додатку
 */
public class Constants {
    // Налаштування додатку
    public static final String PREFS_NAME = "NoiseMonitorPrefs";
    
    // Ключі для SharedPreferences
    public static final String PREF_LANGUAGE = "language";
    public static final String PREF_LANGUAGE_SELECTED = "language_selected";
    public static final String PREF_THEME_MODE = "theme_mode";
    public static final String PREF_BACKGROUND_SERVICE = "background_service";
    public static final String PREF_IS_PAUSED = "is_paused";
    public static final String PREF_IS_RECORDING = "is_recording";
    public static final String PREF_FIRST_LAUNCH = "first_launch";
    public static final String PREF_MIN_NOISE = "min_noise";
    public static final String PREF_MAX_NOISE = "max_noise";
    public static final String PREF_TOTAL_NOISE = "total_noise";
    public static final String PREF_MEASUREMENTS = "measurements";
    public static final String PREF_MIN_NOISE_TIME = "min_noise_time";
    public static final String PREF_MAX_NOISE_TIME = "max_noise_time";
    public static final String PREF_PERMISSIONS_DENIED = "permissions_denied";
    public static final String PREF_BATTERY_OPTIMIZATION_DENIED = "battery_optimization_denied";
    public static final String PREF_SENSITIVITY_OFFSET = "sensitivity_offset";
    public static final String PREF_SMOOTHING_FACTOR = "smoothing_factor";
    public static final String PREF_MIN_NOISE_DETECTION_LEVEL = "min_noise_detection_level";
    public static final String PREF_LAST_NOISE_LEVEL = "last_noise_level";
    
    // Дії для Intent
    public static final String ACTION_NOISE_UPDATE = "com.app.noiselevelmonitor.NOISE_UPDATE";
    public static final String ACTION_MEASURE_ONCE = "com.app.noiselevelmonitor.MEASURE_ONCE";
    public static final String ACTION_TOGGLE_RECORDING = "com.app.noiselevelmonitor.TOGGLE_RECORDING";
    public static final String ACTION_RESET_STATS = "com.app.noiselevelmonitor.RESET_STATS";
    public static final String ACTION_UPDATE_BACKGROUND_STATE = "com.app.noiselevelmonitor.UPDATE_BACKGROUND_STATE";
    public static final String ACTION_UPDATE_RECORDING = "com.app.noiselevelmonitor.UPDATE_RECORDING";
    public static final String ACTION_APP_TO_BACKGROUND = "com.app.noiselevelmonitor.APP_TO_BACKGROUND";
    public static final String ACTION_APP_TO_FOREGROUND = "com.app.noiselevelmonitor.APP_TO_FOREGROUND";
    public static final String ACTION_UPDATE_CALIBRATION = "com.app.noiselevelmonitor.UPDATE_CALIBRATION";
    public static final String ACTION_STOP_SERVICE = "com.app.noiselevelmonitor.STOP_SERVICE";
    
    // Додаткові параметри для Intent
    public static final String EXTRA_RECORDING_STATE = "recording_state";
    public static final String EXTRA_IS_RECORDING = "isRecording";
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_NOISE_LEVEL = "noise_level";
    public static final String EXTRA_BACKGROUND_SERVICE_STATE = "background_service_state";
    public static final String EXTRA_UPDATE_FREQUENCY = "update_frequency";
    public static final String EXTRA_SENSITIVITY_OFFSET = "sensitivity_offset";
    public static final String EXTRA_SMOOTHING_FACTOR = "smoothing_factor";
    public static final String EXTRA_MIN_NOISE_LEVEL = "min_noise_level";
    public static final String EXTRA_PRESERVE_STATE = "preserve_state";
    public static final String EXTRA_WAS_FULLY_SHUTDOWN = "wasFullyShutdown";
    
    // Канал нотифікацій
    public static final String CHANNEL_ID = "noise_monitor_channel";
    public static final int NOTIFICATION_ID = 1;
    
    // Технічні константи
    public static final double REFERENCE_AMPLITUDE = 32767.0;
    public static final double CALIBRATION_OFFSET = 93.0;
    public static final double MIN_DB = 30.0;
    public static final double MAX_DB = 120.0;
    public static final double MIN_VALID_DB = 30.0;
    public static final double MIN_VALID_STAT_DB = 20.0;
    
    // Константи для вимірювань
    public static final int SAMPLE_WINDOW = 5;
    public static final int MIN_STABLE_READINGS = 3;
    public static final int MAX_ERRORS = 3;
    public static final long NOTIFICATION_UPDATE_INTERVAL = 1000; // 1 секунда
    public static final long NOISE_UPDATE_INTERVAL = 100; // 100 мс
    
    // Приватний конструктор, щоб запобігти створенню екземплярів класу
    private Constants() {
        // Порожній конструктор
    }
} 