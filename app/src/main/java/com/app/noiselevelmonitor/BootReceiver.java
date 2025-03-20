package com.app.noiselevelmonitor;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.util.Log;
import androidx.core.content.ContextCompat;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            if (checkPermissions(context)) {
                try {
                    Intent serviceIntent = new Intent(context, NoiseService.class);
                    serviceIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent);
                    } else {
                        context.startService(serviceIntent);
                    }
                    Log.d(TAG, "Сервіс моніторингу шуму запущено після перезавантаження");
                } catch (Exception e) {
                    Log.e(TAG, "Помилка при запуску сервісу: " + e.getMessage());
                }
            } else {
                Log.w(TAG, "Відсутні необхідні дозволи для запуску сервісу");
            }
        }
    }

    private boolean checkPermissions(Context context) {
        boolean audioPermission = ContextCompat.checkSelfPermission(context, 
            Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        
        boolean fgServicePermission = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            fgServicePermission = ContextCompat.checkSelfPermission(context,
                Manifest.permission.FOREGROUND_SERVICE_MICROPHONE) == PackageManager.PERMISSION_GRANTED;
        }
        
        return audioPermission && fgServicePermission;
    }
} 