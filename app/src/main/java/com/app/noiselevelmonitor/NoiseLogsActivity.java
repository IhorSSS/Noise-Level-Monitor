package com.app.noiselevelmonitor;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Collections;
import java.util.Comparator;

/**
 * Клас утиліт для часто використовуваних функцій
 */
class Utils {
    /**
     * Показати Toast повідомлення
     */
    public static void showToast(Context context, String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
}

/**
 * Активність для перегляду та управління логами шуму
 */
public class NoiseLogsActivity extends AppCompatActivity {
    private static final String TAG = "NoiseLogsActivity";
    private static final long LOG_UPDATE_INTERVAL_MS = 1000; // Оновлення кожну секунду
    
    // Константи для типів сортування
    private static final int SORT_BY_TIME = 0;
    private static final int SORT_BY_NOISE_ASC = 1;
    private static final int SORT_BY_NOISE_DESC = 2;
    
    private RecyclerView recyclerView;
    private NoiseLogAdapter adapter;
    private TextView emptyView;
    private Button clearLogsButton;
    private Button loggingPauseButton;
    private Handler updateHandler;
    private Runnable updateRunnable;
    private boolean isActivityActive = true;
    private Toolbar toolbar;
    private int currentSortType = SORT_BY_TIME; // За замовчуванням сортування за часом
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_noise_logs);
        
        // Встановлюємо стиль для PopupMenu
        getTheme().applyStyle(R.style.Theme_NoiseLevelMonitor_PopupMenu, true);
        
        // Налаштування панелі дій
        toolbar = findViewById(R.id.logsToolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.logs_title);
        }
        
        // Ініціалізація UI компонентів
        recyclerView = findViewById(R.id.logs_recycler_view);
        emptyView = findViewById(R.id.empty_logs_view);
        clearLogsButton = findViewById(R.id.clear_logs_button);
        loggingPauseButton = findViewById(R.id.logging_pause_button);
        
        // Налаштування RecyclerView
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        
        // Ініціалізація Handler для оновлення логів
        updateHandler = new Handler(Looper.getMainLooper());
        updateRunnable = this::loadLogs;
        
        // Завантаження логів
        loadLogs();
        
        // Налаштування кнопки очищення логів
        clearLogsButton.setOnClickListener(v -> {
            showClearLogsConfirmationDialog();
        });
        
        // Налаштування кнопки паузи/відновлення
        setupLoggingPauseButton();
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        isActivityActive = true;
        loadLogs(); // Оновити негайно
        startPeriodicUpdates();
        
        // Оновлюємо стан кнопки паузи/відновлення
        updateLoggingPauseButton(
            NoiseLogManager.getInstance(this).isLoggingEnabled(),
            NoiseLogManager.getInstance(this).isLoggingPaused());
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        isActivityActive = false;
        stopPeriodicUpdates();
    }
    
    /**
     * Запускає періодичне оновлення логів
     */
    private void startPeriodicUpdates() {
        updateHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (isActivityActive) {
                    loadLogs();
                    updateHandler.postDelayed(this, LOG_UPDATE_INTERVAL_MS);
                }
            }
        }, LOG_UPDATE_INTERVAL_MS);
    }
    
    /**
     * Зупиняє періодичне оновлення логів
     */
    private void stopPeriodicUpdates() {
        updateHandler.removeCallbacks(updateRunnable);
    }

    /**
     * Завантаження та відображення логів
     */
    private void loadLogs() {
        try {
            List<NoiseLogEntry> logs = NoiseLogManager.getInstance(this).getAllLogs();
            
            // Сортування логів в залежності від обраного методу
            sortLogs(logs);
            
            if (adapter == null) {
                adapter = new NoiseLogAdapter(logs);
                recyclerView.setAdapter(adapter);
            } else {
                // Оновлюємо існуючий адаптер замість створення нового
                adapter.updateData(logs);
            }
            
            // Показуємо або приховуємо повідомлення про пусті логи
            if (logs.isEmpty()) {
                recyclerView.setVisibility(View.GONE);
                emptyView.setVisibility(View.VISIBLE);
                clearLogsButton.setEnabled(false);
            } else {
                recyclerView.setVisibility(View.VISIBLE);
                emptyView.setVisibility(View.GONE);
                clearLogsButton.setEnabled(true);
            }
        } catch (Exception e) {
            Log.e(TAG, "Помилка при завантаженні логів", e);
            Utils.showToast(this, getString(R.string.error_data_retrieval, e.getMessage()));
        }
    }
    
    /**
     * Сортування логів за обраним методом
     */
    private void sortLogs(List<NoiseLogEntry> logs) {
        switch (currentSortType) {
            case SORT_BY_TIME:
                // За часом (від найновіших до найстаріших)
                Collections.sort(logs, (e1, e2) -> Long.compare(e2.getTimestamp(), e1.getTimestamp()));
                break;
            case SORT_BY_NOISE_ASC:
                // За рівнем шуму (від найтихіших до найгучніших)
                Collections.sort(logs, (e1, e2) -> Double.compare(e1.getNoiseLevel(), e2.getNoiseLevel()));
                break;
            case SORT_BY_NOISE_DESC:
                // За рівнем шуму (від найгучніших до найтихіших)
                Collections.sort(logs, (e1, e2) -> Double.compare(e2.getNoiseLevel(), e1.getNoiseLevel()));
                break;
        }
    }
    
    /**
     * Показує діалог підтвердження очищення логів
     */
    private void showClearLogsConfirmationDialog() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_logs)
                .setMessage(R.string.confirm_clear_logs)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    NoiseLogManager.getInstance(this).clearAllLogs();
                    Utils.showToast(this, getString(R.string.logs_cleared));
                    loadLogs(); // Оновлюємо список
                })
                .setNegativeButton(R.string.no, null)
                .show();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.logs_menu, menu);
        
        // Встановлюємо правильний стан для меню сортування
        switch (currentSortType) {
            case SORT_BY_TIME:
                menu.findItem(R.id.action_sort_time).setChecked(true);
                break;
            case SORT_BY_NOISE_ASC:
                menu.findItem(R.id.action_sort_noise_ascending).setChecked(true);
                break;
            case SORT_BY_NOISE_DESC:
                menu.findItem(R.id.action_sort_noise_descending).setChecked(true);
                break;
        }
        
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            onBackPressed();
            return true;
        } else if (item.getItemId() == R.id.action_export_csv) {
            exportLogsToCSV();
            return true;
        } else if (item.getItemId() == R.id.action_sort_time) {
            currentSortType = SORT_BY_TIME;
            item.setChecked(true);
            loadLogs();
            return true;
        } else if (item.getItemId() == R.id.action_sort_noise_ascending) {
            currentSortType = SORT_BY_NOISE_ASC;
            item.setChecked(true);
            loadLogs();
            return true;
        } else if (item.getItemId() == R.id.action_sort_noise_descending) {
            currentSortType = SORT_BY_NOISE_DESC;
            item.setChecked(true);
            loadLogs();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    /**
     * Експортує логи шуму в CSV-файл і відкриває діалог шерінгу
     */
    private void exportLogsToCSV() {
        try {
            // Отримуємо всі логи
            List<NoiseLogEntry> logs = NoiseLogManager.getInstance(this).getAllLogs();
            
            if (logs.isEmpty()) {
                Utils.showToast(this, getString(R.string.no_logs_for_export));
                return;
            }
            
            // Генеруємо ім'я файлу з поточною датою та часом
            SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault());
            String filename = "noise_logs_" + dateFormat.format(new Date()) + ".csv";
            
            // Створюємо CSV-вміст
            StringBuilder csvContent = new StringBuilder();
            // Додаємо заголовок з коментарем
            csvContent.append("# ").append(getString(R.string.csv_export_header)).append("\n");
            csvContent.append("# ").append(getString(R.string.csv_date_format_info)).append("\n");
            csvContent.append(getString(R.string.csv_column_headers)).append("\n");
            
            // Формати для відображення дати і часу
            SimpleDateFormat csvDateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            SimpleDateFormat csvTimeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            
            // Визначаємо локалізацію для форматування числа
            Locale currentLocale = Locale.getDefault();
            
            // Додаємо дані
            for (NoiseLogEntry entry : logs) {
                Date logDate = new Date(entry.getTimestamp());
                String date = csvDateFormat.format(logDate);
                String time = csvTimeFormat.format(logDate);
                
                // Форматуємо рівень шуму у відповідності до локалізації,
                // але обгортаємо значення в лапки, щоб CSV-файл не розділяв його на стовпці
                String noiseLevel;
                if (currentLocale.getLanguage().equals(Locale.ENGLISH.getLanguage())) {
                    // Для англійської мови використовуємо крапку
                    noiseLevel = String.format(Locale.US, "%.1f", entry.getNoiseLevel());
                } else {
                    // Для української використовуємо кому
                    noiseLevel = String.format(Locale.forLanguageTag("uk"), "%.1f", entry.getNoiseLevel());
                }
                
                // Обгортаємо в подвійні лапки, щоб гарантувати правильне розпізнавання в CSV
                csvContent.append(date).append(",")
                         .append(time).append(",")
                         .append("\"").append(noiseLevel).append("\"").append("\n");
            }
            
            // Зберігаємо CSV-файл у внутрішньому сховищі
            File outputDir = new File(getFilesDir(), "noise_logs");
            if (!outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            File outputFile = new File(outputDir, filename);
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(csvContent.toString().getBytes());
            }
            
            // Створюємо URI для файлу з використанням FileProvider
            Uri fileUri = FileProvider.getUriForFile(
                    this,
                    getApplicationContext().getPackageName() + ".fileprovider",
                    outputFile);
            
            // Створюємо intent для шерінгу
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/csv");
            shareIntent.putExtra(Intent.EXTRA_SUBJECT, getString(R.string.logs_title));
            shareIntent.putExtra(Intent.EXTRA_STREAM, fileUri);
            shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            
            // Відкриваємо діалог шерінгу
            startActivity(Intent.createChooser(shareIntent, getString(R.string.export_csv)));
            
        } catch (IOException e) {
            Log.e(TAG, "Помилка при експорті CSV", e);
            Utils.showToast(this, "Помилка при експорті: " + e.getMessage());
        } catch (Exception e) {
            Log.e(TAG, "Несподівана помилка при експорті CSV", e);
            Utils.showToast(this, "Помилка: " + e.getMessage());
        }
    }
    
    /**
     * Налаштування кнопки паузи/відновлення логування
     */
    private void setupLoggingPauseButton() {
        boolean loggingEnabled = NoiseLogManager.getInstance(this).isLoggingEnabled();
        boolean loggingPaused = NoiseLogManager.getInstance(this).isLoggingPaused();
        
        // Оновлюємо стан кнопки в залежності від стану логування
        updateLoggingPauseButton(loggingEnabled, loggingPaused);
        
        // Встановлюємо обробник кліку
        loggingPauseButton.setOnClickListener(v -> {
            // Переключаємо стан паузи
            boolean currentPauseState = NoiseLogManager.getInstance(this).isLoggingPaused();
            boolean newPauseState = !currentPauseState;
            NoiseLogManager.getInstance(this).setLoggingPaused(newPauseState);
            
            // Оновлюємо UI
            updateLoggingPauseButton(NoiseLogManager.getInstance(this).isLoggingEnabled(), newPauseState);
            
            // Показуємо повідомлення
            Utils.showToast(this, newPauseState ? 
                    getString(R.string.logging_paused) : 
                    getString(R.string.logging_resumed));
        });
    }
    
    /**
     * Оновлює стан кнопки паузи/відновлення
     */
    private void updateLoggingPauseButton(boolean loggingEnabled, boolean paused) {
        // Оновлюємо текст кнопки
        loggingPauseButton.setText(paused ? R.string.logging_resume : R.string.logging_pause);
        
        // Кнопка доступна тільки якщо логування увімкнено
        loggingPauseButton.setEnabled(loggingEnabled);
        
        // Оновлюємо видимість кнопки
        //loggingPauseButton.setVisibility(loggingEnabled ? View.VISIBLE : View.GONE);
    }
    
    /**
     * Адаптер для відображення логів у RecyclerView
     */
    private class NoiseLogAdapter extends RecyclerView.Adapter<NoiseLogAdapter.LogViewHolder> {
        private List<NoiseLogEntry> logs;
        private final SimpleDateFormat dateFormat;
        private final SimpleDateFormat timeFormat;
        
        public NoiseLogAdapter(List<NoiseLogEntry> logs) {
            this.logs = logs;
            this.dateFormat = new SimpleDateFormat("dd.MM.yyyy", Locale.getDefault());
            this.timeFormat = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
        }
        
        /**
         * Оновлює дані в адаптері
         * @param newLogs нові записи логу
         */
        public void updateData(List<NoiseLogEntry> newLogs) {
            this.logs = newLogs;
            notifyDataSetChanged();
        }
        
        @NonNull
        @Override
        public LogViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = getLayoutInflater().inflate(R.layout.item_noise_log, parent, false);
            return new LogViewHolder(view);
        }
        
        @Override
        public void onBindViewHolder(@NonNull LogViewHolder holder, int position) {
            NoiseLogEntry entry = logs.get(position);
            Date date = new Date(entry.getTimestamp());
            
            // Форматуємо дату і час
            String dateStr = dateFormat.format(date);
            String timeStr = timeFormat.format(date);
            
            // Встановлюємо значення
            holder.dateTimeView.setText(getString(R.string.log_date_time_format, dateStr, timeStr));
            holder.noiseLevelView.setText(getString(R.string.log_noise_value, entry.getNoiseLevel()));
        }
        
        @Override
        public int getItemCount() {
            return logs.size();
        }
        
        class LogViewHolder extends RecyclerView.ViewHolder {
            TextView dateTimeView;
            TextView noiseLevelView;
            
            LogViewHolder(@NonNull View itemView) {
                super(itemView);
                dateTimeView = itemView.findViewById(R.id.log_date_time);
                noiseLevelView = itemView.findViewById(R.id.log_noise_level);
            }
        }
    }
} 