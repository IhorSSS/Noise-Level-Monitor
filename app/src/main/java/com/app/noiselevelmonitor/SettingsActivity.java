package com.app.noiselevelmonitor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.appcompat.widget.Toolbar;
import java.util.Locale;
import android.content.res.Configuration;
import android.os.Build;
import android.widget.Toast;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.google.android.material.switchmaterial.SwitchMaterial;
import androidx.core.app.TaskStackBuilder;
import com.google.android.material.button.MaterialButton;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.AdapterView;
import android.view.View;
import android.view.LayoutInflater;
import android.app.AlertDialog;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.app.ActionBar;
import android.view.Menu;

public class SettingsActivity extends BaseActivity {
    private static final String TAG = "SettingsActivity";
    
    // Використовуємо константи з класу Constants
    private RadioGroup languageGroup;
    private RadioGroup themeGroup;
    private SwitchMaterial backgroundServiceSwitch;
    private String currentLanguage;
    private int currentThemeMode;
    private boolean isBackgroundServiceEnabled = true;
    private Toolbar toolbar;
    
    // Константи для налаштувань калібрування
    private static final String PREF_UPDATE_FREQUENCY = "calibration_update_frequency";
    private static final String PREF_SENSITIVITY = "calibration_sensitivity";
    private static final String PREF_SMOOTHING = "calibration_smoothing";
    private static final String PREF_MIN_NOISE_LEVEL = "calibration_min_noise_level";
    
    // Інтервали оновлення в мілісекундах
    private static final long[] UPDATE_FREQUENCIES = {50, 100, 200, 500, 1000};
    // Значення за замовчуванням - 100мс
    private static final int DEFAULT_UPDATE_FREQUENCY_INDEX = 1;
    
    // Рівні чутливості відносно базового значення калібрування
    private static final double[] SENSITIVITY_OFFSETS = {-10.0, -7.5, -5.0, -2.5, 0.0, 2.5, 5.0, 7.5, 10.0};
    // Значення за замовчуванням - 0.0 (без зміщення)
    private static final int DEFAULT_SENSITIVITY_INDEX = 4;
    
    // Коефіцієнти згладжування (більше значення = більше згладжування)
    private static final double[] SMOOTHING_FACTORS = {0.0, 0.3, 0.5, 0.7, 0.9};
    // Значення за замовчуванням - 0.3
    private static final int DEFAULT_SMOOTHING_INDEX = 1;
    
    // Мінімальні значення шуму, які враховуються (dB)
    private static final double[] MIN_NOISE_LEVELS = {20.0, 25.0, 30.0, 35.0, 40.0};
    // Значення за замовчуванням - 30.0
    private static final int DEFAULT_MIN_NOISE_LEVEL_INDEX = 2;
    
    // UI елементи для калібрування
    private Spinner updateFrequencySpinner;
    private Spinner sensitivitySpinner;
    private Spinner smoothingSpinner;
    private Spinner minNoiseSpinner;
    
    private MaterialButton resetUpdateFrequencyButton;
    private MaterialButton resetSensitivityButton;
    private MaterialButton resetSmoothingButton;
    private MaterialButton resetMinNoiseButton;
    private MaterialButton resetCalibrationButton;
    
    // Поточні значення налаштувань
    private int currentUpdateFrequencyIndex = DEFAULT_UPDATE_FREQUENCY_INDEX;
    private int currentSensitivityIndex = DEFAULT_SENSITIVITY_INDEX;
    private int currentSmoothingIndex = DEFAULT_SMOOTHING_INDEX;
    private int currentMinNoiseIndex = DEFAULT_MIN_NOISE_LEVEL_INDEX;

    private SwitchMaterial loggingEnabledSwitch;
    private Spinner sampleFrequencySpinner, retentionPeriodSpinner;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        // Set up the toolbar
        toolbar = findViewById(R.id.settingsToolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setTitle(R.string.settings_title);
        }

        // Логуємо поточну мову
        Log.d(TAG, "onCreate: Поточна мова: " + Locale.getDefault().getLanguage());

        // Завантаження поточних налаштувань
        loadSettings();
        
        // Налаштовуємо групу радіокнопок для вибору мови
        setupLanguageRadioGroup();

        // Налаштовуємо групу радіокнопок для вибору теми
        setupThemeSettings();

        // Налаштовуємо перемикач фонового сервісу
        setupBackgroundServiceSwitch();
        
        // Налаштовуємо елементи калібрування
        setupCalibrationSettings();
        
        // Setup logging settings
        setupLoggingSettings();
    }

    // Завантаження поточних налаштувань
    private void loadSettings() {
        // Завантажуємо збережені налаштування
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        currentLanguage = prefs.getString(Constants.PREF_LANGUAGE, Locale.getDefault().getLanguage());
        currentThemeMode = prefs.getInt(Constants.PREF_THEME_MODE, AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        isBackgroundServiceEnabled = prefs.getBoolean(Constants.PREF_BACKGROUND_SERVICE, true);
        
        // Завантажуємо налаштування калібрування
        currentUpdateFrequencyIndex = prefs.getInt(PREF_UPDATE_FREQUENCY, DEFAULT_UPDATE_FREQUENCY_INDEX);
        currentSensitivityIndex = prefs.getInt(PREF_SENSITIVITY, DEFAULT_SENSITIVITY_INDEX);
        currentSmoothingIndex = prefs.getInt(PREF_SMOOTHING, DEFAULT_SMOOTHING_INDEX);
        currentMinNoiseIndex = prefs.getInt(PREF_MIN_NOISE_LEVEL, DEFAULT_MIN_NOISE_LEVEL_INDEX);
        
        Log.d(TAG, "loadSettings: Встановлена мова додатку: " + currentLanguage);
        Log.d(TAG, "loadSettings: Встановлена тема додатку: " + getThemeDescription(currentThemeMode));
        Log.d(TAG, "loadSettings: Завантажено налаштування калібрування");
    }

    // Повертає опис теми за кодом
    private String getThemeDescription(int themeMode) {
        switch (themeMode) {
            case AppCompatDelegate.MODE_NIGHT_YES:
                return "Темна";
            case AppCompatDelegate.MODE_NIGHT_NO:
                return "Світла";
            case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:
                int uiMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
                boolean isSystemDark = uiMode == Configuration.UI_MODE_NIGHT_YES;
                return "Системна (" + (isSystemDark ? "зараз темна" : "зараз світла") + ")";
            default:
                return "Невідома";
        }
    }

    // Налаштування перемикачів мови
    private void setupLanguageRadioGroup() {
        languageGroup = findViewById(R.id.languageGroup);
        
        // Встановлюємо обрану мову
        switch (currentLanguage) {
            case "uk":
                languageGroup.check(R.id.radioUkrainian);
                break;
            case "en":
                languageGroup.check(R.id.radioEnglish);
                break;
            default:
                // У разі якщо мова не українська і не англійська, використовуємо англійську як запасний варіант
                languageGroup.check(R.id.radioEnglish);
                break;
        }
        
        // Обробник зміни мови
        languageGroup.setOnCheckedChangeListener((group, checkedId) -> {
            String languageCode;
            
            if (checkedId == R.id.radioUkrainian) {
                languageCode = "uk";
            } else if (checkedId == R.id.radioEnglish) {
                languageCode = "en";
            } else {
                languageCode = Locale.getDefault().getLanguage();
            }
            
            // Зберігаємо вибрану мову
            updateLanguage(languageCode);
        });
    }

    /**
     * Оновлює мову додатку
     */
    private void updateLanguage(String languageCode) {
        try {
            Log.d(TAG, "updateLanguage: Зміна мови на " + languageCode);
            
            // Зберігаємо налаштування
            currentLanguage = languageCode;
            SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit();
            editor.putString(Constants.PREF_LANGUAGE, languageCode);
            editor.putBoolean("language_selected", true);
            editor.apply();
            
            // Застосовуємо налаштування в додаток
            NoiseMonitorApplication app = (NoiseMonitorApplication) getApplication();
            app.setLocale(languageCode);

            // ПЕРЕД зміною мови запускаємо сервіс з wasFullyShutdown=false,
            // щоб він не відновлював запис при перезапуску
            startNoiseService();
            
            // Перезапускаємо активність для застосування нової мови
            recreate();
            
        } catch (Exception e) {
            Log.e(TAG, "updateLanguage: Помилка", e);
        }
    }

    private void setupThemeSettings() {
        try {
            RadioGroup themeGroup = findViewById(R.id.themeGroup);
            RadioButton lightThemeRadio = findViewById(R.id.radioLightTheme);
            RadioButton darkThemeRadio = findViewById(R.id.radioDarkTheme);
            
            // Отримуємо збережений режим теми
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
            int storedThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
            
            // Встановлюємо обране значення
            switch (storedThemeMode) {
                case AppCompatDelegate.MODE_NIGHT_YES:
                    darkThemeRadio.setChecked(true);
                    Log.d(TAG, "setupThemeSettings: Dark theme is active, selecting dark theme radio button");
                    break;
                case AppCompatDelegate.MODE_NIGHT_NO:
                    lightThemeRadio.setChecked(true);
                    Log.d(TAG, "setupThemeSettings: Light theme is active, selecting light theme radio button");
                    break;
                case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:
                default:
                    // За замовчуванням обираємо світлу тему, якщо системна
                    lightThemeRadio.setChecked(true);
                    Log.d(TAG, "setupThemeSettings: Using light theme as default for system theme");
                    break;
            }
            
            // Встановлюємо слухачі подій
            themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
                int selectedThemeMode;
                
                if (checkedId == R.id.radioLightTheme) {
                    selectedThemeMode = AppCompatDelegate.MODE_NIGHT_NO;
                } else if (checkedId == R.id.radioDarkTheme) {
                    selectedThemeMode = AppCompatDelegate.MODE_NIGHT_YES;
                } else {
                    // За замовчуванням використовуємо світлу тему
                    selectedThemeMode = AppCompatDelegate.MODE_NIGHT_NO;
                }
                
                if (selectedThemeMode != storedThemeMode) {
                    updateTheme(selectedThemeMode);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "setupThemeSettings: Error", e);
        }
    }

    /**
     * Оновлює тему додатку
     */
    private void updateTheme(int themeMode) {
        try {
            Log.d(TAG, "updateTheme: Зміна режиму теми на " + themeMode);
            
            // Зберігаємо тему в налаштуваннях
            SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit();
            editor.putInt("theme_mode", themeMode);
            editor.apply();
            
            // Оновлюємо назву теми для логування
            String themeName;
            switch (themeMode) {
                case AppCompatDelegate.MODE_NIGHT_YES:
                    themeName = "Темна";
                    break;
                case AppCompatDelegate.MODE_NIGHT_NO:
                    themeName = "Світла";
                    break;
                default:
                    themeName = "Системна";
            }
            Log.d(TAG, "updateTheme: Нова тема: " + themeName);
            
            // ПЕРЕД зміною теми запускаємо сервіс з wasFullyShutdown=false,
            // щоб він не відновлював запис при перезапуску
            startNoiseService();
            
            // Встановлюємо нову тему
            AppCompatDelegate.setDefaultNightMode(themeMode);
            
        } catch (Exception e) {
            Log.e(TAG, "updateTheme: Помилка", e);
        }
    }

    private void setupBackgroundServiceSwitch() {
        backgroundServiceSwitch = findViewById(R.id.backgroundServiceSwitch);
        backgroundServiceSwitch.setChecked(isBackgroundServiceEnabled);
        
        backgroundServiceSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Зберігаємо налаштування
            SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit();
            editor.putBoolean(Constants.PREF_BACKGROUND_SERVICE, isChecked);
            editor.apply();
            
            // Надсилаємо інформацію в сервіс
            Intent serviceIntent = new Intent(this, NoiseService.class);
            serviceIntent.setAction(Constants.ACTION_UPDATE_BACKGROUND_STATE);
            serviceIntent.putExtra(Constants.PREF_BACKGROUND_SERVICE, isChecked);
            serviceIntent.putExtra(Constants.EXTRA_WAS_FULLY_SHUTDOWN, false);
            startService(serviceIntent);
        });
    }
    
    /**
     * Налаштування елементів розділу калібрування
     */
    private void setupCalibrationSettings() {
        // Ініціалізація спінерів
        setupUpdateFrequencySpinner();
        setupSensitivitySpinner();
        setupSmoothingSpinner();
        setupMinNoiseSpinner();
        
        // Ініціалізація кнопок скидання
        setupResetButtons();
    }
    
    /**
     * Налаштування спінера для вибору частоти оновлення
     */
    private void setupUpdateFrequencySpinner() {
        updateFrequencySpinner = findViewById(R.id.updateFrequencySpinner);
        
        // Створюємо масив опцій для спінера
        String[] updateFrequencyOptions = new String[UPDATE_FREQUENCIES.length];
        for (int i = 0; i < UPDATE_FREQUENCIES.length; i++) {
            long freq = UPDATE_FREQUENCIES[i];
            // Форматуємо текст в залежності від значення
            if (freq < 1000) {
                updateFrequencyOptions[i] = freq + " " + getString(R.string.milliseconds);
            } else {
                updateFrequencyOptions[i] = (freq / 1000.0) + " " + getString(R.string.seconds);
            }
        }
        
        // Створюємо адаптер
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, updateFrequencyOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        updateFrequencySpinner.setAdapter(adapter);
        
        // Встановлюємо поточне значення
        updateFrequencySpinner.setSelection(currentUpdateFrequencyIndex);
        
        // Обробник зміни
        updateFrequencySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != currentUpdateFrequencyIndex) {
                    currentUpdateFrequencyIndex = position;
                    saveCalibrationSettings();
                    applyCalibrationSettings();
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Нічого не робимо
            }
        });
        
        // Кнопка скидання
        resetUpdateFrequencyButton = findViewById(R.id.resetUpdateFrequencyButton);
        resetUpdateFrequencyButton.setOnClickListener(v -> {
            updateFrequencySpinner.setSelection(DEFAULT_UPDATE_FREQUENCY_INDEX);
        });
    }
    
    /**
     * Налаштування спінера для вибору чутливості
     */
    private void setupSensitivitySpinner() {
        sensitivitySpinner = findViewById(R.id.sensitivitySpinner);
        
        // Створюємо масив опцій для спінера
        String[] sensitivityOptions = new String[SENSITIVITY_OFFSETS.length];
        for (int i = 0; i < SENSITIVITY_OFFSETS.length; i++) {
            double offset = SENSITIVITY_OFFSETS[i];
            String prefix = offset >= 0 ? "+" : "";
            sensitivityOptions[i] = prefix + offset + " dB";
        }
        
        // Створюємо адаптер
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, sensitivityOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sensitivitySpinner.setAdapter(adapter);
        
        // Встановлюємо поточне значення
        sensitivitySpinner.setSelection(currentSensitivityIndex);
        
        // Обробник зміни
        sensitivitySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != currentSensitivityIndex) {
                    currentSensitivityIndex = position;
                    saveCalibrationSettings();
                    applyCalibrationSettings();
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Нічого не робимо
            }
        });
        
        // Кнопка скидання
        resetSensitivityButton = findViewById(R.id.resetSensitivityButton);
        resetSensitivityButton.setOnClickListener(v -> {
            sensitivitySpinner.setSelection(DEFAULT_SENSITIVITY_INDEX);
        });
    }
    
    /**
     * Налаштування спінера для вибору згладжування
     */
    private void setupSmoothingSpinner() {
        smoothingSpinner = findViewById(R.id.smoothingSpinner);
        
        // Створюємо масив опцій для спінера
        String[] smoothingOptions = new String[SMOOTHING_FACTORS.length];
        for (int i = 0; i < SMOOTHING_FACTORS.length; i++) {
            double factor = SMOOTHING_FACTORS[i];
            if (factor == 0.0) {
                smoothingOptions[i] = getString(R.string.none);
            } else {
                // Перетворюємо коефіцієнт в зрозумілий відсоток згладжування
                int percent = (int) (factor * 100);
                smoothingOptions[i] = percent + "%";
            }
        }
        
        // Створюємо адаптер
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, smoothingOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        smoothingSpinner.setAdapter(adapter);
        
        // Встановлюємо поточне значення
        smoothingSpinner.setSelection(currentSmoothingIndex);
        
        // Обробник зміни
        smoothingSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != currentSmoothingIndex) {
                    currentSmoothingIndex = position;
                    saveCalibrationSettings();
                    applyCalibrationSettings();
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Нічого не робимо
            }
        });
        
        // Кнопка скидання
        resetSmoothingButton = findViewById(R.id.resetSmoothingButton);
        resetSmoothingButton.setOnClickListener(v -> {
            smoothingSpinner.setSelection(DEFAULT_SMOOTHING_INDEX);
        });
    }
    
    /**
     * Налаштування спінера для вибору мінімального значення шуму
     */
    private void setupMinNoiseSpinner() {
        minNoiseSpinner = findViewById(R.id.minNoiseSpinner);
        
        // Створюємо масив опцій для спінера
        String[] minNoiseOptions = new String[MIN_NOISE_LEVELS.length];
        for (int i = 0; i < MIN_NOISE_LEVELS.length; i++) {
            minNoiseOptions[i] = MIN_NOISE_LEVELS[i] + " dB";
        }
        
        // Створюємо адаптер
        ArrayAdapter<String> adapter = new ArrayAdapter<>(
            this, android.R.layout.simple_spinner_item, minNoiseOptions);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        minNoiseSpinner.setAdapter(adapter);
        
        // Встановлюємо поточне значення
        minNoiseSpinner.setSelection(currentMinNoiseIndex);
        
        // Обробник зміни
        minNoiseSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position != currentMinNoiseIndex) {
                    currentMinNoiseIndex = position;
                    saveCalibrationSettings();
                    applyCalibrationSettings();
                }
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Нічого не робимо
            }
        });
        
        // Кнопка скидання
        resetMinNoiseButton = findViewById(R.id.resetMinNoiseButton);
        resetMinNoiseButton.setOnClickListener(v -> {
            minNoiseSpinner.setSelection(DEFAULT_MIN_NOISE_LEVEL_INDEX);
        });
    }
    
    /**
     * Налаштування кнопки скидання всіх параметрів калібрування
     */
    private void setupResetButtons() {
        resetCalibrationButton = findViewById(R.id.resetCalibrationButton);
        resetCalibrationButton.setOnClickListener(v -> {
            new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_calibration_confirm_title)
                .setMessage(R.string.reset_calibration_confirm_message)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    resetAllCalibrationSettings();
                })
                .setNegativeButton(R.string.no, null)
                .show();
        });
    }
    
    /**
     * Скидання всіх налаштувань калібрування на заводські
     */
    private void resetAllCalibrationSettings() {
        updateFrequencySpinner.setSelection(DEFAULT_UPDATE_FREQUENCY_INDEX);
        sensitivitySpinner.setSelection(DEFAULT_SENSITIVITY_INDEX);
        smoothingSpinner.setSelection(DEFAULT_SMOOTHING_INDEX);
        minNoiseSpinner.setSelection(DEFAULT_MIN_NOISE_LEVEL_INDEX);
        
        // Повідомлення про успішне скидання
        Toast.makeText(this, R.string.reset_calibration_success, Toast.LENGTH_SHORT).show();
    }
    
    /**
     * Збереження налаштувань калібрування
     */
    private void saveCalibrationSettings() {
        SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt(PREF_UPDATE_FREQUENCY, currentUpdateFrequencyIndex);
        editor.putInt(PREF_SENSITIVITY, currentSensitivityIndex);
        editor.putInt(PREF_SMOOTHING, currentSmoothingIndex);
        editor.putInt(PREF_MIN_NOISE_LEVEL, currentMinNoiseIndex);
        editor.apply();
        
        Log.d(TAG, "saveCalibrationSettings: Збережено налаштування калібрування");
    }
    
    /**
     * Застосування налаштувань калібрування до сервісу
     */
    private void applyCalibrationSettings() {
        // Отримати реальні значення з індексів
        long updateFrequency = UPDATE_FREQUENCIES[currentUpdateFrequencyIndex];
        double sensitivityOffset = SENSITIVITY_OFFSETS[currentSensitivityIndex];
        double smoothingFactor = SMOOTHING_FACTORS[currentSmoothingIndex];
        double minNoiseLevel = MIN_NOISE_LEVELS[currentMinNoiseIndex];
        
        // Створити Intent для оновлення налаштувань у сервісі
        Intent serviceIntent = new Intent(this, NoiseService.class);
        serviceIntent.setAction(Constants.ACTION_UPDATE_CALIBRATION);
        serviceIntent.putExtra(Constants.EXTRA_UPDATE_FREQUENCY, updateFrequency);
        serviceIntent.putExtra(Constants.EXTRA_SENSITIVITY_OFFSET, sensitivityOffset);
        serviceIntent.putExtra(Constants.EXTRA_SMOOTHING_FACTOR, smoothingFactor);
        serviceIntent.putExtra(Constants.EXTRA_MIN_NOISE_LEVEL, minNoiseLevel);
        serviceIntent.putExtra(Constants.EXTRA_WAS_FULLY_SHUTDOWN, false);
        startService(serviceIntent);
        
        Log.d(TAG, "applyCalibrationSettings: Застосовано налаштування калібрування");
    }

    private void updateInterface(String languageCode) {
        // Оновлюємо текст в залежності від обраної мови
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.settings_title);
        }
        
        // Якщо потрібно, додаткове оновлення інтерфейсу в залежності від мови
        if (languageCode.equals("uk")) {
            // Специфічні для української мови налаштування інтерфейсу
        } else {
            // Специфічні для англійської мови налаштування інтерфейсу
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            // При натисканні кнопки "Назад" в тулбарі
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.action_power_off) {
            shutdownApp();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.settings_menu, menu);
        return true;
    }
    
    /**
     * Вимкнення додатку з перевіркою стану фонового моніторингу
     */
    private void shutdownApp() {
        boolean isBackgroundServiceEnabled = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE)
                .getBoolean(Constants.PREF_BACKGROUND_SERVICE, true);
        
        if (isBackgroundServiceEnabled) {
            // Показуємо діалогове вікно підтвердження
            new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.power_off_confirmation_title)
                    .setMessage(R.string.power_off_confirmation)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        terminateApp();
                    })
                    .setNegativeButton(R.string.no, null)
                    .show();
        } else {
            // Відразу закриваємо додаток
            terminateApp();
        }
    }
    
    /**
     * Завершення роботи додатку
     */
    private void terminateApp() {
        try {
            Log.d(TAG, "terminateApp: Початок завершення додатку");
            
            // Зберігаємо стан логування
            NoiseLogManager logManager = NoiseLogManager.getInstance(getApplicationContext());
            boolean wasLoggingEnabled = logManager.isLoggingEnabled();
            boolean wasLoggingPaused = logManager.isLoggingPaused();
            int logCount = logManager.getAllLogs().size();
            
            Log.d(TAG, "terminateApp: Стан логування перед збереженням - enabled=" + wasLoggingEnabled + 
                ", paused=" + wasLoggingPaused + ", count=" + logCount);
            
            // Зберігаємо налаштування логування
            logManager.forcePreserveLogs();
            
            // Перевіряємо, чи збереглися логи
            int newLogCount = logManager.getAllLogs().size();
            Log.d(TAG, "terminateApp: Кількість логів після збереження: " + newLogCount);
            
            // Зупиняємо сервіс зі збереженням стану
            Intent serviceIntent = new Intent(this, NoiseService.class);
            serviceIntent.setAction(Constants.ACTION_STOP_SERVICE);
            serviceIntent.putExtra(Constants.EXTRA_PRESERVE_STATE, true);
            serviceIntent.putExtra(Constants.EXTRA_WAS_FULLY_SHUTDOWN, false);
            startService(serviceIntent);
            
            // Даємо час на збереження стану
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    // Завершуємо всі активності
                    finishAffinity();
                    
                    // Завершуємо процес додатку
                    android.os.Process.killProcess(android.os.Process.myPid());
                } catch (Exception e) {
                    Log.e(TAG, "terminateApp: Помилка при завершенні додатку", e);
                }
            }, 500); // Затримка 500мс для збереження стану
        } catch (Exception e) {
            Log.e(TAG, "terminateApp: Помилка при завершенні додатку", e);
            // В будь-якому випадку намагаємось завершити додаток
            finishAffinity();
            android.os.Process.killProcess(android.os.Process.myPid());
        }
    }

    private void setupLoggingSettings() {
        loggingEnabledSwitch = findViewById(R.id.loggingEnabledSwitch);
        sampleFrequencySpinner = findViewById(R.id.sampleFrequencySpinner);
        retentionPeriodSpinner = findViewById(R.id.retentionPeriodSpinner);
        
        // Set up sample frequency spinner
        String[] frequencyOptions = getResources().getStringArray(R.array.logging_sample_frequencies);
        ArrayAdapter<String> frequencyAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, frequencyOptions);
        frequencyAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sampleFrequencySpinner.setAdapter(frequencyAdapter);
        
        // Set up retention period spinner
        String[] retentionOptions = getResources().getStringArray(R.array.logging_retention_periods);
        ArrayAdapter<String> retentionAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, retentionOptions);
        retentionAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        retentionPeriodSpinner.setAdapter(retentionAdapter);
        
        // Load initial values
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE);
        loggingEnabledSwitch.setChecked(NoiseLogManager.getInstance(this).isLoggingEnabled());
        sampleFrequencySpinner.setSelection(NoiseLogManager.getInstance(this).getSampleFrequencyIndex());
        retentionPeriodSpinner.setSelection(NoiseLogManager.getInstance(this).getRetentionPeriodIndex());
        
        // Set listeners
        loggingEnabledSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            // Якщо вимикаємо логування і є збережені логи, показуємо попередження
            if (!isChecked && !NoiseLogManager.getInstance(this).getAllLogs().isEmpty()) {
                new MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.logging_disabled_warning_title)
                    .setMessage(R.string.logging_disabled_warning)
                    .setPositiveButton(R.string.yes, (dialog, which) -> {
                        NoiseLogManager.getInstance(this).setLoggingEnabled(false);
                        NoiseLogManager.getInstance(this).setLoggingPaused(false); // Скидаємо стан паузи
                    })
                    .setNegativeButton(R.string.no, (dialog, which) -> {
                        // Повертаємо перемикач у положення "увімкнено"
                        loggingEnabledSwitch.setChecked(true);
                    })
                    .show();
            } else {
                NoiseLogManager.getInstance(this).setLoggingEnabled(isChecked);
                if (isChecked) {
                    // При ввімкненні логування скидаємо стан паузи
                    NoiseLogManager.getInstance(this).setLoggingPaused(false);
                }
            }
        });
        
        sampleFrequencySpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                NoiseLogManager.getInstance(SettingsActivity.this).setSampleFrequencyIndex(position);
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
        
        retentionPeriodSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                NoiseLogManager.getInstance(SettingsActivity.this).setRetentionPeriodIndex(position);
            }
            
            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });
    }

    /**
     * Запускає сервіс моніторингу шуму
     */
    private void startNoiseService() {
        try {
            Log.d(TAG, "startNoiseService: Запуск сервісу");
            
            // Перевіряємо, чи це зміна конфігурації (зміна мови або теми)
            boolean isChangingConfiguration = getChangingConfigurations() != 0;
            Log.d(TAG, "startNoiseService: isChangingConfiguration=" + isChangingConfiguration);
            
            Intent serviceIntent = new Intent(this, NoiseService.class);
            
            // Встановлюємо wasFullyShutdown=false для SettingsActivity, 
            // оскільки це НІКОЛИ не є повним перезапуском програми
            serviceIntent.putExtra(Constants.EXTRA_WAS_FULLY_SHUTDOWN, false);
            
            // Передаємо налаштування
            serviceIntent.putExtra(Constants.PREF_LANGUAGE, currentLanguage);
            serviceIntent.putExtra(Constants.PREF_BACKGROUND_SERVICE, isBackgroundServiceEnabled);
            
            Log.d(TAG, "startNoiseService: Запуск сервісу з wasFullyShutdown=false");
            
            // Запускаємо сервіс
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(serviceIntent);
            } else {
                startService(serviceIntent);
            }
        } catch (Exception e) {
            Log.e(TAG, "startNoiseService: Помилка", e);
        }
    }
} 