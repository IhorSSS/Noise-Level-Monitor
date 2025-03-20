package com.app.noiselevelmonitor;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import java.util.Locale;

public class NoiseMonitorApplication extends Application {
    private static final String TAG = "NoiseMonitorApplication";
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        // Set light theme by default on first launch
        initializeDefaultTheme();
        
        // Застосовуємо збережену мову при старті додатку
        setLocale(getStoredLanguage());
    }
    
    /**
     * Set light theme by default on first launch
     */
    private void initializeDefaultTheme() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        
        // Check if theme has been set before
        if (!prefs.contains(Constants.PREF_THEME_MODE)) {
            // It's the first launch - set light theme by default
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt(Constants.PREF_THEME_MODE, androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            editor.apply();
            
            // Apply the light theme immediately
            androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(
                androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO);
            
            Log.d(TAG, "First launch: Setting default light theme");
        }
    }
    
    /**
     * Отримує збережену мову з налаштувань або системну, якщо не вибрано
     * @return код мови
     */
    public String getStoredLanguage() {
        SharedPreferences prefs = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
        boolean isLanguageSelected = prefs.getBoolean(Constants.PREF_LANGUAGE_SELECTED, false);
        
        if (isLanguageSelected) {
            // Якщо користувач вже вибрав мову - використовуємо її
            return prefs.getString(Constants.PREF_LANGUAGE, Locale.getDefault().getLanguage());
        } else {
            // Якщо користувач ще не вибирав мову - використовуємо системну
            return Locale.getDefault().getLanguage();
        }
    }
    
    /**
     * Встановлює нову мову для додатку
     * @param languageCode код мови
     */
    public void setLocale(String languageCode) {
        try {
            // Створюємо локаль для вибраної мови
            Locale locale = new Locale(languageCode);
            
            // 1. Встановлюємо локаль за замовчуванням
            Locale.setDefault(locale);
            
            // 2. Оновлюємо через AppCompat (для сучасних версій Android)
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.create(locale));
            
            // 3. Зберігаємо вибрану мову в налаштуваннях
            SharedPreferences.Editor editor = getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE).edit();
            editor.putString(Constants.PREF_LANGUAGE, languageCode);
            editor.putBoolean(Constants.PREF_LANGUAGE_SELECTED, true);
            editor.apply();
        } catch (Exception e) {
            Log.e(TAG, "Помилка при встановленні мови", e);
        }
    }
    
    @Override
    protected void attachBaseContext(Context base) {
        try {
            // Отримуємо мову з налаштувань
            SharedPreferences prefs = base.getSharedPreferences(Constants.PREFS_NAME, MODE_PRIVATE);
            boolean isLanguageSelected = prefs.getBoolean(Constants.PREF_LANGUAGE_SELECTED, false);
            
            // Визначаємо мову
            String languageCode = isLanguageSelected 
                ? prefs.getString(Constants.PREF_LANGUAGE, Locale.getDefault().getLanguage())
                : Locale.getDefault().getLanguage();
            
            // Створюємо локаль та налаштовуємо конфігурацію
            Locale locale = new Locale(languageCode);
            Configuration config = new Configuration();
            config.setLocale(locale);
            
            // Створюємо контекст з налаштованою конфігурацією
            Context context = base.createConfigurationContext(config);
            
            super.attachBaseContext(context);
        } catch (Exception e) {
            super.attachBaseContext(base);
        }
    }
} 