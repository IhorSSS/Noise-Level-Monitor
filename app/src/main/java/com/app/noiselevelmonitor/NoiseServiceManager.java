package com.app.noiselevelmonitor;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.util.Log;

/**
 * Менеджер сервісу шуму - забезпечує єдину точку доступу до сервісу.
 * Реалізує шаблон Singleton для запобігання дублювання операцій з сервісом.
 */
public class NoiseServiceManager {
    private static final String TAG = "NoiseServiceManager";
    private static final String PREFS_NAME = Constants.PREFS_NAME;
    private static final long SERVICE_OP_COOLDOWN_MS = 1000; // 1 секунда між операціями
    
    private static NoiseServiceManager instance;
    private static final Object lock = new Object(); // Для потокобезпечності
    
    private final Context context;
    private long lastServiceOpTime = 0;
    
    // Конструктор приватний для реалізації патерну Singleton
    private NoiseServiceManager(Context context) {
        this.context = context.getApplicationContext(); // Використовуємо ApplicationContext
    }
    
    /**
     * Отримання екземпляру менеджера.
     */
    public static NoiseServiceManager getInstance(Context context) {
        if (instance == null) {
            synchronized (lock) {
                if (instance == null) {
                    instance = new NoiseServiceManager(context);
                }
            }
        }
        return instance;
    }
    
    /**
     * Запуск сервісу шуму.
     * 
     * @return true, якщо запуск успішний або сервіс вже запущено
     */
    public boolean startService() {
        synchronized (lock) {
            // Перевіряємо кулдаун між операціями
            if (!checkOpCooldown()) {
                Log.d(TAG, "startService: Операція відхилена через кулдаун");
                return false;
            }
            
            try {
                // Перевіряємо, чи сервіс вже запущений
                if (isServiceRunning()) {
                    Log.d(TAG, "startService: Сервіс вже запущений");
                    return true;
                }
                
                Log.d(TAG, "startService: Запускаємо сервіс");
                
                // Отримуємо налаштування
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                
                // Перевіряємо, чи це перший запуск додатку
                boolean isFirstLaunch = !prefs.getBoolean(Constants.PREF_FIRST_LAUNCH, false);
                
                // Отримуємо збережений стан паузи
                boolean isPaused = prefs.getBoolean(Constants.PREF_IS_PAUSED, false);
                boolean isRecording = !isPaused;
                
                // При першому запуску гарантуємо, що запис активний
                if (isFirstLaunch) {
                    isRecording = true;
                    
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putBoolean(Constants.PREF_IS_PAUSED, false);
                    editor.putBoolean(Constants.PREF_FIRST_LAUNCH, true);
                    editor.apply();
                    
                    Log.d(TAG, "startService: Перший запуск, встановлено isRecording=true");
                }
                
                String currentLanguage = prefs.getString(Constants.PREF_LANGUAGE, "uk");
                
                // Підготовка інтенту запуску сервісу
                Intent serviceIntent = new Intent(context, NoiseService.class);
                
                // Параметр wasFullyShutdown вказує, чи це повний запуск
                serviceIntent.putExtra(Constants.EXTRA_WAS_FULLY_SHUTDOWN, true);
                serviceIntent.putExtra("isFirstLaunch", isFirstLaunch);
                serviceIntent.putExtra("isRecording", isRecording);
                serviceIntent.putExtra(Constants.PREF_LANGUAGE, currentLanguage);
                
                // Додаємо інформацію про фоновий моніторинг
                boolean isBackgroundServiceEnabled = prefs.getBoolean(Constants.PREF_BACKGROUND_SERVICE, true);
                serviceIntent.putExtra(Constants.PREF_BACKGROUND_SERVICE, isBackgroundServiceEnabled);
                
                Log.d(TAG, "startService: Запуск сервісу з параметрами: isFirstLaunch=" + isFirstLaunch + 
                      ", isRecording=" + isRecording);
                
                // Запускаємо сервіс
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent);
                } else {
                    context.startService(serviceIntent);
                }
                
                // Зберігаємо час останньої операції
                updateLastOpTime();
                
                return true;
            } catch (Exception e) {
                Log.e(TAG, "startService: Помилка при запуску сервісу", e);
                return false;
            }
        }
    }
    
    /**
     * Зупинка сервісу шуму.
     * 
     * @return true, якщо зупинка успішна або сервіс вже зупинено
     */
    public boolean stopService() {
        synchronized (lock) {
            // Перевіряємо кулдаун між операціями
            if (!checkOpCooldown()) {
                Log.d(TAG, "stopService: Операція відхилена через кулдаун");
                return false;
            }
            
            try {
                // Перевіряємо, чи сервіс запущений
                if (!isServiceRunning()) {
                    Log.d(TAG, "stopService: Сервіс вже зупинено");
                    return true;
                }
                
                Log.d(TAG, "stopService: Зупиняємо сервіс");
                
                // Створюємо інтент для зупинки сервісу
                Intent serviceIntent = new Intent(context, NoiseService.class);
                serviceIntent.setAction(Constants.ACTION_STOP_SERVICE);
                
                // Зупиняємо сервіс
                context.startService(serviceIntent);
                
                // Зберігаємо час останньої операції
                updateLastOpTime();
                
                return true;
            } catch (Exception e) {
                Log.e(TAG, "stopService: Помилка при зупинці сервісу", e);
                return false;
            }
        }
    }
    
    /**
     * Перевірка, чи запущений сервіс шуму.
     * 
     * @return true, якщо сервіс запущений
     */
    public boolean isServiceRunning() {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
                if (NoiseService.class.getName().equals(service.service.getClassName())) {
                    return true;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "isServiceRunning: Помилка при перевірці стану сервісу", e);
        }
        return false;
    }
    
    /**
     * Перемикання стану запису (пауза/продовження).
     */
    public void toggleRecording() {
        synchronized (lock) {
            // Перевіряємо кулдаун між операціями
            if (!checkOpCooldown()) {
                Log.d(TAG, "toggleRecording: Операція відхилена через кулдаун");
                return;
            }
            
            try {
                Log.d(TAG, "toggleRecording: Перемикаємо стан запису");
                
                // Перевіряємо, чи сервіс запущений
                if (!isServiceRunning()) {
                    Log.d(TAG, "toggleRecording: Сервіс не запущений, пропускаємо");
                    return;
                }
                
                // Відправляємо інтент для перемикання запису
                Intent intent = new Intent(context, NoiseService.class);
                intent.setAction(Constants.ACTION_TOGGLE_RECORDING);
                intent.putExtra("timestamp", System.currentTimeMillis()); // Унікальний timestamp
                context.startService(intent);
                
                // Зберігаємо час останньої операції
                updateLastOpTime();
            } catch (Exception e) {
                Log.e(TAG, "toggleRecording: Помилка при перемиканні запису", e);
            }
        }
    }
    
    /**
     * Скидання статистики шуму.
     */
    public void resetStats() {
        synchronized (lock) {
            // Перевіряємо кулдаун між операціями
            if (!checkOpCooldown()) {
                Log.d(TAG, "resetStats: Операція відхилена через кулдаун");
                return;
            }
            
            try {
                Log.d(TAG, "resetStats: Скидаємо статистику");
                
                // Перевіряємо, чи сервіс запущений
                if (!isServiceRunning()) {
                    Log.d(TAG, "resetStats: Сервіс не запущений, пропускаємо");
                    return;
                }
                
                // Відправляємо інтент для скидання статистики
                Intent intent = new Intent(context, NoiseService.class);
                intent.setAction(Constants.ACTION_RESET_STATS);
                context.startService(intent);
                
                // Зберігаємо час останньої операції
                updateLastOpTime();
            } catch (Exception e) {
                Log.e(TAG, "resetStats: Помилка при скиданні статистики", e);
            }
        }
    }
    
    /**
     * Оновлення стану фонового моніторингу.
     * 
     * @param isEnabled true, якщо фоновий моніторинг увімкнено
     */
    public void updateBackgroundState(boolean isEnabled) {
        synchronized (lock) {
            // Перевіряємо кулдаун між операціями
            if (!checkOpCooldown()) {
                Log.d(TAG, "updateBackgroundState: Операція відхилена через кулдаун");
                return;
            }
            
            try {
                Log.d(TAG, "updateBackgroundState: Оновлюємо стан фонового моніторингу на " + isEnabled);
                
                // Зберігаємо налаштування
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                SharedPreferences.Editor editor = prefs.edit();
                editor.putBoolean(Constants.PREF_BACKGROUND_SERVICE, isEnabled);
                editor.apply();
                
                // Перевіряємо, чи сервіс запущений
                if (!isServiceRunning()) {
                    Log.d(TAG, "updateBackgroundState: Сервіс не запущений, пропускаємо");
                    return;
                }
                
                // Оновлюємо сервіс
                Intent serviceIntent = new Intent(context, NoiseService.class);
                serviceIntent.setAction(Constants.ACTION_UPDATE_BACKGROUND_STATE);
                serviceIntent.putExtra(Constants.PREF_BACKGROUND_SERVICE, isEnabled);
                context.startService(serviceIntent);
                
                // Зберігаємо час останньої операції
                updateLastOpTime();
            } catch (Exception e) {
                Log.e(TAG, "updateBackgroundState: Помилка при оновленні стану фонового моніторингу", e);
            }
        }
    }
    
    /**
     * Повідомлення сервісу, що додаток перейшов на передній план.
     */
    public void notifyForeground() {
        synchronized (lock) {
            // Перевіряємо кулдаун між операціями
            if (!checkOpCooldown()) {
                Log.d(TAG, "notifyForeground: Операція відхилена через кулдаун");
                return;
            }
            
            try {
                Log.d(TAG, "notifyForeground: Повідомляємо про перехід на передній план");
                
                // Перевіряємо, чи сервіс запущений
                if (!isServiceRunning()) {
                    Log.d(TAG, "notifyForeground: Сервіс не запущений, пропускаємо");
                    return;
                }
                
                // Відправляємо інтент
                Intent foregroundIntent = new Intent(context, NoiseService.class);
                foregroundIntent.setAction(Constants.ACTION_APP_TO_FOREGROUND);
                context.startService(foregroundIntent);
                
                // Зберігаємо час останньої операції
                updateLastOpTime();
            } catch (Exception e) {
                Log.e(TAG, "notifyForeground: Помилка при повідомленні про передній план", e);
            }
        }
    }
    
    /**
     * Повідомлення сервісу, що додаток перейшов у фон.
     */
    public void notifyBackground() {
        synchronized (lock) {
            // Перевіряємо кулдаун між операціями
            if (!checkOpCooldown()) {
                Log.d(TAG, "notifyBackground: Операція відхилена через кулдаун");
                return;
            }
            
            try {
                Log.d(TAG, "notifyBackground: Повідомляємо про перехід у фон");
                
                // Перевіряємо, чи сервіс запущений
                if (!isServiceRunning()) {
                    Log.d(TAG, "notifyBackground: Сервіс не запущений, пропускаємо");
                    return;
                }
                
                // Відправляємо інтент
                Intent backgroundIntent = new Intent(context, NoiseService.class);
                backgroundIntent.setAction(Constants.ACTION_APP_TO_BACKGROUND);
                context.startService(backgroundIntent);
                
                // Зберігаємо час останньої операції
                updateLastOpTime();
            } catch (Exception e) {
                Log.e(TAG, "notifyBackground: Помилка при повідомленні про фон", e);
            }
        }
    }
    
    /**
     * Перевірка кулдауну між операціями сервісу.
     * Запобігає занадто частим викликам сервісу.
     * 
     * @return true, якщо минув достатній час від останньої операції
     */
    private boolean checkOpCooldown() {
        long currentTime = SystemClock.elapsedRealtime();
        return (currentTime - lastServiceOpTime) >= SERVICE_OP_COOLDOWN_MS;
    }
    
    /**
     * Оновлення часу останньої операції з сервісом.
     */
    private void updateLastOpTime() {
        lastServiceOpTime = SystemClock.elapsedRealtime();
    }
} 