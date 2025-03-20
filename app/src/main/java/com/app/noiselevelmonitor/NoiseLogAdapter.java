package com.app.noiselevelmonitor;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Адаптер для відображення списку логів шуму
 */
public class NoiseLogAdapter extends RecyclerView.Adapter<NoiseLogAdapter.ViewHolder> {

    private List<NoiseLogEntry> logEntries;
    private Context context;
    private final SimpleDateFormat dateFormat;
    private final SimpleDateFormat timeFormat;

    /**
     * Створює новий адаптер для логів
     * @param logEntries список записів логу
     */
    public NoiseLogAdapter(List<NoiseLogEntry> logEntries) {
        this.logEntries = logEntries;
        
        // Формати дати і часу
        Locale locale = Locale.getDefault();
        this.dateFormat = new SimpleDateFormat("dd.MM.yyyy", locale);
        this.timeFormat = new SimpleDateFormat("HH:mm:ss", locale);
    }

    /**
     * Створює новий адаптер для логів
     * @param context контекст
     * @param logEntries список записів логу
     */
    public NoiseLogAdapter(Context context, List<NoiseLogEntry> logEntries) {
        this.context = context;
        this.logEntries = logEntries;
        
        // Формати дати і часу
        // Використовуємо мову системи для правильного відображення дати
        Locale locale = Locale.getDefault();
        this.dateFormat = new SimpleDateFormat("dd.MM.yyyy", locale);
        this.timeFormat = new SimpleDateFormat("HH:mm:ss", locale);
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (context == null) {
            context = parent.getContext();
        }
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_noise_log, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        NoiseLogEntry entry = logEntries.get(position);
        
        // Форматуємо дату і час
        String date = dateFormat.format(new Date(entry.getTimestamp()));
        String time = timeFormat.format(new Date(entry.getTimestamp()));
        
        // Форматуємо рівень шуму
        String noiseLevel = String.format(Locale.getDefault(), "%.1f", entry.getNoiseLevel());
        
        // Встановлюємо дату і час у TextView
        holder.dateTimeTextView.setText(
                String.format("%s at %s", date, time));
        
        // Встановлюємо рівень шуму у TextView
        holder.noiseLevelTextView.setText(
                String.format("%.1f dB", entry.getNoiseLevel()));
    }

    @Override
    public int getItemCount() {
        return logEntries.size();
    }

    /**
     * Оновлює дані в адаптері
     * @param newEntries нові записи логу
     */
    public void updateData(List<NoiseLogEntry> newEntries) {
        this.logEntries = newEntries;
        notifyDataSetChanged();
    }

    /**
     * ViewHolder для елементу списку логів
     */
    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView dateTimeTextView;
        final TextView noiseLevelTextView;

        ViewHolder(View itemView) {
            super(itemView);
            dateTimeTextView = itemView.findViewById(R.id.log_date_time);
            noiseLevelTextView = itemView.findViewById(R.id.log_noise_level);
        }
    }
} 