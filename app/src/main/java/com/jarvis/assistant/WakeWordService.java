package com.jarvis.assistant;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

/**
 * JARVIS background wake service.
 *
 * SpeechRecognizer is used as a simple wake-phrase detector. It is not a
 * dedicated low-power hotword engine, so continuous recognition can consume
 * battery and may behave differently across Android/OEM speech services.
 * Start this service from the visible app after microphone permission is granted.
 */
public class WakeWordService extends Service {
    private static final String CHANNEL_ID = "jarvis_wake_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String WAKE_PHRASE = "hey jarvis";
    private SpeechRecognizer recognizer;
    private final Handler handler = new Handler();
    private boolean commandMode = false;
    private boolean stopping = false;

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startWakeForeground();
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel c = new NotificationChannel(
                    CHANNEL_ID, "JARVIS Wake Word", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Keeps JARVIS ready for the Hey JARVIS wake phrase.");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(c);
        }
    }

    private void startWakeForeground() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, open, PendingIntent.FLAG_UPDATE_CURRENT |
                        (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));

        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        Notification n = b.setContentTitle("JARVIS is listening")
                .setContentText("Waiting for “Hey JARVIS”")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, n);
        }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            stopSelf();
            return START_NOT_STICKY;
        }
        stopping = false;
        startListening(false);
        return START_STICKY;
    }

    private void startListening(boolean command) {
        if (stopping || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        commandMode = command;
        handler.post(() -> {
            destroyRecognizer();
            if (!SpeechRecognizer.isRecognitionAvailable(this)) {
                notifyWake("Speech recognition is not available on this phone.", false);
                stopSelf();
                return;
            }

            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(Bundle params) {}
                @Override public void onBeginningOfSpeech() {}
                @Override public void onRmsChanged(float rmsdB) {}
                @Override public void onBufferReceived(byte[] buffer) {}
                @Override public void onEndOfSpeech() {}
                @Override public void onPartialResults(Bundle partialResults) {}
                @Override public void onEvent(int eventType, Bundle params) {}

                @Override public void onResults(Bundle results) {
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String heard = matches != null && !matches.isEmpty() ? matches.get(0) : "";
                    handleSpeech(heard);
                }

                @Override public void onError(int error) {
                    if (!stopping) scheduleRestart(commandMode ? true : false, 350);
                }
            });

            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
            recognizer.startListening(i);
        });
    }

    private void handleSpeech(String heard) {
        String text = heard == null ? "" : heard.trim();
        String lower = text.toLowerCase(Locale.ROOT);

        if (!commandMode) {
            int p = lower.indexOf(WAKE_PHRASE);
            if (p >= 0) {
                String command = text.substring(Math.min(text.length(), p + WAKE_PHRASE.length())).trim();
                notifyWake("Hey JARVIS detected. Listening for your command…", true);
                if (!command.isEmpty()) {
                    deliverCommand(command);
                    scheduleRestart(false, 500);
                } else {
                    startListening(true);
                }
            } else {
                scheduleRestart(false, 250);
            }
            return;
        }

        if (!text.isEmpty()) {
            deliverCommand(text);
            notifyWake("Command received.", false);
        }
        scheduleRestart(false, 500);
    }

    private void deliverCommand(String command) {
        Intent i = new Intent(this, MainActivity.class);
        i.putExtra("WAKE_COMMAND", command);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
            startActivity(i);
        } catch (Exception ignored) {
            notifyWake("Command: " + command, false);
        }
    }

    private void notifyWake(String text, boolean wake) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (nm == null) return;
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 1, open, PendingIntent.FLAG_UPDATE_CURRENT |
                (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0));
        Notification.Builder b = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        Notification n = b.setContentTitle(wake ? "JARVIS activated" : "JARVIS is listening")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .setContentIntent(pi)
                .build();
        nm.notify(NOTIFICATION_ID, n);
    }

    private void scheduleRestart(boolean command, long delayMs) {
        if (stopping) return;
        handler.postDelayed(() -> startListening(command), delayMs);
    }

    private void destroyRecognizer() {
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) {}
            try { recognizer.destroy(); } catch (Exception ignored) {}
            recognizer = null;
        }
    }

    @Override public void onDestroy() {
        stopping = true;
        handler.removeCallbacksAndMessages(null);
        destroyRecognizer();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
