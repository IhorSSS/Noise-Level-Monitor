package com.app.noiselevelmonitor;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.util.Log;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import java.util.Locale;
import android.content.SharedPreferences;
import android.os.Build;

public abstract class BaseActivity extends AppCompatActivity {
    private static final String TAG = "BaseActivity";
    
    @Override
    protected void attachBaseContext(Context newBase) {
        try {
            // Отримуємо мову додатку
            NoiseMonitorApplication app = (NoiseMonitorApplication) newBase.getApplicationContext();
            String languageCode = app.getStoredLanguage();
            
            // Створюємо локаль
            Locale locale = new Locale(languageCode);
            
            // Конфігуруємо і створюємо контекст
            Configuration config = new Configuration();
            config.setLocale(locale);
            Context context = newBase.createConfigurationContext(config);
            
            super.attachBaseContext(context);
        } catch (Exception e) {
            Log.e(TAG, "Помилка при створенні контексту", e);
            super.attachBaseContext(newBase);
        }
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        try {
            // Для Android 13+ використовуємо AppCompatDelegate
            if (Build.VERSION.SDK_INT >= 33) {
                NoiseMonitorApplication app = (NoiseMonitorApplication) getApplication();
                String languageCode = app.getStoredLanguage();
                Locale locale = new Locale(languageCode);
                
                AppCompatDelegate.setApplicationLocales(
                    androidx.core.os.LocaleListCompat.create(locale));
            }
        } catch (Exception e) {
            Log.e(TAG, "Помилка при оновленні конфігурації", e);
        }
    }
} 