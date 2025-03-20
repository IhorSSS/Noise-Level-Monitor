package com.app.noiselevelmonitor;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.content.SharedPreferences;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.io.File;
import java.util.Locale;
import android.content.res.Configuration;
import android.Manifest;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class NoiseService extends Service {
    private static final String TAG = "NoiseService";
    
    // Константи для WakeLock
    private static final String WAKELOCK_TAG = "NoiseService:WakeLock";
    
    // Змінні для MediaRecorder
    private MediaRecorder mediaRecorder;
    private Handler handler;
    private Runnable updateNoise;
    
    // Змінні стану
    private boolean isRecording = false;
    private boolean isSingleMeasurement = false;
    private boolean isPaused = false;
    private boolean isServiceRunning = true;
    private boolean isBackgroundServiceEnabled = true;
    private boolean isAppForeground = true;
    private int errorCount = 0;
    private boolean isTimerRunning = false;
    
    // Змінні для вимірювання
    private double[] recentAmplitudes = new double[Constants.SAMPLE_WINDOW];
    private int amplitudeIndex = 0;
    private double minNoiseLevel = 0;
    private double maxNoiseLevel = 0;
    private double totalNoiseLevel = 0;
    private int noiseMeasurements = 0;
    private long minNoiseTime = 0;
    private long maxNoiseTime = 0;
    private long lastNotificationUpdate = 0;
    private double lastNoiseLevel = 0;
    
    // Змінні для вимірювання стабільності
    private int stableReadingCount = 0;
    private double lastStableReading = 0;
    private boolean isStableMeasurementMode = false;
    
    // Системні змінні
    private NotificationManager notificationManager;
    private PowerManager.WakeLock wakeLock;

    // Змінні для налаштувань калібрування
    private long updateFrequency = Constants.NOISE_UPDATE_INTERVAL;
    private double sensitivityOffset = 0.0;
    private double smoothingFactor = 0.3;
    private double minNoiseDetectionLevel = Constants.MIN_VALID_DB;

    // Додаємо прапорець для відстеження стану оновлення
    private boolean isNoiseUpdating = false;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate: Сервіс створюється");
        
        try {
            // Оновлюємо мову сервісу
            NoiseMonitorApplication app = (NoiseMonitorApplication) getApplication();
            String languageCode = app.getStoredLanguage();
            updateServiceLocale(languageCode);
            
            // Ініціалізуємо базові змінні
            isServiceRunning = false; // Initially not in foreground mode
            
            // Завантажуємо налаштування фонового сервісу
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
            isBackgroundServiceEnabled = prefs.getBoolean(Constants.PREF_BACKGROUND_SERVICE, true);
            
            // Завантажуємо налаштування калібрування при створенні сервісу
            loadCalibrationSettings(prefs);
            
            // Встановлюємо нотифікацію
            createNotificationChannel();
            // Використовуємо сумісний метод отримання NotificationManager
            notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            
            // Завантажуємо статистику
            loadNoiseStats();
        } catch (Exception e) {
            Log.e(TAG, "onCreate: Помилка при створенні сервісу", e);
            isServiceRunning = false;
        }
    }

    @Override
    protected void attachBaseContext(Context base) {
        try {
            // Отримуємо мову з додатку
            NoiseMonitorApplication app = (NoiseMonitorApplication) base.getApplicationContext();
            String languageCode = app.getStoredLanguage();
            
            // Створюємо локаль та контекст
            Locale locale = new Locale(languageCode);
            Configuration config = new Configuration();
            config.setLocale(locale);
            
            // Створюємо контекст
            Context context = base.createConfigurationContext(config);
            super.attachBaseContext(context);
        } catch (Exception e) {
            super.attachBaseContext(base);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Отримуємо збережену мову і оновлюємо її
        NoiseMonitorApplication app = (NoiseMonitorApplication) getApplication();
        updateServiceLocale(app.getStoredLanguage());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand: Сервіс отримав команду");
        
        String action = intent != null ? intent.getAction() : null;
        Log.d(TAG, "onStartCommand: Отримано intent з дією " + action);
        
        // Перевіряємо, чи був перезапуск сервісу системою
        boolean isSystemRestart = (flags & START_FLAG_REDELIVERY) != 0;
        
        // Якщо це просто перезапуск системою, продовжуємо роботу з поточним станом
        if (isSystemRestart) {
            Log.d(TAG, "onStartCommand: Перезапуск сервісу системою, продовжуємо з поточним станом");
            updateNotification();
            return START_REDELIVER_INTENT;
        }
        
        if (intent == null) {
            Log.d(TAG, "onStartCommand: Intent є null, запускаємо з поточним станом");
            updateNotification();
            return START_STICKY;
        }
        
        // Отримуємо дані з intent
        boolean wasFullyShutdown = intent.getBooleanExtra(Constants.EXTRA_WAS_FULLY_SHUTDOWN, false);
        
        // Обробка дій
        if (action == null) {
            // Це запуск сервісу - встановлюємо початковий стан тільки якщо це повний запуск
            if (wasFullyShutdown) {
                // Встановлюємо мову
                String language = intent.getStringExtra(Constants.PREF_LANGUAGE);
                if (language != null) {
                    setLanguage(language);
                }
                
                // Перевіряємо, чи це перший запуск
                boolean isFirstLaunch = intent.getBooleanExtra("isFirstLaunch", false);
                
                // Отримуємо стан запису
                isRecording = intent.getBooleanExtra("isRecording", true);
                
                // При першому запуску або взагалі без явного значення - гарантуємо, що запис активний
                if (isFirstLaunch || !intent.hasExtra("isRecording")) {
                    isRecording = true;
                    isPaused = false;
                    Log.d(TAG, "onStartCommand: Перший запуск або не вказаний стан запису, гарантуємо активний запис");
                }
                
                // Оновлюємо змінні стану відповідно до isRecording
                isPaused = !isRecording;
                
                // Отримуємо стан фонового моніторингу
                isBackgroundServiceEnabled = intent.getBooleanExtra(Constants.PREF_BACKGROUND_SERVICE, true);
                
                Log.d(TAG, "onStartCommand: Повний запуск сервісу з параметрами: " +
                      "isFirstLaunch=" + isFirstLaunch + ", isRecording=" + isRecording + ", isPaused=" + isPaused);
                
                // Скидаємо стан повної зупинки
                wasFullyShutdown = false;
                
                // Ініціалізуємо медіа рекордер, якщо запис має бути активним
                if (isRecording && mediaRecorder == null) {
                    Log.d(TAG, "onStartCommand: Ініціалізуємо та запускаємо запис за статусом isRecording=true");
                    initializeMediaRecorder();
                    startNoiseUpdates();
                } else {
                    Log.d(TAG, "onStartCommand: Запис неактивний (isRecording=" + isRecording + "), не запускаємо");
                }
            } else {
                Log.d(TAG, "onStartCommand: Запуск сервісу без повної зупинки, зберігаємо поточний стан");
            }
        } else {
            // Обробка конкретних дій
                switch (action) {
                    case Constants.ACTION_TOGGLE_RECORDING:
                    // Перемикаємо стан запису
                    toggleRecording();
                    
                    // Оновлюємо нотифікацію
                    updateNotification();
                    break;
                    
                case Constants.ACTION_STOP_SERVICE:
                    Log.d(TAG, "onStartCommand: Отримано команду зупинки сервісу");
                    stopForeground(true);
                    stopSelf();
                    return START_NOT_STICKY;
                    
                case Constants.ACTION_APP_TO_FOREGROUND:
                    Log.d(TAG, "onStartCommand: Додаток перейшов на передній план");
                    isAppForeground = true;
                    
                    // Оновлюємо нотифікацію
                    updateNotification();
                    
                    // Відправляємо поточний стан
                    sendCurrentStateBroadcast();
                        break;
                    
                case Constants.ACTION_APP_TO_BACKGROUND:
                    Log.d(TAG, "onStartCommand: Додаток перейшов у фон");
                    isAppForeground = false;
                    updateNotification();
                    break;
                    
                    case Constants.ACTION_RESET_STATS:
                    Log.d(TAG, "onStartCommand: Скидання статистики");
                        resetStats();
                        break;
                    
                case Constants.ACTION_UPDATE_BACKGROUND_STATE:
                    isBackgroundServiceEnabled = intent.getBooleanExtra(Constants.PREF_BACKGROUND_SERVICE, true);
                    Log.d(TAG, "onStartCommand: Оновлено стан фонового моніторингу: " + isBackgroundServiceEnabled);
                        break;
                    
                case Constants.ACTION_MEASURE_ONCE:
                    Log.d(TAG, "onStartCommand: Отримано команду одноразового вимірювання");
                    performMeasurement();
                        break;
                    
                default:
                    Log.d(TAG, "onStartCommand: Невідома дія: " + action);
                        break;
                }
            }
            
        // Налаштовуємо для показу в шторці повідомлень
        updateNotification();
        
        // Якщо запис активний, запускаємо таймер вимірювання
        if (isRecording && !isTimerRunning) {
            startMeasurementTimer();
        }
        
        return START_STICKY;
    }

    // Оновлює локаль сервісу без перезапуску запису
    private void updateServiceLocale(String languageCode) {
        try {
            // Встановлюємо локаль
            Locale locale = new Locale(languageCode);
            Locale.setDefault(locale);
            
            // Створюємо конфігурацію
            Configuration config = new Configuration();
            config.setLocale(locale);

            // Оновлюємо конфігурацію
            getResources().updateConfiguration(config, getResources().getDisplayMetrics());
            Log.d(TAG, "updateServiceLocale: Мова сервісу оновлена на " + languageCode);
        } catch (Exception e) {
            Log.e(TAG, "updateServiceLocale: Помилка при оновленні мови сервісу", e);
        }
    }

    private void setLanguage(String language) {
        try {
            updateServiceLocale(language);
            Log.d(TAG, "setLanguage: Мова сервісу встановлена на " + language);
        } catch (Exception e) {
            Log.e(TAG, "setLanguage: Помилка при встановленні мови сервісу", e);
        }
    }

    // Ініціалізація MediaRecorder
    private void initializeMediaRecorder() {
        try {
            releaseMediaRecorder(); // Звільняємо попередній екземпляр, якщо він існує
            
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
            
            // Створюємо тимчасовий файл для запису
            File outputFile = new File(getCacheDir(), "temp_audio.3gp");
            mediaRecorder.setOutputFile(outputFile.getAbsolutePath());
            
            mediaRecorder.prepare();
            mediaRecorder.start();
            
            Log.d(TAG, "initializeMediaRecorder: MediaRecorder успішно ініціалізований");
        } catch (Exception e) {
            Log.e(TAG, "initializeMediaRecorder: Помилка при ініціалізації MediaRecorder", e);
            releaseMediaRecorder();
        }
    }

    // Звільнення ресурсів MediaRecorder
    private void releaseMediaRecorder() {
        try {
            if (mediaRecorder != null) {
                if (isRecording) {
                    try {
                        mediaRecorder.stop();
                    } catch (RuntimeException ignored) {
                        // Ігноруємо помилки зупинки
                    }
                }
                
                try {
                    mediaRecorder.reset();
                    mediaRecorder.release();
                } catch (Exception ignored) {
                    // Ігноруємо помилки вивільнення
                }
                
                mediaRecorder = null;
            }
            
            // Прибираємо зміну стану isRecording - це повинен робити тільки метод toggleRecording
            // isRecording = false;
            
        } catch (Exception e) {
            Log.e(TAG, "releaseMediaRecorder: Загальна помилка", e);
            mediaRecorder = null;
        }
    }

    // Оновлення стану запису
    private void startNoiseUpdates() {
        try {
            Log.d(TAG, "startNoiseUpdates: Починаємо оновлення шуму");
            
            // Ініціалізуємо запис, якщо необхідно
            if (mediaRecorder == null) {
                initializeMediaRecorder();
            }
            
            // Встановлюємо стан запису в активний
            isRecording = true;
            isPaused = false;
            
            // Зберігаємо стан
            SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit();
            editor.putBoolean(Constants.PREF_IS_PAUSED, isPaused);
            editor.apply();
            
            // Створюємо Handler для UI потоку
            if (handler == null) {
                handler = new Handler(Looper.getMainLooper());
            }
            
            // Очищуємо будь-який існуючий Runnable
            if (updateNoise != null) {
                handler.removeCallbacks(updateNoise);
            }
            
            // Перевіряємо, чи медіа рекордер готовий
            if (mediaRecorder == null) {
                Log.e(TAG, "startNoiseUpdates: MediaRecorder не ініціалізовано");
                return;
            }
            
            // Створюємо нове завдання для оновлення шуму
            updateNoise = new Runnable() {
                @Override
                public void run() {
                    // Перевіряємо, чи сервіс все ще працює
                    if (!isServiceRunning) {
                        Log.d(TAG, "startNoiseUpdates: Сервіс не активний, зупиняємо оновлення");
                        return;
                    }
                    
                    // Перевіряємо, чи запис активний
                    if (!isRecording) {
                        Log.d(TAG, "startNoiseUpdates: Запис не активний, зупиняємо оновлення");
                        return;
                    }
                    
                    try {
                        // Отримуємо поточний рівень шуму
                        double noiseLevel = getNoiseLevelFromRecorder();
                        
                        // Оновлюємо статистику шуму
                        updateNoiseStats(noiseLevel);
                        
                        // Надсилаємо broadcast з оновленням
                        broadcastUpdate(noiseLevel, null, true);
                        
                        // Оновлюємо нотифікацію, якщо необхідно
                        if (System.currentTimeMillis() - lastNotificationUpdate > Constants.NOTIFICATION_UPDATE_INTERVAL) {
                            createOrUpdateNotification(noiseLevel);
                            lastNotificationUpdate = System.currentTimeMillis();
                        }
                        
                        // Зберігаємо останній вимір
                        lastNoiseLevel = noiseLevel;
                        
                        // Плануємо наступне оновлення
                        handler.postDelayed(this, updateFrequency);
                    } catch (Exception e) {
                        Log.e(TAG, "Помилка при оновленні шуму: " + e.getMessage(), e);
                        errorCount++;
                        
                        // Якщо забагато помилок, зупиняємо запис
                        if (errorCount > Constants.MAX_ERRORS) {
                            Log.e(TAG, "startNoiseUpdates: Досягнуто максимальну кількість помилок, зупиняємо запис");
                            stopNoiseUpdates();
                            handleError(getString(R.string.error_general) + ": " + e.getMessage());
                        } else {
                            // Плануємо наступну спробу
                            handler.postDelayed(this, updateFrequency);
                        }
                    }
                }
            };
            
            // Запускаємо оновлення шуму
            isNoiseUpdating = true;
            handler.post(updateNoise);
            
            // Відправляємо broadcast з повідомленням про початок запису
            broadcastRecordingState(true);
            broadcastStatus(getString(R.string.service_running));
            
            // Оновлюємо нотифікацію
            updateNotification();
            
            Log.d(TAG, "startNoiseUpdates: Запуск оновлень шуму успішний");
        } catch (Exception e) {
            Log.e(TAG, "startNoiseUpdates: Помилка при запуску оновлень шуму", e);
            isNoiseUpdating = false;
            if (mediaRecorder != null) {
                releaseMediaRecorder();
            }
        }
    }

    // Зупинка оновлень рівня шуму
    private void stopNoiseUpdates() {
        try {
            Log.d(TAG, "stopNoiseUpdates: Зупиняємо оновлення шуму");
            
            // Якщо вже зупинено, не робимо нічого
            if (mediaRecorder == null && !isNoiseUpdating) {
                Log.d(TAG, "stopNoiseUpdates: Оновлення вже зупинені");
                return;
            }
            
            // Зберігаємо останній рівень шуму перед зупинкою
            saveNoiseStats();
            
            // Скидаємо стан оновлення
            isNoiseUpdating = false;
            
            // Видаляємо обробники оновлень
            if (handler != null && updateNoise != null) {
                handler.removeCallbacks(updateNoise);
            }
            
            // Звільняємо WakeLock
            releaseWakeLock();
            
            // Звільняємо медіа рекордер
            releaseMediaRecorder();
            
            Log.d(TAG, "stopNoiseUpdates: Оновлення зупинені, стан: isPaused=" + isPaused + ", isRecording=" + isRecording);
            
            // Оновлюємо нотифікацію залежно від налаштувань фонового режиму
            if (isBackgroundServiceEnabled) {
                // Оновлюємо нотифікацію, щоб показати стан паузи
                lastNotificationUpdate = 0; // Скидаємо час останнього оновлення, щоб примусово оновити
                createOrUpdateNotification(lastNoiseLevel);
            } else {
                // Видаляємо нотифікацію повністю
                stopForeground(true);
                notificationManager.cancel(Constants.NOTIFICATION_ID);
            }
            
            // Відправляємо актуальний стан до MainActivity
            sendCurrentStateBroadcast();
            
        } catch (Exception e) {
            Log.e(TAG, "stopNoiseUpdates: Помилка", e);
        }
    }

    // Створення або оновлення нотифікації
    private void createOrUpdateNotification(double noiseLevel) {
        try {
            createNotificationChannel();
            
            // Створюємо інтент для відкриття активності при натисканні на нотифікацію
            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            );
            
            // Визначаємо статус запису
            String contentText = isRecording ? 
                getString(R.string.service_running) : 
                getString(R.string.service_paused);
            
            // Створюємо нотифікацію
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_stat_kisspng_computer_icons)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
            
            Notification notification = builder.build();
            
            // Запускаємо сервіс у режимі foreground з нотифікацією
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(Constants.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                } else {
                    startForeground(Constants.NOTIFICATION_ID, notification);
                }
                
                Log.d(TAG, "createOrUpdateNotification: Нотифікацію оновлено, статус: " + contentText);
                isServiceRunning = true;
            } catch (Exception e) {
                Log.e(TAG, "createOrUpdateNotification: Помилка при запуску foreground сервісу", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "createOrUpdateNotification: Помилка при оновленні нотифікації", e);
        }
    }

    // Обробка помилок
    private void handleError(String errorMessage) {
        errorCount++;
        Log.e(TAG, "handleError: " + errorMessage);
        broadcastStatus(errorMessage);
        
        if (errorCount >= Constants.MAX_ERRORS) {
            createOrUpdateNotification(0);
        } else {
            if (handler != null) {
                handler.postDelayed(this::initializeMediaRecorder, 1000);
            }
        }
    }

    // Створення каналу нотифікацій
    private void createNotificationChannel() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationChannel channel = new NotificationChannel(
                    Constants.CHANNEL_ID,
                    getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_LOW
                );
                channel.setDescription(getString(R.string.service_running));
                channel.setSound(null, null);
                channel.enableVibration(false);
                
                NotificationManager notificationManager = getSystemService(NotificationManager.class);
                    notificationManager.createNotificationChannel(channel);
                Log.d(TAG, "createNotificationChannel: Канал нотифікацій створено");
            }
        } catch (Exception e) {
            Log.e(TAG, "createNotificationChannel: Помилка при створенні каналу нотифікацій", e);
        }
    }

    // Вимірювання рівня шуму
    private double getNoiseLevelFromRecorder() {
        try {
            if (mediaRecorder == null) {
                return minNoiseDetectionLevel;
            }
            
            double amplitude = (double) mediaRecorder.getMaxAmplitude();
            
            // Ігноруємо невалідні значення
            if (amplitude <= 0) {
                return lastNoiseLevel > 0 ? lastNoiseLevel : minNoiseDetectionLevel;
            }
            
            // Зберігаємо для усереднення
            recentAmplitudes[amplitudeIndex] = amplitude;
            amplitudeIndex = (amplitudeIndex + 1) % Constants.SAMPLE_WINDOW;
            
            // Розраховуємо середню амплітуду
            double averageAmplitude = 0;
            int validSamples = 0;
            for (double amp : recentAmplitudes) {
                if (amp > 0) {
                    averageAmplitude += amp;
                    validSamples++;
                }
            }
            
            if (validSamples == 0) {
                return lastNoiseLevel > 0 ? lastNoiseLevel : minNoiseDetectionLevel;
            }
            
            averageAmplitude /= validSamples;
            
            // Конвертація в дБ з урахуванням налаштувань калібрування
            double db = 20 * Math.log10(averageAmplitude / Constants.REFERENCE_AMPLITUDE) + Constants.CALIBRATION_OFFSET + sensitivityOffset;
            
            // Обмеження діапазону з урахуванням користувацького мінімального рівня шуму
            db = Math.max(minNoiseDetectionLevel, Math.min(Constants.MAX_DB, db));
            
            // Згладжування з користувацьким коефіцієнтом
            if (lastNoiseLevel > 0 && smoothingFactor > 0) {
                db = (1 - smoothingFactor) * db + smoothingFactor * lastNoiseLevel;
            }
            
            // Зберігаємо виміряне значення як останнє
            if (db > 0) {
                lastNoiseLevel = db;
                // Періодично зберігаємо значення в SharedPreferences
                if (System.currentTimeMillis() % 10 == 0) { // Зберігаємо приблизно кожне 10-е вимірювання
                    saveNoiseStats();
                }
            }
            
            return db;
        } catch (Exception e) {
            return lastNoiseLevel > 0 ? lastNoiseLevel : minNoiseDetectionLevel;
        }
    }

    // Оновлення статистики шуму
    private void updateNoiseStats(double db) {
        // Оновлення статистики рівня шуму
        if (db < Constants.MIN_VALID_STAT_DB) {
            return;
        }
        
        // Оновлюємо Min/Max
        if (minNoiseLevel == 0.0 || db < minNoiseLevel) {
            minNoiseLevel = db;
            minNoiseTime = System.currentTimeMillis();
        }
        
        if (db > maxNoiseLevel) {
            maxNoiseLevel = db;
            maxNoiseTime = System.currentTimeMillis();
        }
        
        // Оновлюємо середнє
                    totalNoiseLevel += db;
                    noiseMeasurements++;
        
        // Зберігаємо статистику періодично
        if (noiseMeasurements % 100 == 0) {
        saveNoiseStats();
        }
    }

    // Завантаження статистики
    private void loadNoiseStats() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        minNoiseLevel = prefs.getFloat(Constants.PREF_MIN_NOISE, 0);
        maxNoiseLevel = prefs.getFloat(Constants.PREF_MAX_NOISE, 0);
        totalNoiseLevel = prefs.getFloat(Constants.PREF_TOTAL_NOISE, 0);
        noiseMeasurements = prefs.getInt(Constants.PREF_MEASUREMENTS, 0);
        minNoiseTime = prefs.getLong(Constants.PREF_MIN_NOISE_TIME, 0);
        maxNoiseTime = prefs.getLong(Constants.PREF_MAX_NOISE_TIME, 0);
        // Завантажуємо останній виміряний рівень шуму
        lastNoiseLevel = prefs.getFloat(Constants.PREF_LAST_NOISE_LEVEL, 0);
        Log.d(TAG, "loadNoiseStats: Завантажено останній рівень шуму: " + lastNoiseLevel + " dB");
    }

    // Збереження статистики
    private void saveNoiseStats() {
        SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit();
        editor.putFloat(Constants.PREF_MIN_NOISE, (float) minNoiseLevel);
        editor.putFloat(Constants.PREF_MAX_NOISE, (float) maxNoiseLevel);
        editor.putFloat(Constants.PREF_TOTAL_NOISE, (float) totalNoiseLevel);
        editor.putInt(Constants.PREF_MEASUREMENTS, noiseMeasurements);
        editor.putLong(Constants.PREF_MIN_NOISE_TIME, minNoiseTime);
        editor.putLong(Constants.PREF_MAX_NOISE_TIME, maxNoiseTime);
        // Зберігаємо останній виміряний рівень шуму
        editor.putFloat(Constants.PREF_LAST_NOISE_LEVEL, (float) lastNoiseLevel);
        editor.apply();
    }

    // Скидання статистики
    private void resetStats() {
        try {
            // Скидаємо статистику
        minNoiseLevel = 0;
        maxNoiseLevel = 0;
        totalNoiseLevel = 0;
        noiseMeasurements = 0;
        minNoiseTime = 0;
        maxNoiseTime = 0;
        
            // Зберігаємо скинуті значення
        saveNoiseStats();
        
            // Відправляємо broadcast про оновлення
            Intent broadcastIntent = new Intent(Constants.ACTION_NOISE_UPDATE);
            broadcastIntent.putExtra(Constants.EXTRA_NOISE_LEVEL, lastNoiseLevel);
            sendBroadcast(broadcastIntent);
            
            Log.d(TAG, "resetStats: Статистику скинуто");
        } catch (Exception e) {
            Log.e(TAG, "resetStats: Помилка при скиданні статистики", e);
        }
    }

    // Перемикання режиму запису
    private void toggleRecording() {
        try {
            Log.d(TAG, "toggleRecording: Перемикання стану запису");
            Log.d(TAG, "toggleRecording: Поточний стан: isPaused=" + isPaused + ", isRecording=" + isRecording + ", mediaRecorder=" + (mediaRecorder != null));
            
            // Перемикаємо стан
            isRecording = !isRecording;
            isPaused = !isRecording;
            
            // Зберігаємо стан у SharedPreferences
            SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit();
            editor.putBoolean(Constants.PREF_IS_PAUSED, isPaused);
            editor.apply();
            
            if (isRecording) {
                // Запускаємо запис
                Log.d(TAG, "toggleRecording: Запускаємо запис");
                
                // Ініціалізуємо медіарекордер, якщо він відсутній
                if (mediaRecorder == null) {
                    initializeMediaRecorder();
                }
                
                // Починаємо оновлення шуму
                startNoiseUpdates();
                
                // Відправляємо broadcast з оновленим станом
                broadcastRecordingState(true);
                broadcastStatus(getString(R.string.service_running));
            } else {
                // Зупиняємо запис
                Log.d(TAG, "toggleRecording: Зупиняємо запис");
                
                // Зберігаємо останній рівень шуму перед зупинкою
                saveNoiseStats();
                
                // Зупиняємо оновлення шуму
                stopNoiseUpdates();
                
                // Відправляємо broadcast з оновленим станом
                broadcastRecordingState(false);
                broadcastStatus(getString(R.string.service_paused));
            }
            
            // Оновлюємо нотифікацію
            updateNotification();
            
            // Відправляємо повний стан у MainActivity
            sendCurrentStateBroadcast();
            
            Log.d(TAG, "toggleRecording: Стан успішно змінено: isRecording=" + isRecording + ", isPaused=" + isPaused);
        } catch (Exception e) {
            Log.e(TAG, "toggleRecording: Помилка при перемиканні запису", e);
        }
    }

    // Одноразове вимірювання
    private void performSingleMeasurement() {
        isSingleMeasurement = true;
        
        if (!isRecording) {
            // Якщо запис не запущено - запускаємо його
            // startNoiseUpdates обробить завершення вимірювання
            initializeMediaRecorder();
        } else {
            // Вже записуємо - просто отримуємо значення і завершуємо
            double level = getNoiseLevelFromRecorder();
            broadcastNoiseLevel(level);
            
            // Плануємо зупинку через невеликий проміжок часу
            if (handler != null) {
                handler.postDelayed(() -> {
                    // Оскільки запис вже був активний, не зупиняємо його
                    // Тільки скидаємо прапорець одноразового вимірювання
                    isSingleMeasurement = false;
                }, 300);
            } else {
                isSingleMeasurement = false;
            }
        }
    }

    // Зупинка сервісу
    public void stopNoiseService() {
        try {
            if (isServiceRunning) {
                stopNoiseUpdates();
                saveNoiseStats();
                releaseMediaRecorder();
                isServiceRunning = false;
                stopForeground(true);
                stopSelf();
            }
        } catch (Exception e) {
            Log.e(TAG, "stopNoiseService: Помилка", e);
        }
    }

    // Обробка зміни режиму фонового сервісу
    private void handleBackgroundStateChange(Intent intent) {
        boolean wasBackgroundEnabled = isBackgroundServiceEnabled;
        isBackgroundServiceEnabled = intent.getBooleanExtra(Constants.PREF_BACKGROUND_SERVICE, true);
        
        // Зберігаємо налаштування
        SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit();
        editor.putBoolean(Constants.PREF_BACKGROUND_SERVICE, isBackgroundServiceEnabled);
        editor.apply();
        
        Log.d(TAG, "Background service setting changed: " + isBackgroundServiceEnabled);
        
        // ISSUE #6: Critical moment - handle transition from enabled->disabled
        if (wasBackgroundEnabled && !isBackgroundServiceEnabled) {
            // Ensure notification is completely removed
            stopForeground(true);
            notificationManager.cancel(Constants.NOTIFICATION_ID);
            Log.d(TAG, "Removed notification due to background service being disabled");
            
            // If app is not in foreground, we need to stop recording
            if (!isAppForeground && isRecording) {
                isPaused = true;
                stopNoiseUpdates();
                releaseMediaRecorder();
                broadcastStatus(getString(R.string.service_paused));
                Log.d(TAG, "Stopped recording because app is in background and background service disabled");
            }
        } 
        // ISSUE #7: Handle transition from disabled->enabled
        else if (!wasBackgroundEnabled && isBackgroundServiceEnabled && isRecording) {
            // Show notification if recording is active
            createOrUpdateNotification(lastNoiseLevel);
            Log.d(TAG, "Created notification after enabling background service");
        }
    }

    // Обробка зміни стану запису
    private void handleRecordingStateChange(Intent intent) {
        boolean shouldRecord = intent.getBooleanExtra(Constants.EXTRA_IS_RECORDING, false);
        
        Log.d(TAG, "handleRecordingStateChange: Отримано запит на зміну стану запису, shouldRecord=" + shouldRecord);
        
        if (shouldRecord && (!isRecording || isPaused)) {
            // Запускаємо запис, якщо він ще не запущений або на паузі
            Log.d(TAG, "handleRecordingStateChange: Запускаємо запис");
            isPaused = false;
            startNoiseUpdates(); // Це також викличе initializeMediaRecorder якщо потрібно
            
            // Оновлюємо UI
            broadcastRecordingState(true);
            broadcastStatus(getString(R.string.service_running));
        } else if (!shouldRecord && isRecording && !isPaused) {
            // Зупиняємо запис, якщо він активний
            Log.d(TAG, "handleRecordingStateChange: Зупиняємо запис");
            stopNoiseUpdates();
            releaseMediaRecorder();
            
            // Оновлюємо UI
            broadcastRecordingState(false);
            broadcastStatus(getString(R.string.service_paused));
        } else {
            Log.d(TAG, "handleRecordingStateChange: Не потрібно змінювати стан, поточний стан: isRecording=" 
                  + isRecording + ", isPaused=" + isPaused);
        }
    }

    // Відправка даних одним інтентом (уніфікована)
    private void broadcastUpdate(double noiseLevel, String status, boolean recordingState) {
        try {
            Intent intent = new Intent(Constants.ACTION_NOISE_UPDATE);
            intent.setPackage(getPackageName());
            
            // Рівень шуму - використовуємо lastNoiseLevel, якщо передано 0 і сервіс на паузі
            double broadcastValue = noiseLevel;
            if (isPaused && noiseLevel <= 0 && lastNoiseLevel > 0) {
                broadcastValue = lastNoiseLevel;
                Log.d(TAG, "broadcastUpdate: Використовуємо збережений рівень шуму для broadcast: " + lastNoiseLevel + " dB");
            }
            
            intent.putExtra(Constants.EXTRA_NOISE_LEVEL, broadcastValue);
            
            // Статистика
            intent.putExtra("min_noise", minNoiseLevel);
            intent.putExtra("max_noise", maxNoiseLevel);
            intent.putExtra("total_noise", totalNoiseLevel);
            intent.putExtra("measurements", noiseMeasurements);
            intent.putExtra("min_noise_time", minNoiseTime);
            intent.putExtra("max_noise_time", maxNoiseTime);
            
            // Стан запису
            intent.putExtra(Constants.EXTRA_RECORDING_STATE, recordingState);
            
            // Статус
            if (status != null) {
                intent.putExtra(Constants.EXTRA_STATUS, status);
            } else {
                String defaultStatus = recordingState ? 
                    getString(R.string.service_running) : getString(R.string.service_paused);
                intent.putExtra(Constants.EXTRA_STATUS, defaultStatus);
            }
            
            // Стан фонового моніторингу
            intent.putExtra(Constants.EXTRA_BACKGROUND_SERVICE_STATE, isBackgroundServiceEnabled);
            
            sendBroadcast(intent);
        } catch (Exception e) {
            Log.e(TAG, "broadcastUpdate: Помилка", e);
        }
    }
    
    // Відправка статусу (використовує уніфікований метод)
    private void broadcastStatus(String status) {
        // Важливо: Передаємо актуальний стан - активний запис лише якщо не на паузі
        boolean actualRecordingState = isRecording && !isPaused;
        broadcastUpdate(lastNoiseLevel, status, actualRecordingState);
    }

    // Відправка стану запису (використовує уніфікований метод)
    private void broadcastRecordingState(boolean recordingState) {
        // Явно передаємо стан запису, переданий як параметр, а не внутрішнє значення isRecording
        broadcastUpdate(lastNoiseLevel, null, recordingState);
    }

    // Відправка рівня шуму (використовує уніфікований метод)
    private void broadcastNoiseLevel(double db) {
        // Важливо: Передаємо актуальний стан - активний запис лише якщо не на паузі
        boolean actualRecordingState = isRecording && !isPaused;
        broadcastUpdate(db, null, actualRecordingState);
    }

    // Робота з WakeLock
    private void acquireWakeLock() {
        try {
            if (wakeLock == null) {
                PowerManager powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKELOCK_TAG);
                wakeLock.setReferenceCounted(false);
            }
            
            if (!wakeLock.isHeld()) {
                wakeLock.acquire(10*60*1000L); // 10 хвилин
            }
        } catch (Exception e) {
            Log.e(TAG, "acquireWakeLock: Помилка", e);
        }
    }

    private void releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception e) {
            Log.e(TAG, "releaseWakeLock: Помилка", e);
        }
    }

    @Override
    public void onDestroy() {
        try {
            Log.d(TAG, "Service being destroyed - cleaning up resources");
            
            // Save noise stats including lastNoiseLevel before exit
            saveNoiseStats();
            
            // Stop all active processing
            stopNoiseUpdates();
            releaseMediaRecorder();
            releaseWakeLock();
            
            // Update service state
            isServiceRunning = false;
            
            // Always ensure notification is completely removed
            stopForeground(true);
            notificationManager.cancel(Constants.NOTIFICATION_ID);
            
        } catch (Exception e) {
            Log.e(TAG, "onDestroy: Error", e);
        } finally {
            super.onDestroy();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    // Add the method to handle calibration updates
    /**
     * Обробляє оновлення налаштувань калібрування
     */
    private void handleCalibrationUpdate(Intent intent) {
        try {
            boolean settingsChanged = false;
            
            if (intent.hasExtra(Constants.EXTRA_UPDATE_FREQUENCY)) {
                long newFrequency = intent.getLongExtra(Constants.EXTRA_UPDATE_FREQUENCY, Constants.NOISE_UPDATE_INTERVAL);
                settingsChanged = newFrequency != updateFrequency;
                updateFrequency = newFrequency;
                Log.d(TAG, "handleCalibrationUpdate: Частота оновлення змінена на " + updateFrequency + " мс");
            }
            
            if (intent.hasExtra(Constants.EXTRA_SENSITIVITY_OFFSET)) {
                sensitivityOffset = intent.getDoubleExtra(Constants.EXTRA_SENSITIVITY_OFFSET, 0.0);
                Log.d(TAG, "handleCalibrationUpdate: Зміщення чутливості змінено на " + sensitivityOffset + " dB");
            }
            
            if (intent.hasExtra(Constants.EXTRA_SMOOTHING_FACTOR)) {
                smoothingFactor = intent.getDoubleExtra(Constants.EXTRA_SMOOTHING_FACTOR, 0.3);
                Log.d(TAG, "handleCalibrationUpdate: Коефіцієнт згладжування змінено на " + smoothingFactor);
            }
            
            if (intent.hasExtra(Constants.EXTRA_MIN_NOISE_LEVEL)) {
                minNoiseDetectionLevel = intent.getDoubleExtra(Constants.EXTRA_MIN_NOISE_LEVEL, Constants.MIN_VALID_DB);
                Log.d(TAG, "handleCalibrationUpdate: Мінімальний рівень шуму змінено на " + minNoiseDetectionLevel + " dB");
            }

            // Перезапускаємо оновлення шуму тільки якщо змінилася частота оновлення і запис активний
            if (isRecording && !isPaused && settingsChanged) {
                Log.d(TAG, "handleCalibrationUpdate: Перезапуск оновлення через зміну частоти");
                
                // Не зупиняємо повністю, а лише оновлюємо таймінг
                if (handler != null && updateNoise != null) {
                    handler.removeCallbacks(updateNoise);
                    handler.postDelayed(updateNoise, updateFrequency);
                }
            }
            
            // Повідомляємо про оновлення налаштувань
            broadcastStatus(getString(R.string.calibration_updated));
        } catch (Exception e) {
            Log.e(TAG, "handleCalibrationUpdate: Помилка при оновленні налаштувань калібрування", e);
        }
    }

    /**
     * Завантажує налаштування калібрування з SharedPreferences
     */
    private void loadCalibrationSettings(SharedPreferences prefs) {
        try {
            // Завантажуємо налаштування калібрування
            sensitivityOffset = prefs.getFloat(Constants.PREF_SENSITIVITY_OFFSET, 0);
            smoothingFactor = prefs.getFloat(Constants.PREF_SMOOTHING_FACTOR, 0.3f);
            minNoiseDetectionLevel = prefs.getFloat(Constants.PREF_MIN_NOISE_DETECTION_LEVEL, (float)Constants.MIN_VALID_DB);
            
            Log.d(TAG, "loadCalibrationSettings: Налаштування калібрування завантажено: " +
                  "sensitivityOffset=" + sensitivityOffset + ", smoothingFactor=" + smoothingFactor + 
                  ", minNoiseDetectionLevel=" + minNoiseDetectionLevel);
        } catch (Exception e) {
            Log.e(TAG, "loadCalibrationSettings: Помилка при завантаженні налаштувань калібрування", e);
        }
    }

    private void cleanup() {
        // Implementation of cleanup method
    }

    private void startMeasurementTimer() {
        try {
            if (isTimerRunning) {
                Log.d(TAG, "startMeasurementTimer: Таймер вже запущений, пропускаємо");
                return;
            }
            
            // Ініціалізуємо MediaRecorder для вимірювання шуму
            initializeMediaRecorder();
            
            // Запускаємо таймер вимірювання
            handler = new Handler(Looper.getMainLooper());
            updateNoise = new Runnable() {
                @Override
                public void run() {
                    if (isRecording) {
                        try {
                            measureNoiseLevel();
                            handler.postDelayed(this, updateFrequency);
                        } catch (Exception e) {
                            Log.e(TAG, "startMeasurementTimer: Помилка при вимірюванні шуму", e);
                            stopMeasurementTimer();
                            errorCount++;
                            
                            // Спроба перезапуску після помилки
                            if (errorCount < Constants.MAX_ERRORS) {
                                Log.d(TAG, "startMeasurementTimer: Спроба перезапуску після помилки");
                                startMeasurementTimer();
                            } else {
                                Log.e(TAG, "startMeasurementTimer: Забагато помилок, зупиняємо вимірювання");
                            }
                        }
                    } else {
                        handler.removeCallbacks(this);
                        isTimerRunning = false;
                    }
                }
            };
            
            handler.post(updateNoise);
            isTimerRunning = true;
            Log.d(TAG, "startMeasurementTimer: Таймер вимірювання запущено");
        } catch (Exception e) {
            Log.e(TAG, "startMeasurementTimer: Помилка при запуску таймера вимірювання", e);
        }
    }

    private void stopMeasurementTimer() {
        try {
            // Зупиняємо таймер вимірювання
            if (handler != null && updateNoise != null) {
                handler.removeCallbacks(updateNoise);
                Log.d(TAG, "stopMeasurementTimer: Таймер вимірювання зупинено");
            }
            
            // Звільняємо MediaRecorder
            releaseMediaRecorder();
            
            isTimerRunning = false;
        } catch (Exception e) {
            Log.e(TAG, "stopMeasurementTimer: Помилка при зупинці таймера вимірювання", e);
        }
    }

    private void performMeasurement() {
        try {
            // Виконуємо одноразове вимірювання шуму
            if (!isTimerRunning) {
                isSingleMeasurement = true;
                startMeasurementTimer();
                
                // Зупиняємо вимірювання після одного заміру
                new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (isSingleMeasurement) {
                            stopMeasurementTimer();
                            isSingleMeasurement = false;
                        }
                    }
                }, updateFrequency * 3); // Даємо час на вимірювання
            } else {
                // Просто виконуємо одне вимірювання, якщо таймер вже запущений
                measureNoiseLevel();
            }
        } catch (Exception e) {
            Log.e(TAG, "performMeasurement: Помилка при виконанні одноразового вимірювання", e);
        }
    }

    private void measureNoiseLevel() {
        try {
            if (mediaRecorder == null) {
                Log.e(TAG, "measureNoiseLevel: MediaRecorder не ініціалізований");
                return;
            }
            
            // Отримуємо амплітуду звуку
            int amplitude = mediaRecorder.getMaxAmplitude();
            
            // Обчислюємо рівень шуму в дБ
            double db = 0;
            if (amplitude > 0) {
                db = 20 * Math.log10(amplitude / Constants.REFERENCE_AMPLITUDE) + Constants.CALIBRATION_OFFSET + sensitivityOffset;
                
                // Обмежуємо діапазон
                if (db < Constants.MIN_DB) db = Constants.MIN_DB;
                if (db > Constants.MAX_DB) db = Constants.MAX_DB;
                
                // Застосовуємо згладжування
                if (lastNoiseLevel > 0) {
                    db = lastNoiseLevel * (1 - smoothingFactor) + db * smoothingFactor;
                }
                
                // Ігноруємо дуже низькі значення
                if (db < minNoiseDetectionLevel) {
                    db = 0;
                }
            }
            
            // Оновлюємо останній рівень шуму
            lastNoiseLevel = db;
            
            // Оновлюємо статистику
            updateNoiseStats(db);
            
            // Оновлюємо нотифікацію, якщо пройшов певний час
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastNotificationUpdate > Constants.NOTIFICATION_UPDATE_INTERVAL) {
                updateNotification();
                lastNotificationUpdate = currentTime;
            }
            
            // Відправляємо broadcast з оновленням
            Intent broadcastIntent = new Intent(Constants.ACTION_NOISE_UPDATE);
            broadcastIntent.putExtra(Constants.EXTRA_NOISE_LEVEL, db);
            sendBroadcast(broadcastIntent);
            
            Log.d(TAG, "measureNoiseLevel: Виміряно рівень шуму: " + db + " дБ");
        } catch (Exception e) {
            Log.e(TAG, "measureNoiseLevel: Помилка при вимірюванні рівня шуму", e);
            throw e; // Перекидаємо помилку для перезапуску медіарекордера
        }
    }

    private void updateNotification() {
        try {
            createNotificationChannel();
            
            // Створюємо інтент для відкриття активності при натисканні на нотифікацію
            Intent notificationIntent = new Intent(this, MainActivity.class);
            notificationIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                0,
                notificationIntent,
                PendingIntent.FLAG_IMMUTABLE
            );
            
            // Визначаємо статус запису
            String contentText = isRecording ? 
                getString(R.string.service_running) : 
                getString(R.string.service_paused);
            
            // Створюємо нотифікацію
            NotificationCompat.Builder builder = new NotificationCompat.Builder(this, Constants.CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(contentText)
                .setSmallIcon(R.drawable.ic_stat_kisspng_computer_icons)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setOngoing(true);
            
            Notification notification = builder.build();
            
            // Запускаємо сервіс у режимі foreground з нотифікацією
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(Constants.NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
                } else {
                    startForeground(Constants.NOTIFICATION_ID, notification);
                }
                
                Log.d(TAG, "updateNotification: Нотифікацію оновлено, статус: " + contentText);
                isServiceRunning = true;
                
                // Відправляємо актуальний стан через broadcast
                sendCurrentStateBroadcast();
            } catch (Exception e) {
                Log.e(TAG, "updateNotification: Помилка при запуску foreground сервісу", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "updateNotification: Помилка при оновленні нотифікації", e);
        }
    }

    // Методи, які використовуються в onStartCommand

    private void saveServiceState() {
        try {
            // Зберігаємо поточний стан сервісу
            SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putBoolean(Constants.PREF_IS_PAUSED, !isRecording);
            editor.apply();
            
            Log.d(TAG, "saveServiceState: Стан сервісу збережено: isRecording=" + isRecording);
        } catch (Exception e) {
            Log.e(TAG, "saveServiceState: Помилка при збереженні стану сервісу", e);
        }
    }

    private void sendRecordingStateChangedBroadcast() {
        try {
            // Відправляємо broadcast зі зміною стану запису
            Intent broadcastIntent = new Intent(Constants.ACTION_TOGGLE_RECORDING);
            broadcastIntent.putExtra(Constants.EXTRA_RECORDING_STATE, isRecording);
            sendBroadcast(broadcastIntent);
            
            Log.d(TAG, "sendRecordingStateChangedBroadcast: Відправлено broadcast про зміну стану запису: " + isRecording);
        } catch (Exception e) {
            Log.e(TAG, "sendRecordingStateChangedBroadcast: Помилка при відправці broadcast", e);
        }
    }

    private void sendCurrentStateBroadcast() {
        try {
            // Відправляємо broadcast з поточним станом
            Intent broadcastIntent = new Intent(Constants.ACTION_NOISE_UPDATE);
            broadcastIntent.putExtra(Constants.EXTRA_RECORDING_STATE, isRecording);
            broadcastIntent.putExtra(Constants.EXTRA_STATUS, isRecording ? 
                getString(R.string.service_running) : getString(R.string.service_paused));
            broadcastIntent.putExtra(Constants.EXTRA_BACKGROUND_SERVICE_STATE, isBackgroundServiceEnabled);
            
            // Додаємо всю статистичну інформацію
            broadcastIntent.putExtra("min_noise", minNoiseLevel);
            broadcastIntent.putExtra("max_noise", maxNoiseLevel);
            broadcastIntent.putExtra("total_noise", totalNoiseLevel);
            broadcastIntent.putExtra("measurements", noiseMeasurements);
            broadcastIntent.putExtra("min_noise_time", minNoiseTime);
            broadcastIntent.putExtra("max_noise_time", maxNoiseTime);
            
            sendBroadcast(broadcastIntent);
            
            Log.d(TAG, "sendCurrentStateBroadcast: Відправлено broadcast з поточним станом: " + isRecording);
        } catch (Exception e) {
            Log.e(TAG, "sendCurrentStateBroadcast: Помилка при відправці broadcast", e);
        }
    }
} 