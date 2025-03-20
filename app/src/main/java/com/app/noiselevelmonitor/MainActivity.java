package com.app.noiselevelmonitor;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.Menu;
import android.view.MenuItem;
import java.util.Locale;
import java.util.ArrayList;
import android.content.SharedPreferences;
import com.github.anastr.speedviewlib.TubeSpeedometer;
import com.google.android.material.color.MaterialColors;
import android.graphics.Color;
import androidx.appcompat.widget.Toolbar;
import com.google.android.material.button.MaterialButton;
import android.net.Uri;
import android.provider.Settings;
import android.content.ActivityNotFoundException;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import android.widget.ImageView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import android.view.View;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.view.Gravity;
import androidx.appcompat.app.AppCompatDelegate;
import android.graphics.drawable.GradientDrawable;
import android.view.WindowManager;
import com.google.android.material.snackbar.BaseTransientBottomBar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import android.view.MotionEvent;
import android.graphics.drawable.ColorDrawable;
import androidx.appcompat.app.AlertDialog;
import com.github.anastr.speedviewlib.Speedometer;
import com.github.anastr.speedviewlib.components.Section;
import com.github.anastr.speedviewlib.components.Style;
import java.util.ArrayList;
import java.util.List;
import android.text.style.RelativeSizeSpan;

public class MainActivity extends BaseActivity {
    private static final int PERMISSION_REQUEST_CODE = 123;
    private static final String TAG = "MainActivity";
    
    // Константи, що дублюють значення з класу Constants
    private static final String PREFS_NAME = Constants.PREFS_NAME;
    private static final String PREF_LANGUAGE = Constants.PREF_LANGUAGE;
    private static final String PREF_MIN_NOISE = Constants.PREF_MIN_NOISE;
    private static final String PREF_MAX_NOISE = Constants.PREF_MAX_NOISE;
    private static final String PREF_TOTAL_NOISE = Constants.PREF_TOTAL_NOISE;
    private static final String PREF_MEASUREMENTS = Constants.PREF_MEASUREMENTS;
    private static final String PREF_MIN_NOISE_TIME = Constants.PREF_MIN_NOISE_TIME;
    private static final String PREF_MAX_NOISE_TIME = Constants.PREF_MAX_NOISE_TIME;
    private static final String PREF_BACKGROUND_SERVICE = Constants.PREF_BACKGROUND_SERVICE;
    
    // Специфічні константи для MainActivity
    private static final String PREF_IS_PAUSED = "is_paused";
    private static final String PREF_FIRST_LAUNCH = "first_launch";
    private static final String PREF_RESTART_COUNTER = "restart_counter";
    
    private TextView statusText;
    private TextView noiseLevelText;
    private TextView noiseLevelTitleText;
    private TextView statusTitleText;
    private TextView minNoiseText;
    private TextView avgNoiseText;
    private TextView maxNoiseText;
    private TextView backgroundStatusText;
    private boolean isServiceRunning = false;
    private boolean isRecording = true;
    private TubeSpeedometer noiseSpeedometer;
    private String currentLanguage;
    private AlertDialog batteryOptimizationDialog;
    private AlertDialog noiseReferenceDialog;
    private AlertDialog noiseTimeTooltipDialog;
    private boolean isInitialSetup = true;
    
    // Змінні для статистики
    private double minNoiseLevel = 0;
    private double maxNoiseLevel = 0;
    private double totalNoiseLevel = 0;
    private int noiseMeasurements = 0;
    private double averageNoiseLevel = 0;
    private MaterialButton resetStatsButton;
    private MaterialButton buttonPause;
    private ImageView minInfoIcon;
    private ImageView maxInfoIcon;
    private ImageView speedometerInfoIcon;
    private long minNoiseTime;
    private long maxNoiseTime;
    private String minNoiseTimeStr;
    private String maxNoiseTimeStr;
    private boolean isDarkTheme;
    private boolean isBackgroundServiceEnabled = true;
    private boolean isAppForeground = true;
    private BroadcastReceiver noiseReceiver;
    private BroadcastReceiver batteryOptimizationReceiver;
    private SharedPreferences prefs;
    
    // Flag to track if service was already started in this session
    private boolean isServiceStartInitiated = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        Log.d(TAG, "onCreate: Початок створення активності");
        
        // Отримуємо збережену мову
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        currentLanguage = prefs.getString(PREF_LANGUAGE, Locale.getDefault().getLanguage());
        
        // Встановлюємо прапорець початкового запуску, перевіряємо PREF_FIRST_LAUNCH
        boolean isFirstLaunch = !prefs.getBoolean(PREF_FIRST_LAUNCH, false);
        isInitialSetup = savedInstanceState == null && isFirstLaunch;
        
        // Якщо це перший запуск, запам'ятовуємо це
        if (isFirstLaunch) {
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(PREF_FIRST_LAUNCH, true);
            editor.apply();
            
            // Встановлюємо стан запису в активний при першому запуску
            isRecording = true;
            editor.putBoolean(Constants.PREF_IS_PAUSED, !isRecording);
            editor.apply();
        }
        
        Log.d(TAG, "onCreate: isInitialSetup=" + isInitialSetup + ", isFirstLaunch=" + isFirstLaunch);
        
        // Встановлюємо мову при першому створенні активності
        setLanguage(currentLanguage);
        
        // Отримуємо збережений режим теми і встановлюємо один раз при створенні
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        // Apply theme only if it hasn't been applied yet (prevent recreation loops)
        if (savedInstanceState == null) {
            AppCompatDelegate.setDefaultNightMode(themeMode);
        }
        
        setContentView(R.layout.activity_main);
        
        // Add field for battery optimization dialog
        batteryOptimizationDialog = null;
        
        // Відновлюємо стани з збережених значень
        isServiceRunning = NoiseServiceManager.getInstance(this).isServiceRunning();
        boolean isPaused = prefs.getBoolean(Constants.PREF_IS_PAUSED, false);
        isRecording = !isPaused;
        
        Log.d(TAG, "onCreate: Відновлено стани: isServiceRunning=" + isServiceRunning + ", isRecording=" + isRecording);
        
        // Налаштовуємо UI
        setupUI();
        
        // Налаштовуємо приймач для оновлень рівня шуму
        setupNoiseReceiver();
        
        // Перевіряємо наявність дозволів
        checkAndRequestPermissions();
        
        Log.d(TAG, "onCreate: Базова ініціалізація завершена");
    }
    
    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: Активність стартує");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: Активність відновлено");
        
        isAppForeground = true;
        
        // Оновлюємо опції меню
        invalidateOptionsMenu();
        
        // Оновлюємо статистику шуму
        loadNoiseStats();
        
        // КЛЮЧОВИЙ МОМЕНТ: тільки тут запускаємо сервіс, якщо він ще не запущений
        // і якщо у нас є всі необхідні дозволи
        if (hasRequiredPermissions()) {
            if (!isServiceRunning) {
                Log.d(TAG, "onResume: Сервіс не запущений, запускаємо");
                boolean started = NoiseServiceManager.getInstance(this).startService();
                if (started) {
                    isServiceRunning = true;
                    
                    // Завжди запускаємо запис при першому старті сервісу або якщо isRecording=true
                    if (isInitialSetup || isRecording) {
                        Log.d(TAG, "onResume: Запускаємо запис " + (isInitialSetup ? "(перший запуск додатку)" : "(відновлюємо активний стан)"));
                        
                        // Активуємо запис, тільки якщо він не активний
                        if (!isRecording) {
                            NoiseServiceManager.getInstance(this).toggleRecording();
                            isRecording = true;
                        }
                    }
                }
            } else {
                // Якщо сервіс вже запущений, просто повідомляємо йому, що активність на передньому плані
                Log.d(TAG, "onResume: Сервіс вже запущений, відправляємо ACTION_APP_TO_FOREGROUND");
                NoiseServiceManager.getInstance(this).notifyForeground();
                
                // Перевіряємо чи це перший запуск програми і автоматично вмикаємо запис
                if (isInitialSetup) {
                    Log.d(TAG, "onResume: Автоматично запускаємо запис при першому запуску");
                    
                    // Активуємо запис, тільки якщо він не активний
                    if (!isRecording) {
                        NoiseServiceManager.getInstance(this).toggleRecording();
                        isRecording = true;
                    }
                }
            }
        }
        
        // Оновлюємо UI відповідно до поточного стану
        updatePauseButton();
        updateStatus(isRecording ? getString(R.string.service_running) : getString(R.string.service_paused));
        
        // Встановлюємо фоновий режим
        if (isServiceRunning) {
            setupBackgroundServiceSettings(prefs);
        }
        
        // Налаштовуємо приймач для оновлень рівня шуму, якщо він ще не налаштований
        setupNoiseReceiver();
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause: Призупинення активності");
        
        // Відключаємо приймач оновлень
        if (noiseReceiver != null) {
            try {
                unregisterReceiver(noiseReceiver);
                Log.d(TAG, "onPause: Receiver відключено");
            } catch (Exception e) {
                Log.e(TAG, "onPause: Помилка відключення receiver", e);
            }
        }
        
        // Зберігаємо статистику шуму
        saveNoiseStats();
        
        // Зберігаємо поточний стан запису
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(Constants.PREF_IS_PAUSED, !isRecording);
        editor.apply();
        
        Log.d(TAG, "onPause: Поточний стан запису збережено: isRecording=" + isRecording);
        isAppForeground = false;
    }
    
    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: Активність зупинена");
        
        // Dismiss all dialogs
        dismissAllDialogs();
        
        // Скидаємо прапорець початкового запуску
        isInitialSetup = false;
        
        // Перевіряємо, чи це зупинка через зміну конфігурації
        boolean isChangingConfiguration = isChangingConfigurations();
        
        Log.d(TAG, "onStop: isChangingConfigurations=" + isChangingConfiguration);
        
        // Зберігаємо статистику шуму
        saveNoiseStats();
        
        // Якщо активність не фінішується і не змінює конфігурацію 
        // (тобто переходить у фон, але продовжує роботу)
        if (!isFinishing() && !isChangingConfiguration && isServiceRunning) {
            // Відправляємо сервісу інформацію, що додаток перейшов у фон
            Log.d(TAG, "onStop: Відправляємо ACTION_APP_TO_BACKGROUND");
            NoiseServiceManager.getInstance(this).notifyBackground();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy: Зупинка активності");
        
        // Dismiss all dialogs
        dismissAllDialogs();
        
        // Перевіряємо, чи це зміна конфігурації
        boolean isChangingConfiguration = isChangingConfigurations();
        Log.d(TAG, "onDestroy: isChangingConfigurations=" + isChangingConfiguration);
        
        if (!isChangingConfiguration) {
            // Повне завершення роботи додатку
            Log.d(TAG, "onDestroy: Повне завершення роботи додатку");
            
            // Перевіряємо, чи увімкнено фоновий моніторинг
            boolean isBackgroundServiceEnabled = prefs.getBoolean(PREF_BACKGROUND_SERVICE, true);
            
            // Зупиняємо сервіс, якщо фоновий моніторинг вимкнено
            if (!isBackgroundServiceEnabled && isServiceRunning) {
                Log.d(TAG, "onDestroy: Зупиняємо сервіс (фоновий моніторинг вимкнено)");
                NoiseServiceManager.getInstance(this).stopService();
                isServiceRunning = false;
            } else if (isServiceRunning) {
                Log.d(TAG, "onDestroy: Сервіс продовжує працювати у фоні");
            }
        }
        
        // Відключаємо приймачі
        try {
            if (noiseReceiver != null) {
                try {
                    unregisterReceiver(noiseReceiver);
                } catch (Exception ignored) {}
            }
            
            if (batteryOptimizationReceiver != null) {
                try {
                    unregisterReceiver(batteryOptimizationReceiver);
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            Log.e(TAG, "onDestroy: Помилка відключення приймачів", e);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.d(TAG, "onNewIntent: Отримано новий Intent");
        
        // Перевіряємо чи є сервіс запущений
        if (!isServiceRunning) {
            setupNoiseReceiver();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        
        // Пункт "Налаштування" залишаємо
        MenuItem settingsItem = menu.findItem(R.id.action_settings);
        if (settingsItem != null) {
            settingsItem.setVisible(true);
        }
        
        // Пункт "Перегляд логів" показуємо лише якщо логування увімкнено
        MenuItem viewLogsItem = menu.findItem(R.id.action_view_logs);
        if (viewLogsItem != null) {
            boolean loggingEnabled = NoiseLogManager.getInstance(this).isLoggingEnabled();
            viewLogsItem.setVisible(loggingEnabled);
        }
        
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.action_settings) {
            // Відкриваємо налаштування
            Intent settingsIntent = new Intent(this, SettingsActivity.class);
            startActivity(settingsIntent);
            return true;
        } else if (id == R.id.action_view_logs) {
            // Відкриваємо активність перегляду логів
            Intent logsIntent = new Intent(this, NoiseLogsActivity.class);
            startActivity(logsIntent);
            return true;
        }
        
        return super.onOptionsItemSelected(item);
    }

    private void setLanguage(String languageCode) {
        // Skip if language is the same
        if (languageCode.equals(currentLanguage) && !isInitialSetup) {
            return;
        }

        try {
            // Отримуємо додаток і встановлюємо мову через нього
            NoiseMonitorApplication app = (NoiseMonitorApplication) getApplication();
            app.setLocale(languageCode);
            
            // Оновлюємо поточну мову в активності
            currentLanguage = languageCode;

            // Відправляємо інформацію про зміну мови в сервіс
            Intent serviceIntent = new Intent(this, NoiseService.class);
            serviceIntent.putExtra(Constants.PREF_LANGUAGE, languageCode);
            startService(serviceIntent);

            // Перевіряємо, чи це початковий запуск - якщо так, просто встановлюємо мову без перезапуску
            if (isInitialSetup) {
                isInitialSetup = false;
                return;
            }

            // Перезапускаємо активність для повного застосування нової мови
            Intent restartIntent = new Intent(this, MainActivity.class);
            restartIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(restartIntent);
            finish();
        } catch (Exception e) {
            Log.e(TAG, "setLanguage: Помилка при зміні мови", e);
            Toast.makeText(this, "Помилка при зміні мови: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void updateAllTexts() {
        // Отримуємо поточну мову
        String currentLang = getCurrentLanguage();
        
        // Оновлюємо локаль активності
        try {
            NoiseMonitorApplication app = (NoiseMonitorApplication) getApplication();
            app.setLocale(currentLang);
            
            // Примусово оновлюємо мову в сервісі
            Intent serviceIntent = new Intent(this, NoiseService.class);
            serviceIntent.putExtra(PREF_LANGUAGE, currentLang);
            startService(serviceIntent);
        } catch (Exception e) {
            Log.e(TAG, "updateAllTexts: Помилка оновлення локалі", e);
        }
        
        // Оновлюємо UI на головному потоці
        runOnUiThread(() -> {
            try {
                // Оновлюємо заголовок статусу
                if (statusTitleText != null) {
                    statusTitleText.setText(getString(R.string.status));
                }
                
                // Оновлюємо решту текстових елементів
                if (noiseLevelText != null) {
                    noiseLevelText.setText(getString(R.string.noise_level_placeholder));
                }
                if (noiseLevelTitleText != null) {
                    noiseLevelTitleText.setText(getString(R.string.noise_level));
                }
                if (buttonPause != null) {
                    updatePauseButton();
                }
                if (resetStatsButton != null) {
                    resetStatsButton.setText(getString(R.string.button_reset_stats));
                }
                
                // Оновлюємо заголовок додатку
                if (getSupportActionBar() != null) {
                    getSupportActionBar().setTitle(R.string.app_name);
                }
                
                // Оновлюємо статистику
                updateStatsText();
            } catch (Exception e) {
                Log.e(TAG, "updateAllTexts: Помилка при оновленні текстів", e);
            }
        });
    }

    private void showError(String message) {
        Log.e(TAG, "showError: " + message);
        runOnUiThread(() -> {
            if (statusText != null) {
                statusText.setText(getString(R.string.error_prefix, message));
            }
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        });
    }

    // Налаштування приймача оновлень від сервісу
    private void setupNoiseReceiver() {
        try {
            // Відключаємо попередній приймач, якщо він існує
            if (noiseReceiver != null) {
                try {
                    unregisterReceiver(noiseReceiver);
                } catch (Exception ignored) {
                    // Ігноруємо помилку, якщо приймач не був зареєстрований
                }
            }
            
            // Створюємо новий приймач
            noiseReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    try {
                        if (intent == null || intent.getAction() == null || 
                            !intent.getAction().equals(Constants.ACTION_NOISE_UPDATE)) {
                            return;
                        }
                        
                        // Оновлення стану запису має пріоритет
                        if (intent.hasExtra(Constants.EXTRA_RECORDING_STATE)) {
                            boolean newRecordingState = intent.getBooleanExtra(Constants.EXTRA_RECORDING_STATE, false);
                            updateRecordingState(newRecordingState);
                        }
                        
                        // Оновлення статусу
                        if (intent.hasExtra(Constants.EXTRA_STATUS)) {
                            String status = intent.getStringExtra(Constants.EXTRA_STATUS);
                            updateStatus(status);
                        }
                        
                        // Оновлення стану фонового моніторингу
                        if (intent.hasExtra(Constants.EXTRA_BACKGROUND_SERVICE_STATE)) {
                            boolean isBackgroundEnabled = intent.getBooleanExtra(Constants.EXTRA_BACKGROUND_SERVICE_STATE, true);
                            updateBackgroundStatus(isBackgroundEnabled);
                        }
                        
                        // Оновлення рівня шуму
                        if (intent.hasExtra(Constants.EXTRA_NOISE_LEVEL)) {
                            double db = intent.getDoubleExtra(Constants.EXTRA_NOISE_LEVEL, 0.0);
                            updateNoiseLevel(db);
                            
                            // Оновлення статистики
                            if (intent.hasExtra("min_noise")) {
                                minNoiseLevel = intent.getDoubleExtra("min_noise", Double.MAX_VALUE);
                                maxNoiseLevel = intent.getDoubleExtra("max_noise", Double.MIN_VALUE);
                                totalNoiseLevel = intent.getDoubleExtra("total_noise", 0);
                                noiseMeasurements = intent.getIntExtra("measurements", 0);
                                minNoiseTime = intent.getLongExtra("min_noise_time", 0);
                                maxNoiseTime = intent.getLongExtra("max_noise_time", 0);
                                
                                // Перевіряємо і коригуємо значення шуму
                                checkValidNoiseRange();
                                
                                // Розрахунок середнього рівня шуму
                                if (noiseMeasurements > 0) {
                                    averageNoiseLevel = totalNoiseLevel / noiseMeasurements;
                                } else {
                                    averageNoiseLevel = 0;
                                }
                                
                                updateStatsText();
                            }
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Помилка при отриманні broadcast: " + e.getMessage());
                    }
                }
            };
            
            // Реєстрація приймача
            IntentFilter filter = new IntentFilter(Constants.ACTION_NOISE_UPDATE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                registerReceiver(noiseReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(noiseReceiver, filter);
            }
            
        } catch (Exception e) {
            Log.e(TAG, "Помилка при налаштуванні приймача: " + e.getMessage());
            Toast.makeText(this, getString(R.string.error_general), Toast.LENGTH_SHORT).show();
        }
    }

    // Оновлення статусу фонового моніторингу
    private void updateBackgroundStatus(boolean isEnabled) {
        Log.d(TAG, "updateBackgroundStatus: Оновлення статусу фонового моніторингу: " + isEnabled);
        runOnUiThread(() -> {
            try {
                if (backgroundStatusText != null) {
                    backgroundStatusText.setText(isEnabled ? 
                        getString(R.string.background_monitoring_enabled) : 
                        getString(R.string.background_monitoring_disabled));
                    Log.d(TAG, "updateBackgroundStatus: Статус фонового моніторингу оновлено успішно");
                } else {
                    Log.e(TAG, "updateBackgroundStatus: backgroundStatusText є null");
                }
            } catch (Exception e) {
                Log.e(TAG, "updateBackgroundStatus: Помилка при оновленні статусу фонового моніторингу", e);
            }
        });
    }

    private void updateStatus(String status) {
        Log.d(TAG, "updateStatus: Оновлення статусу: " + status);
        runOnUiThread(() -> {
            try {
                if (statusText != null) {
                    statusText.setText(status);
                    Log.d(TAG, "updateStatus: Статус оновлено успішно");
                } else {
                    Log.e(TAG, "updateStatus: statusText є null");
                }
            } catch (Exception e) {
                Log.e(TAG, "updateStatus: Помилка при оновленні статусу", e);
            }
        });
    }

    private void updateNoiseLevel(double db) {
        if (noiseLevelText == null || noiseSpeedometer == null) {
            Log.e(TAG, "updateNoiseLevel: Views не ініціалізовано");
            return;
        }
        
        runOnUiThread(() -> {
            try {
                // Форматуємо текст
                String formattedText = String.format(Locale.getDefault(), getString(R.string.noise_level_format), db);
                noiseLevelText.setText(formattedText);
                
                // ВАЖЛИВО: Конвертуємо double в float для setSpeedAt
                noiseSpeedometer.setSpeedAt((float) db);
                
                // Визначаємо, яку позначку підсвітити, на основі поточного рівня шуму
                updateSpeedometerTickHighlighting((float) db);
                
                Log.d(TAG, "updateNoiseLevel: Оновлено рівень шуму та спідометр: " + db + " дБ");
            } catch (Exception e) {
                Log.e(TAG, "updateNoiseLevel: Помилка при оновленні рівня шуму", e);
            }
        });
    }

    private void checkAndRequestPermissions() {
        Log.d(TAG, "checkAndRequestPermissions: Перевірка дозволів");
        
        List<String> permissionsNeeded = new ArrayList<>();
        
        // Перевірка і додавання необхідних дозволів
        if (checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.RECORD_AUDIO);
            Log.d(TAG, "Permission " + android.Manifest.permission.RECORD_AUDIO + " is не надано");
        } else {
            Log.d(TAG, "Permission " + android.Manifest.permission.RECORD_AUDIO + " is надано");
        }
        
        if (checkSelfPermission(android.Manifest.permission.FOREGROUND_SERVICE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.FOREGROUND_SERVICE);
            Log.d(TAG, "Permission " + android.Manifest.permission.FOREGROUND_SERVICE + " is не надано");
        } else {
            Log.d(TAG, "Permission " + android.Manifest.permission.FOREGROUND_SERVICE + " is надано");
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && 
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.POST_NOTIFICATIONS);
            Log.d(TAG, "Permission " + android.Manifest.permission.POST_NOTIFICATIONS + " is не надано");
        } else {
            Log.d(TAG, "Permission " + android.Manifest.permission.POST_NOTIFICATIONS + " is надано");
        }
        
        // Перевірка дозволу WAKE_LOCK
        if (checkSelfPermission(android.Manifest.permission.WAKE_LOCK) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(android.Manifest.permission.WAKE_LOCK);
            Log.d(TAG, "Permission " + android.Manifest.permission.WAKE_LOCK + " is не надано");
        } else {
            Log.d(TAG, "Permission " + android.Manifest.permission.WAKE_LOCK + " is надано");
        }
        
        // Якщо є дозволи які потрібно запитати
        if (!permissionsNeeded.isEmpty()) {
            Log.d(TAG, "checkAndRequestPermissions: Запит дозволів: " + permissionsNeeded.size());
            requestPermissions(permissionsNeeded.toArray(new String[0]), PERMISSION_REQUEST_CODE);
            updateStatus(getString(R.string.waiting_permissions));
        } else {
            Log.d(TAG, "checkAndRequestPermissions: Всі дозволи надані");
            checkBatteryOptimization();
            // НЕ запускаємо сервіс тут, щоб уникнути подвійного запуску
        }
    }

    private void checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            boolean batteryOptimizationDenied = prefs.getBoolean(Constants.PREF_BATTERY_OPTIMIZATION_DENIED, false);
            
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            if (!pm.isIgnoringBatteryOptimizations(getPackageName())) {
                if (!batteryOptimizationDenied) {
                    showBatteryOptimizationDialog();
                } else {
                    // Якщо користувач раніше відмовився, просто запускаємо сервіс
                    startNoiseService();
                }
            } else {
                startNoiseService();
            }
        }
    }

    private void showBatteryOptimizationDialog() {
        AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.battery_optimization_title)
            .setMessage(R.string.battery_optimization_message)
            .setPositiveButton(R.string.battery_optimization_button, (dialogInterface, which) -> {
                try {
                    Intent intent = new Intent();
                    String packageName = getPackageName();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + packageName));
                    startActivity(intent);
                    Toast.makeText(this, R.string.battery_optimization_hint, Toast.LENGTH_LONG).show();
                } catch (ActivityNotFoundException e) {
                    Log.e(TAG, "Battery optimization settings not available", e);
                    Toast.makeText(this, R.string.battery_optimization_error, Toast.LENGTH_LONG).show();
                }
            })
            .setNegativeButton(R.string.battery_optimization_negative, (dialogInterface, which) -> {
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putBoolean(Constants.PREF_BATTERY_OPTIMIZATION_DENIED, true);
                editor.apply();
                Toast.makeText(this, R.string.battery_optimization_warning, Toast.LENGTH_LONG).show();
                startNoiseService();
            })
            .create();
            
        // Store dialog reference to dismiss it when activity is destroyed
        batteryOptimizationDialog = dialog;
        
        // Only show if not changing configurations
        if (!isFinishing() && !isChangingConfigurations()) {
            dialog.show();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            
            for (int i = 0; i < permissions.length; i++) {
                String permission = permissions[i];
                int result = grantResults[i];
                
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Log.d(TAG, "onRequestPermissionsResult: Permission " + permission + " не надано");
                } else {
                    Log.d(TAG, "onRequestPermissionsResult: Permission " + permission + " надано");
                }
            }
            
            if (allGranted) {
                Log.d(TAG, "onRequestPermissionsResult: Всі дозволи надані");
                checkBatteryOptimization();
                
                // Дозволяємо системі запустити сервіс при потребі в onCreate, але не робимо це напряму тут
                // Повідомляємо користувача, що дозволи отримані
                updateStatus(getString(R.string.service_starting));
            } else {
                Log.d(TAG, "onRequestPermissionsResult: Не всі дозволи надано");
                
                // Зберігаємо факт відмови від дозволів
                SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
                editor.putBoolean(Constants.PREF_PERMISSIONS_DENIED, true);
                editor.apply();
                
                // Повідомляємо користувача про важливість дозволів
                updateStatus(getString(R.string.error_permissions));
                Toast.makeText(this, R.string.error_permissions, Toast.LENGTH_LONG).show();
            }
        }
    }

    private void stopNoiseService() {
        stopNoiseService(false);
    }
    
    private void stopNoiseService(boolean preserveState) {
        try {
            if (!isServiceRunning) {
                Log.d(TAG, "stopNoiseService: Сервіс не запущений, пропускаємо");
                return;
            }
            
            Log.d(TAG, "stopNoiseService: Зупинка сервісу, preserveState=" + preserveState);
            
            // Зупиняємо сервіс через менеджер
            boolean stopped = NoiseServiceManager.getInstance(this).stopService();
            
            if (stopped) {
                // Оновлюємо стан
                isServiceRunning = false;
                
                // Зберігаємо поточний стан запису, якщо потрібно
                if (preserveState) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(Constants.PREF_IS_PAUSED, !isRecording);
                    editor.apply();
                    
                    Log.d(TAG, "stopNoiseService: Збережено стан запису: isRecording=" + isRecording);
                }
                
                // Оновлюємо UI
                updateStatus(getString(R.string.service_stopped));
                
                Log.d(TAG, "stopNoiseService: Сервіс зупинено успішно");
            } else {
                Log.d(TAG, "stopNoiseService: Не вдалося зупинити сервіс");
            }
        } catch (Exception e) {
            Log.e(TAG, "stopNoiseService: Помилка при зупинці сервісу", e);
        }
    }

    private void startNoiseService() {
        try {
            Log.d(TAG, "startNoiseService: Запуск сервісу");
            
            // Перевіряємо, чи вже запущений сервіс
            if (isServiceRunning) {
                Log.d(TAG, "startNoiseService: Сервіс вже запущений, пропускаємо");
                return;
            }
            
            // Запускаємо сервіс через менеджер
            boolean started = NoiseServiceManager.getInstance(this).startService();
            
            if (started) {
                // Оновлюємо стан
                isServiceRunning = true;
                
                // Оновлюємо UI
                updatePauseButton();
                updateStatus(getString(R.string.service_starting));
                
                Log.d(TAG, "startNoiseService: Сервіс запущено успішно");
            } else {
                Log.d(TAG, "startNoiseService: Не вдалося запустити сервіс");
            }
        } catch (Exception e) {
            String errorMsg = getString(R.string.error_service_start, e.getMessage());
            Log.e(TAG, "startNoiseService: " + errorMsg, e);
            showError(errorMsg);
        }
    }

    /**
     * Перезапускає сервіс безпечно, враховуючи стан перезапуску
     * Запобігає перезапуску під час зміни теми
     */
    private void safeRestartService() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int restartCounter = prefs.getInt(PREF_RESTART_COUNTER, 0);
        
        if (restartCounter > 0) {
            Log.d(TAG, "safeRestartService: Пропускаємо перезапуск сервісу під час зміни теми");
            return;
        }
        
        Log.d(TAG, "safeRestartService: Безпечний перезапуск сервісу");
        restartNoiseService();
    }

    private void restartNoiseService() {
        Log.d(TAG, "restartNoiseService: Початок перезапуску сервісу");
        stopNoiseService();
        
        // Даємо час на зупинку сервісу
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            startNoiseService();
        }, 500);
    }

    private void toggleRecording() {
        try {
            if (isServiceRunning) {
                // Перемикаємо стан запису через менеджер
                NoiseServiceManager.getInstance(this).toggleRecording();
                
                // Оптимістично оновлюємо стан - реальний стан буде оновлено через broadcast
                isRecording = !isRecording;
                
                // Оновлюємо UI
                updatePauseButton();
                updateStatus(isRecording ? getString(R.string.service_running) : getString(R.string.service_paused));
                
                Log.d(TAG, "toggleRecording: Стан запису перемкнуто на: isRecording=" + isRecording);
            } else {
                // Якщо сервіс не запущений, запускаємо його
                Log.d(TAG, "toggleRecording: Сервіс не запущений, запускаємо");
                startNoiseService();
            }
        } catch (Exception e) {
            String errorMsg = getString(R.string.error_toggle_recording, e.getMessage());
            Log.e(TAG, "toggleRecording: " + errorMsg, e);
            showError(errorMsg);
        }
    }

    private void updatePauseButton() {
        Log.d(TAG, "updatePauseButton: Оновлення кнопки паузи, стан isRecording = " + isRecording);
        runOnUiThread(() -> {
            try {
                if (buttonPause != null) {
                    int textResId = isRecording ? R.string.button_pause : R.string.button_resume;
                    buttonPause.setText(textResId);
                    Log.d(TAG, "updatePauseButton: Встановлено текст кнопки: " + getString(textResId));
                } else {
                    Log.e(TAG, "updatePauseButton: buttonPause є null");
                }
            } catch (Exception e) {
                Log.e(TAG, "updatePauseButton: Помилка при оновленні кнопки", e);
            }
        });
    }

    private void setupNoiseSpeedometer() {
        Log.d(TAG, getString(R.string.setting_up_speedometer));
        try {
            // Базові налаштування
            noiseSpeedometer.setMinSpeed(0);
            noiseSpeedometer.setMaxSpeed(120);
            noiseSpeedometer.setUnit("dB");
            noiseSpeedometer.setWithTremble(false);
            
            // Налаштування міток - встановлюємо 7 позначок (0, 20, 40, 60, 80, 100, 120)
            noiseSpeedometer.setTickNumber(7);
            
            // Встановлюємо правильне відображення для позначок
            noiseSpeedometer.setOnPrintTickLabel((position, tick) -> {
                // Налаштовуємо конкретні значення для кожної позиції
                if (position == 0) return "0";
                else if (position == 1) return "20";
                else if (position == 2) return "40";
                else if (position == 3) return "60";
                else if (position == 4) return "80";
                else if (position == 5) return "100";
                else if (position == 6) return "120";
                else return String.valueOf((int)(tick * 120));
            });
            
            // Налаштування розміру тексту
            noiseSpeedometer.setTextSize(dpToPx(16));
            noiseSpeedometer.setSpeedTextSize(dpToPx(30));
            noiseSpeedometer.setUnitTextSize(dpToPx(16));
            
            // Налаштування градієнту кольорів для секцій спідометра
            setupSpeedometerGradient();
            
            Log.d(TAG, getString(R.string.speedometer_setup_success));
        } catch (Exception e) {
            Log.e(TAG, getString(R.string.error_speedometer_setup, e.getMessage()), e);
        }
    }

    /**
     * Налаштовує кольоровий градієнт для спідометра.
     * Створює градієнт кольорів від блакитного до червоного в залежності від рівня шуму.
     */
    private void setupSpeedometerGradient() {
        try {
            // Очищуємо попередні секції
            noiseSpeedometer.clearSections();
            
            // Створюємо градієнт кольорів від блакитного до червоного
            List<Section> sections = new ArrayList<>();
            
            // Блакитний (найнижчий рівень шуму)
            sections.add(new Section(0f, 0.17f, Color.parseColor("#2196F3"))); // Блакитний (0-20)
            
            // Блідо-зелений
            sections.add(new Section(0.17f, 0.33f, Color.parseColor("#8BC34A"))); // Блідо-зелений (20-40)
            
            // Зелений
            sections.add(new Section(0.33f, 0.5f, Color.parseColor("#4CAF50"))); // Зелений (40-60)
            
            // Оранжевий
            sections.add(new Section(0.5f, 0.67f, Color.parseColor("#FF9800"))); // Оранжевий (60-80)
            
            // Темно-оранжевий
            sections.add(new Section(0.67f, 0.83f, Color.parseColor("#FF5722"))); // Темно-оранжевий (80-100)
            
            // Червоний (найвищий рівень шуму)
            sections.add(new Section(0.83f, 1f, Color.parseColor("#F44336"))); // Червоний (100-120)
            
            // Додаємо секції до спідометра
            noiseSpeedometer.addSections(sections);
            
            Log.d(TAG, "Градієнт кольорів для спідометра налаштовано успішно");
        } catch (Exception e) {
            Log.e(TAG, "Помилка при налаштуванні градієнту кольорів", e);
        }
    }

    /**
     * Оновлює підсвічування позначок на спідометрі залежно від поточного рівня шуму
     * @param noiseLevel поточний рівень шуму в дБ
     */
    private void updateSpeedometerTickHighlighting(float noiseLevel) {
        try {
            // Колір для підсвічування - такий самий як у snackbar
            int highlightColor = Color.parseColor("#2196F3"); // Голубий колір
            
            // Визначаємо поточний діапазон шуму та відповідне значення позначки
            int tickValue;
            if (noiseLevel >= 0 && noiseLevel < 20) {
                tickValue = 0;
            } else if (noiseLevel >= 20 && noiseLevel < 40) {
                tickValue = 20;
            } else if (noiseLevel >= 40 && noiseLevel < 60) {
                tickValue = 40;
            } else if (noiseLevel >= 60 && noiseLevel < 80) {
                tickValue = 60;
            } else if (noiseLevel >= 80 && noiseLevel < 100) {
                tickValue = 80;
            } else if (noiseLevel >= 100 && noiseLevel < 120) {
                tickValue = 100;
            } else {
                tickValue = 120;
            }
            
            // Зберігаємо значення для використання у лямбді
            final int finalTickValue = tickValue;
            
            // Додаємо контроль для відображення лейблів з підсвіткою
            noiseSpeedometer.setOnPrintTickLabel((position, tick) -> {
                String label;
                int value;
                
                // Прив'язуємо значення до позиції тіка
                if (position == 0) { value = 0; label = "0"; }
                else if (position == 1) { value = 20; label = "20"; }
                else if (position == 2) { value = 40; label = "40"; }
                else if (position == 3) { value = 60; label = "60"; }
                else if (position == 4) { value = 80; label = "80"; }
                else if (position == 5) { value = 100; label = "100"; }
                else if (position == 6) { value = 120; label = "120"; }
                else { value = (int)(tick * 120); label = String.valueOf(value); }
                
                // Якщо це позначка, яку потрібно підсвітити
                if (value == finalTickValue) {
                    // Створюємо SpannableString для форматування тексту - зміна кольору і розміру
                    SpannableString spannable = new SpannableString(label);
                    
                    // Встановлюємо колір тексту
                    spannable.setSpan(new ForegroundColorSpan(highlightColor),
                            0, label.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            
                    // Встановлюємо жирний шрифт
                    spannable.setSpan(new StyleSpan(Typeface.BOLD),
                            0, label.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            
                    // Збільшений розмір (відносний розмір +1.4 від звичайного) - було 1.2f
                    spannable.setSpan(new RelativeSizeSpan(1.4f),
                            0, label.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                            
                    return spannable;
                }
                
                // Повертаємо звичайний лейбл для інших значень
                return label;
            });
            
            // Оновлюємо спідометр, щоб зміни набули чинності
            noiseSpeedometer.invalidate();
            
        } catch (Exception e) {
            Log.e(TAG, "updateSpeedometerTickHighlighting: Помилка при підсвічуванні позначок на спідометрі", e);
        }
    }

    private float dpToPx(float dp) {
        return dp * getResources().getDisplayMetrics().density;
    }

    private void loadNoiseStats() {
        try {
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            
            // Перевіряємо, чи це перший запуск
            boolean isFirstLaunch = !prefs.getBoolean(PREF_FIRST_LAUNCH, false);
            
            if (isFirstLaunch) {
                // При першому запуску встановлюємо всі значення в 0
                minNoiseLevel = 0;
                maxNoiseLevel = 0;
                totalNoiseLevel = 0;
                noiseMeasurements = 0;
                minNoiseTime = 0;
                maxNoiseTime = 0;
                averageNoiseLevel = 0;
            } else {
                // Для наступних запусків завантажуємо збережені значення
                minNoiseLevel = prefs.getFloat(PREF_MIN_NOISE, Float.MAX_VALUE);
                maxNoiseLevel = prefs.getFloat(PREF_MAX_NOISE, Float.MIN_VALUE);
                totalNoiseLevel = prefs.getFloat(PREF_TOTAL_NOISE, 0.0f);
                noiseMeasurements = prefs.getInt(PREF_MEASUREMENTS, 0);
                minNoiseTime = prefs.getLong(PREF_MIN_NOISE_TIME, 0);
                maxNoiseTime = prefs.getLong(PREF_MAX_NOISE_TIME, 0);
                
                // Перевіряємо і коригуємо значення шуму
                checkValidNoiseRange();
                
                // Обчислюємо середній рівень шуму, якщо є достатньо вимірювань
                if (noiseMeasurements > 0) {
                    averageNoiseLevel = totalNoiseLevel / noiseMeasurements;
                } else {
                    averageNoiseLevel = 0.0;
                }
            }
            
            // Відновлюємо запис з налаштувань
            boolean wasRecordingPaused = prefs.getBoolean(Constants.PREF_IS_PAUSED, false);
            isRecording = !wasRecordingPaused;
            
            // Завантажуємо стан фонового моніторингу
            boolean isBackgroundEnabled = prefs.getBoolean(Constants.PREF_BACKGROUND_SERVICE, true);
            isBackgroundServiceEnabled = isBackgroundEnabled;
            updateBackgroundStatus(isBackgroundEnabled);
            
            // Оновлюємо відображення статистики
            updateStatsText();
        } catch (Exception e) {
            Log.e(TAG, "loadNoiseStats: Помилка при завантаженні статистики", e);
        }
    }

    private void saveNoiseStats() {
        // Перевіряємо і коригуємо значення шуму перед збереженням
        checkValidNoiseRange();
        
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putFloat(PREF_MIN_NOISE, (float) minNoiseLevel);
        editor.putFloat(PREF_MAX_NOISE, (float) maxNoiseLevel);
        editor.putFloat(PREF_TOTAL_NOISE, (float) totalNoiseLevel);
        editor.putInt(PREF_MEASUREMENTS, noiseMeasurements);
        editor.putLong(PREF_MIN_NOISE_TIME, minNoiseTime);
        editor.putLong(PREF_MAX_NOISE_TIME, maxNoiseTime);
        editor.apply();
    }

    // Оновлення тексту статистики
    private void updateStatsText() {
        // Перевіряємо і коригуємо значення шуму
        checkValidNoiseRange();
        
        double avgNoise = noiseMeasurements > 0 ? totalNoiseLevel / noiseMeasurements : 0;
        
        runOnUiThread(() -> {
            if (minNoiseText != null) {
                minNoiseText.setText(String.format(Locale.getDefault(), 
                        getString(R.string.min_noise_label), minNoiseLevel));
            }
            if (avgNoiseText != null) {
                avgNoiseText.setText(String.format(Locale.getDefault(), 
                        getString(R.string.avg_noise_label), avgNoise));
            }
            if (maxNoiseText != null) {
                maxNoiseText.setText(String.format(Locale.getDefault(), 
                        getString(R.string.max_noise_label), maxNoiseLevel));
            }
            
            // Іконки інфо завжди мають бути видимі
            if (minInfoIcon != null) {
                minInfoIcon.setVisibility(View.VISIBLE);
            }
            
            if (maxInfoIcon != null) {
                maxInfoIcon.setVisibility(View.VISIBLE);
            }
        });
    }

    private void resetStats() {
        try {
            // Скидання статистики через менеджер
            NoiseServiceManager.getInstance(this).resetStats();
            
            // Локальне скидання для UI
            minNoiseLevel = 0;
            maxNoiseLevel = 0;
            totalNoiseLevel = 0;
            noiseMeasurements = 0;
            minNoiseTime = 0;
            maxNoiseTime = 0;
            minNoiseTimeStr = "";
            maxNoiseTimeStr = "";
            
            // Оновлюємо відображення
            updateStatsText();
            
            Log.d(TAG, "resetStats: Статистика скинута");
        } catch (Exception e) {
            Log.e(TAG, "resetStats: Помилка при скиданні статистики", e);
        }
    }

    private void showNoiseTimeTooltip(boolean isMin) {
        // Перевіряємо, чи є дані для відображення
        long time = isMin ? minNoiseTime : maxNoiseTime;
        if (time == 0) {
            // Показуємо повідомлення про відсутність даних
            String noDataMessage = getString(R.string.no_data_available);
            Toast.makeText(this, noDataMessage, Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Don't show dialog if activity is finishing
        if (isFinishing() || isChangingConfigurations()) {
            return;
        }
        
        String prefix = getString(isMin ? R.string.min_tooltip_prefix : R.string.max_tooltip_prefix);
            
        // Форматуємо час
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault());
        String timeStr = sdf.format(new java.util.Date(time));
        
        // Додаємо пробіл після двокрапки
        String fullText = prefix + " " + timeStr;
        
        // Визначаємо поточну тему напряму через системну конфігурацію
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkThemeActive = (nightMode == Configuration.UI_MODE_NIGHT_YES);
        
        Log.d(TAG, "showNoiseTimeTooltip: Показуємо стилізований діалог для " + (isMin ? "мін" : "макс") + 
            " шуму. Системна тема isDarkThemeActive = " + isDarkThemeActive);
        
        // Знаходимо координати блоку статистики для розміщення діалогу над ним
        View statsContainer = findViewById(R.id.statsContainer);
        
        // Створюємо кастомний діалог, що виглядає як снекбар
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(this, R.style.CustomSnackbarDialog);
        
        // Налаштовуємо діалог без заголовка
        // Використовуємо лише один рядок тексту, як у снекбарі
        builder.setMessage(fullText);
        builder.setCancelable(true);
        
        // Не додаємо кнопок, як у снекбарі
        
        // Отримуємо діалог
        final androidx.appcompat.app.AlertDialog dialog = builder.create();
        
        // Store dialog reference
        noiseTimeTooltipDialog = dialog;
        
        // Налаштовуємо window для повної імітації снекбару
        if (dialog.getWindow() != null) {
            // Прозорий фон для вікна діалогу
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
            
            // Встановлюємо положення центровано горизонтально
            dialog.getWindow().setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            
            // Ширина як wrap_content
            dialog.getWindow().setLayout(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            
            // Отримуємо позицію блоку статистики, щоб розмістити діалог над ним
            if (statsContainer != null) {
                int[] location = new int[2];
                statsContainer.getLocationInWindow(location);
                
                // Встановлюємо вертикальний відступ для розміщення над блоком статистики
                WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
                // Розміщуємо діалог значно вище блоку статистики (збільшуємо відступ)
                layoutParams.y = location[1] - (int)dpToPx(100);
                dialog.getWindow().setAttributes(layoutParams);
                
                Log.d(TAG, "showNoiseTimeTooltip: Розміщуємо діалог над блоком статистики на позиції Y: " + layoutParams.y);
            } else {
                // Якщо не можемо знайти блок статистики, розміщуємо в верхній частині екрану
                WindowManager.LayoutParams layoutParams = dialog.getWindow().getAttributes();
                layoutParams.y = (int)dpToPx(100); // Зменшуємо фіксовану позицію
                dialog.getWindow().setAttributes(layoutParams);
                
                Log.d(TAG, "showNoiseTimeTooltip: Не знайдено блок статистики, використовуємо фіксовану позицію");
            }
        }
        
        // Встановлюємо таймер для автоматичного закриття, як у снекбарі
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (dialog.isShowing()) {
                dialog.dismiss();
                noiseTimeTooltipDialog = null;
            }
        }, 3000); // 3 секунди, як у снекбарі
        
        // Налаштовуємо діалог після створення
        dialog.setOnShowListener(dialogInterface -> {
            // Отримуємо вид повідомлення
            TextView messageView = dialog.findViewById(android.R.id.message);
            if (messageView != null) {
                // Стилізуємо текст як у снекбарі
                messageView.setTextAlignment(View.TEXT_ALIGNMENT_CENTER);
                messageView.setTypeface(null, Typeface.BOLD);
                messageView.setSingleLine(true);
                messageView.setMaxWidth(Integer.MAX_VALUE);
                messageView.setPadding(32, 24, 32, 24);
                
                // Налаштовуємо колір тексту згідно теми
                if (isDarkThemeActive) {
                    // Темна тема - білий текст
                    messageView.setTextColor(Color.WHITE);
                    
                    // Створюємо фонове drawable з заокругленими кутами
                    GradientDrawable darkBackground = new GradientDrawable();
                    darkBackground.setColor(Color.parseColor("#191C1C"));
                    darkBackground.setCornerRadius(14 * getResources().getDisplayMetrics().density);
                    
                    // Додаємо тоненьке біле обведення для темної теми
                    darkBackground.setStroke((int)(1 * getResources().getDisplayMetrics().density), Color.parseColor("#50FFFFFF"));
                    
                    // Встановлюємо фон
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        messageView.setBackground(darkBackground);
                    } else {
                        messageView.setBackgroundDrawable(darkBackground);
                    }
                    
                    // Встановлюємо відповідну тінь
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        messageView.setElevation(6 * getResources().getDisplayMetrics().density);
                        
                        // Вимикаємо тінь спрямовану вниз за допомогою налаштування підсвітки
                        messageView.setOutlineProvider(null);
                    }
                    
                    Log.d(TAG, "showNoiseTimeTooltip: Встановлено ТЕМНИЙ фон для снекбар-діалогу в темній темі");
                } else {
                    // Світла тема - чорний текст
                    messageView.setTextColor(Color.BLACK);
                    
                    // Створюємо фонове drawable з заокругленими кутами
                    GradientDrawable lightBackground = new GradientDrawable();
                    lightBackground.setColor(Color.parseColor("#FAFDFC"));
                    lightBackground.setCornerRadius(14 * getResources().getDisplayMetrics().density);
                    
                    // Додаємо тоненьке темно-сіре обведення для світлої теми
                    lightBackground.setStroke((int)(1 * getResources().getDisplayMetrics().density), Color.parseColor("#1F333333"));
                    
                    // Встановлюємо фон
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                        messageView.setBackground(lightBackground);
                    } else {
                        messageView.setBackgroundDrawable(lightBackground);
                    }
                    
                    // Встановлюємо відповідну тінь
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        messageView.setElevation(6 * getResources().getDisplayMetrics().density);
                        
                        // Вимикаємо тінь спрямовану вниз за допомогою налаштування підсвітки
                        messageView.setOutlineProvider(null);
                    }
                    
                    Log.d(TAG, "showNoiseTimeTooltip: Встановлено СВІТЛИЙ фон для снекбар-діалогу в світлій темі");
                }
            }
        });
        
        // Set dismiss listener
        dialog.setOnDismissListener(dialogInterface -> {
            noiseTimeTooltipDialog = null;
        });
        
        // Показуємо діалог
        dialog.show();
        
        Log.d(TAG, getString(R.string.tooltip_shown_for, 
            isMin ? getString(R.string.minimum) : getString(R.string.maximum)));
    }

    private String getCurrentLanguage() {
        NoiseMonitorApplication app = (NoiseMonitorApplication) getApplication();
        return app.getStoredLanguage();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("is_recording", isRecording);
    }

    @Override
    protected void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        isRecording = savedInstanceState.getBoolean("is_recording", true);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Зберігаємо стан запису
        boolean wasRecording = isRecording;
        
        // Логування конфігурації
        int uiMode = newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        boolean isDarkModeNow = uiMode == Configuration.UI_MODE_NIGHT_YES;
        Log.d(TAG, "onConfigurationChanged: Змінено конфігурацію. Темна тема: " + isDarkModeNow);
        
        // Оновлюємо інтерфейс під поточну тему
        updateThemeUI();
        
        // Перевіряємо, чи змінилася системна мова
        String systemLanguage = Locale.getDefault().getLanguage();
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        boolean isLanguageSelected = prefs.getBoolean(Constants.PREF_LANGUAGE_SELECTED, false);
        
        // Якщо ми використовуємо системну мову і вона змінилася - оновлюємо
        if (!isLanguageSelected && !systemLanguage.equals(currentLanguage)) {
            NoiseMonitorApplication app = (NoiseMonitorApplication) getApplication();
            app.setLocale(systemLanguage);
            currentLanguage = systemLanguage;
            updateAllTexts();
            
            // Повідомляємо сервіс про зміну мови
            Intent langIntent = new Intent(this, NoiseService.class);
            langIntent.putExtra(Constants.PREF_LANGUAGE, currentLanguage);
            startService(langIntent);
        }
        
        // Відновлюємо стан запису без перезапуску сервісу
        if (wasRecording != isRecording) {
            updateRecordingState(wasRecording);
        }
    }
    
    /**
     * Перезапускає активність після зміни теми
        }
    }
    
    /**
     * Перевіряє, чи зараз встановлена темна тема
     * @return true якщо зараз темна тема, false якщо світла
     */
    private boolean isCurrentlyDarkTheme() {
        // Спочатку перевіряємо, який режим встановлено
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int themeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO);
        
        // Виводимо інформацію про поточні налаштування
        String themeModeStr;
        switch(themeMode) {
            case AppCompatDelegate.MODE_NIGHT_YES:
                themeModeStr = "MODE_NIGHT_YES";
                break;
            case AppCompatDelegate.MODE_NIGHT_NO:
                themeModeStr = "MODE_NIGHT_NO";
                break;
            case AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM:
                themeModeStr = "MODE_NIGHT_FOLLOW_SYSTEM";
                break;
            default:
                themeModeStr = "UNKNOWN (" + themeMode + ")";
        }
        Log.d(TAG, "isCurrentlyDarkTheme: Поточний режим теми: " + themeModeStr);
        
        // Для фіксованих режимів повертаємо відповідне значення
        if (themeMode == AppCompatDelegate.MODE_NIGHT_YES) {
            Log.d(TAG, "isCurrentlyDarkTheme: Встановлено фіксований темний режим");
            return true;
        } else if (themeMode == AppCompatDelegate.MODE_NIGHT_NO) {
            Log.d(TAG, "isCurrentlyDarkTheme: Встановлено фіксований світлий режим");
            return false;
        } else {
            // Для системного режиму перевіряємо поточну системну тему
            int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            boolean isDarkModeNow = nightMode == Configuration.UI_MODE_NIGHT_YES;
            Log.d(TAG, "isCurrentlyDarkTheme: Використовуємо системний режим, isDarkModeNow = " + isDarkModeNow);
            return isDarkModeNow;
        }
    }

    /**
     * Оновлює елементи UI відповідно до поточної теми
     */
    private void updateThemeUI() {
        // Перевіряємо, чи змінилась тема з моменту створення активності
        boolean currentThemeDark = isCurrentlyDarkTheme();
        
        // Виводимо лог для діагностики
        Log.d(TAG, "updateThemeUI: Поточна тема: " + (currentThemeDark ? "темна" : "світла"));
        
        // Оновлюємо isDarkTheme для відображення поточного стану
        isDarkTheme = currentThemeDark;
        
        // Створюємо фінальну копію змінної для використання в лямбда-виразі
        final boolean finalThemeDark = isDarkTheme;
        
        // Продовжуємо стандартне оновлення UI
        runOnUiThread(() -> {
            try {
                Log.d(TAG, "updateThemeUI: Оновлення спідометра для теми: " + (finalThemeDark ? "темна" : "світла"));
                
                // Оновлюємо ТІЛЬКИ спідометр, оскільки решта UI елементів керуються системною темою автоматично
                if (noiseSpeedometer != null) {
                    // Колір фону спідометра
                    int backgroundColor;
                    int textColor;
                    
                    if (finalThemeDark) {
                        // Для темної теми
                        backgroundColor = Color.parseColor("#121212");
                        textColor = Color.WHITE;
                    } else {
                        // Для світлої теми
                        backgroundColor = Color.parseColor("#E3F2FD");
                        textColor = Color.BLACK;
                    }
                    
                    // Застосовуємо кольори
                    noiseSpeedometer.setBackgroundCircleColor(backgroundColor);
                    noiseSpeedometer.setTextColor(textColor);
                    noiseSpeedometer.setSpeedTextColor(textColor);
                    noiseSpeedometer.setUnitTextColor(textColor);
                    
                    // Повторно застосовуємо підсвічування для поточного рівня шуму
                    if (noiseSpeedometer.getSpeed() > 0) {
                        updateSpeedometerTickHighlighting(noiseSpeedometer.getSpeed());
                    }
                    
                    Log.d(TAG, "updateThemeUI: Спідометр оновлено");
                }
                
                Log.d(TAG, "updateThemeUI: UI успішно оновлено");
                
            } catch (Exception e) {
                Log.e(TAG, "updateThemeUI: Помилка при оновленні елементів UI для теми", e);
            }
        });
    }
    
    /**
     * Встановлює та зберігає режим теми додатку
     * @param themeMode режим теми (AppCompatDelegate.MODE_NIGHT_*)
     */
    private void setAppTheme(int themeMode) {
        Log.d(TAG, "setAppTheme: Встановлення режиму теми: " + themeMode);
        
        // Перевіряємо, чи режим теми відрізняється від поточного
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int currentThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        
        if (currentThemeMode == themeMode) {
            Log.d(TAG, "setAppTheme: Режим теми вже встановлено, пропускаємо");
            return;
        }
        
        // Зберігаємо режим теми в налаштуваннях
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt("theme_mode", themeMode);
        editor.apply();
        
        // Застосовуємо режим теми
        AppCompatDelegate.setDefaultNightMode(themeMode);
        
        // Оновлюємо змінну isDarkTheme після зміни теми
        isDarkTheme = isCurrentlyDarkTheme();
    }
    
    /**
     * Встановлює режим теми без перестворення активності
     * Використовується в updateThemeUI() для запобігання циклічним викликам
     */
    private void setAppThemeWithoutRecreate(int themeMode) {
        Log.d(TAG, "setAppThemeWithoutRecreate: Встановлення режиму теми без перестворення: " + themeMode);
        
        // Перевіряємо, чи режим теми відрізняється від поточного
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        int currentThemeMode = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        
        if (currentThemeMode == themeMode) {
            Log.d(TAG, "setAppThemeWithoutRecreate: Режим теми вже встановлено, пропускаємо");
            return;
        }
        
        // Оновлюємо змінну isDarkTheme напряму
        isDarkTheme = (themeMode == AppCompatDelegate.MODE_NIGHT_YES);
        
        // Застосовуємо режим теми для наступного запуску
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        editor.putInt("theme_mode", themeMode);
        editor.apply();
        
        // Встановлюємо тему без виклику recreate()
        AppCompatDelegate.setDefaultNightMode(themeMode);
    }
    
    /**
     * Перемикає тему додатку між темною та світлою
     */
    private void toggleAppTheme() {
        boolean isCurrentDark = isCurrentlyDarkTheme();
        int newThemeMode = isCurrentDark ? 
            AppCompatDelegate.MODE_NIGHT_NO : 
            AppCompatDelegate.MODE_NIGHT_YES;
        
        Log.d(TAG, "toggleAppTheme: Перемикання з " + 
              (isCurrentDark ? "темної" : "світлої") + " на " + 
              (isCurrentDark ? "світлу" : "темну"));
        
        setAppTheme(newThemeMode);
    }
    
    /**
     * Встановлює режим слідування за системною темою
     */
    private void setFollowSystemTheme() {
        Log.d(TAG, "setFollowSystemTheme: Встановлення режиму слідування за системною темою");
        setAppTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
    }

    private void updateRecordingState(boolean isRecording) {
        Log.d(TAG, "updateRecordingState: Оновлення стану запису на " + isRecording);
        runOnUiThread(() -> {
            try {
                // Оновлюємо локальний стан
                this.isRecording = isRecording;
                
                // Оновлюємо кнопку паузи/відновлення
                updatePauseButton();
                
                // Оновлюємо відображення спідометра
                if (noiseSpeedometer != null) {
                    // Тремтіння активне тільки при записі
                    noiseSpeedometer.setWithTremble(isRecording);
                }
            } catch (Exception e) {
                Log.e(TAG, "updateRecordingState: Помилка при оновленні стану запису", e);
            }
        });
    }

    private void updateBackgroundServiceState(boolean isEnabled) {
        try {
            // Оновлюємо стан фонового моніторингу через менеджер
            NoiseServiceManager.getInstance(this).updateBackgroundState(isEnabled);
            
            // Оновлюємо локальний стан
            isBackgroundServiceEnabled = isEnabled;
            
            // Оновлюємо UI
            updateBackgroundStatus(isEnabled);
            
            Log.d(TAG, "updateBackgroundServiceState: Стан фонового моніторингу оновлено: " + isEnabled);
        } catch (Exception e) {
            Log.e(TAG, "updateBackgroundServiceState: Помилка при оновленні стану фонового моніторингу", e);
        }
    }

    private void setupBackgroundServiceSettings(SharedPreferences prefs) {
        // Встановлюємо початковий стан
        boolean isBackgroundServiceEnabled = prefs.getBoolean(PREF_BACKGROUND_SERVICE, true);
        
        // Оновлюємо UI відповідно до стану
        if (!isBackgroundServiceEnabled) {
            updateStatus(getString(R.string.service_paused));
            updateNoiseLevel(0.0);
            if (buttonPause != null) {
                buttonPause.setText(getString(R.string.button_resume));
            }
        }
    }

    // Показ інформації про референсні значення шуму
    private void showNoiseReferenceInfo() {
        Log.d(TAG, "showNoiseReferenceInfo: Відображення референсних значень шуму");
        
        // Don't show dialog if activity is finishing
        if (isFinishing() || isChangingConfigurations()) {
            return;
        }
        
        // Встановлюємо прапорець, щоб знати, коли діалог видно
        final boolean[] isDialogShowing = {true};
        
        // Визначаємо поточну тему
        int nightMode = getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
        final boolean isDarkThemeActive = (nightMode == Configuration.UI_MODE_NIGHT_YES);
        
        // Надуваємо кастомний layout
        View customView = getLayoutInflater().inflate(R.layout.reference_values_snackbar, null);
        
        // Отримуємо референси на елементи у кастомному layout
        TextView titleTextView = customView.findViewById(R.id.referenceTitle);
        View closeButtonBottom = customView.findViewById(R.id.closeButtonBottom);
        
        // Отримуємо референси на всі рівні шуму
        final TextView[] levelTextViews = new TextView[7];
        levelTextViews[0] = customView.findViewById(R.id.referenceLevel1);
        levelTextViews[1] = customView.findViewById(R.id.referenceLevel2);
        levelTextViews[2] = customView.findViewById(R.id.referenceLevel3);
        levelTextViews[3] = customView.findViewById(R.id.referenceLevel4);
        levelTextViews[4] = customView.findViewById(R.id.referenceLevel5);
        levelTextViews[5] = customView.findViewById(R.id.referenceLevel6);
        levelTextViews[6] = customView.findViewById(R.id.referenceLevel7);
        
        // Встановлюємо тексти
        titleTextView.setText(R.string.noise_reference_title);
        
        // Встановлюємо фон
        GradientDrawable background = new GradientDrawable();
        background.setCornerRadius(14 * getResources().getDisplayMetrics().density);
        
        // Налаштування кольорів в залежності від теми
        if (isDarkThemeActive) {
            background.setColor(Color.parseColor("#191C1C"));
            background.setStroke((int)(1 * getResources().getDisplayMetrics().density), Color.parseColor("#50FFFFFF"));
            titleTextView.setTextColor(Color.WHITE);
            for (TextView tv : levelTextViews) {
                tv.setTextColor(Color.WHITE);
            }
        } else {
            background.setColor(Color.parseColor("#FAFDFC"));
            background.setStroke((int)(1 * getResources().getDisplayMetrics().density), Color.parseColor("#1F333333"));
            titleTextView.setTextColor(Color.BLACK);
            for (TextView tv : levelTextViews) {
                tv.setTextColor(Color.BLACK);
            }
        }
        
        // Встановлюємо фон для діалогу
        customView.setBackground(background);
        
        // Створюємо діалог
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setView(customView);
        final AlertDialog dialog = builder.create();
        
        // Store dialog reference
        noiseReferenceDialog = dialog;
        
        // Функція для оновлення виділення на основі поточного рівня шуму
        final Handler uiHandler = new Handler(Looper.getMainLooper());
        final Runnable updateHighlighting = new Runnable() {
            @Override
            public void run() {
                if (!isDialogShowing[0] || dialog == null || !dialog.isShowing()) {
                    return;
                }
                
                // Отримуємо поточний рівень шуму
                float currentNoiseLevel = noiseSpeedometer != null ? noiseSpeedometer.getSpeed() : 0;
                Log.d(TAG, "updateHighlighting: Поточний рівень шуму: " + currentNoiseLevel + " дБ");
                
                // Скидаємо форматування для всіх TextView
                for (TextView tv : levelTextViews) {
                    tv.setTypeface(null, Typeface.NORMAL);
                    tv.setTextColor(isDarkThemeActive ? Color.WHITE : Color.BLACK);
                }
                
                // Визначаємо, який рівень шуму підсвітити
                int levelIndex = -1;
                if (currentNoiseLevel >= 0 && currentNoiseLevel < 20) {
                    levelIndex = 0; // 0-10
                } else if (currentNoiseLevel >= 20 && currentNoiseLevel < 40) {
                    levelIndex = 1; // 20-30
                } else if (currentNoiseLevel >= 40 && currentNoiseLevel < 60) {
                    levelIndex = 2; // 40-50
                } else if (currentNoiseLevel >= 60 && currentNoiseLevel < 80) {
                    levelIndex = 3; // 60-70
                } else if (currentNoiseLevel >= 80 && currentNoiseLevel < 100) {
                    levelIndex = 4; // 80-90
                } else if (currentNoiseLevel >= 100 && currentNoiseLevel < 120) {
                    levelIndex = 5; // 100-110
                } else if (currentNoiseLevel >= 120) {
                    levelIndex = 6; // 120+
                }
                
                // Підсвічуємо поточний рівень
                if (levelIndex >= 0) {
                    levelTextViews[levelIndex].setTypeface(null, Typeface.BOLD);
                    levelTextViews[levelIndex].setTextColor(Color.parseColor("#2196F3")); // Використовуємо голубий колір для всіх підсвіток
                }
                
                // Плануємо наступне оновлення
                if (isDialogShowing[0]) {
                    uiHandler.postDelayed(this, 200); // Оновлюємо кожні 200мс
                }
            }
        };
        
        // Закриття діалогу при натисканні на кнопку
        closeButtonBottom.setOnClickListener(v -> {
            isDialogShowing[0] = false;
            dialog.dismiss();
            noiseReferenceDialog = null;
        });
        
        // Встановлюємо слухач для закриття діалогу
        dialog.setOnDismissListener(dialogInterface -> {
            isDialogShowing[0] = false;
            noiseReferenceDialog = null;
        });
        
        // Показуємо діалог знизу екрану
        if (dialog.getWindow() != null) {
            // Прозорий фон для діалогу
            dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            
            // Розташування внизу екрану
            WindowManager.LayoutParams params = dialog.getWindow().getAttributes();
            params.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            
            // Відступи для діалогу
            int margin = (int) (16 * getResources().getDisplayMetrics().density);
            dialog.getWindow().getDecorView().setPadding(margin, margin, margin, margin);
            
            dialog.getWindow().setAttributes(params);
        }
        
        // Показуємо діалог
        dialog.show();
        
        // Запускаємо перше оновлення підсвічування
        updateHighlighting.run();
    }

    // Перевірка на валідний діапазон шуму і обробка граничних значень
    private void checkValidNoiseRange() {
        // Перевіряємо чи minNoiseLevel має значення за замовчуванням (Double.MAX_VALUE)
        // або має нереалістичне значення (> 200)
        if (minNoiseLevel == Double.MAX_VALUE || minNoiseLevel > 200) {
            Log.d(TAG, "checkValidNoiseRange: Виявлено нереалістичне значення minNoiseLevel: " + minNoiseLevel + ", встановлюємо 0");
            minNoiseLevel = 0;
        }
        
        // Перевіряємо чи maxNoiseLevel має значення за замовчуванням (Double.MIN_VALUE)
        // або має нереалістичне значення (< 0)
        if (maxNoiseLevel == Double.MIN_VALUE || maxNoiseLevel < 0) {
            Log.d(TAG, "checkValidNoiseRange: Виявлено нереалістичне значення maxNoiseLevel: " + maxNoiseLevel + ", встановлюємо 0");
            maxNoiseLevel = 0;
        }
    }

    private void setupSettingsButton() {
        // додаткова кнопка була видалена
    }

    /**
     * Налаштовує елементи інтерфейсу
     */
    private void setupUI() {
        try {
            androidx.appcompat.widget.Toolbar toolbar = findViewById(R.id.topAppBar);
            setSupportActionBar(toolbar);
    
            noiseLevelText = findViewById(R.id.noiseLevelText);
            statusText = findViewById(R.id.statusText);
            statusTitleText = findViewById(R.id.statusTitleText);
            noiseLevelTitleText = findViewById(R.id.noiseLevelTitleText);
            minNoiseText = findViewById(R.id.minNoiseText);
            avgNoiseText = findViewById(R.id.avgNoiseText);
            maxNoiseText = findViewById(R.id.maxNoiseText);
            minInfoIcon = findViewById(R.id.minInfoIcon);
            maxInfoIcon = findViewById(R.id.maxInfoIcon);
            buttonPause = findViewById(R.id.buttonPause);
            noiseSpeedometer = findViewById(R.id.noiseSpeedometer);
            speedometerInfoIcon = findViewById(R.id.speedometerInfoIcon);
            backgroundStatusText = findViewById(R.id.backgroundStatusText);
            
            setupNoiseSpeedometer();
            // Застосовуємо поточну тему до елементів UI
            updateThemeUI();
            
            loadNoiseStats();
            
            // Налаштування хіктів для іконок інформації
            minInfoIcon.setOnClickListener(v -> showNoiseTimeTooltip(true));
            maxInfoIcon.setOnClickListener(v -> showNoiseTimeTooltip(false));
            speedometerInfoIcon.setOnClickListener(v -> showNoiseReferenceInfo());
            
            buttonPause.setOnClickListener(v -> toggleRecording());
            
            noiseLevelText.setText(getString(R.string.noise_level_placeholder));
            
            // Оновлюємо UI відповідно до стану
            updatePauseButton();
            updateStatus(isRecording ? getString(R.string.service_starting) : getString(R.string.service_paused));
            if (!isRecording) {
                updateNoiseLevel(0.0);
            }
            
            resetStatsButton = findViewById(R.id.resetStatsButton);
            resetStatsButton.setOnClickListener(v -> resetStats());
            
        } catch (Exception e) {
            String errorMsg = getString(R.string.error_processing, e.getMessage());
            Log.e(TAG, errorMsg, e);
            showError(errorMsg);
        }
    }

    /**
     * Перевіряє, чи надані всі необхідні дозволи
     * @return true якщо надані всі дозволи, false в іншому випадку
     */
    private boolean hasRequiredPermissions() {
        return checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED &&
               checkSelfPermission(android.Manifest.permission.FOREGROUND_SERVICE) == android.content.pm.PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Dismiss all dialogs to prevent window leaks
     */
    private void dismissAllDialogs() {
        // Dismiss battery optimization dialog if showing
        if (batteryOptimizationDialog != null && batteryOptimizationDialog.isShowing()) {
            batteryOptimizationDialog.dismiss();
            batteryOptimizationDialog = null;
        }
        
        // Dismiss noise reference dialog if showing
        if (noiseReferenceDialog != null && noiseReferenceDialog.isShowing()) {
            noiseReferenceDialog.dismiss();
            noiseReferenceDialog = null;
        }
        
        // Dismiss noise time tooltip dialog if showing
        if (noiseTimeTooltipDialog != null && noiseTimeTooltipDialog.isShowing()) {
            noiseTimeTooltipDialog.dismiss();
            noiseTimeTooltipDialog = null;
        }
    }

    /**
     * Refreshes the UI without recreating the activity
     */
    private void refreshUI() {
        Log.d(TAG, "refreshUI: Оновлення інтерфейсу без перезапуску активності");
        // Update UI components based on current theme
        updateThemeUI();
        // Update all text labels
        updateAllTexts();
    }
} 