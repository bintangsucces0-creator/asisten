package com.vanta.chessassist;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.vanta.chessassist.overlay.OverlayService;

/**
 * Entry point. Handles:
 *  1. SYSTEM_ALERT_WINDOW permission request
 *  2. Start / stop OverlayService
 *  3. Status display + accessibility service hint
 */
public class MainActivity extends Activity {

    private static final int REQ_OVERLAY = 1001;

    private TextView tvStatus;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tv_status);

        Button btnStart = findViewById(R.id.btn_start);
        Button btnStop  = findViewById(R.id.btn_stop);

        btnStart.setOnClickListener(v -> handleStart());
        btnStop.setOnClickListener(v -> {
            stopService(new Intent(this, OverlayService.class));
            setStatus("Stopped", "#FF5555");
        });

        // Deep-link to Accessibility Settings
        Button btnAccess = findViewById(R.id.btn_accessibility);
        if (btnAccess != null) {
            btnAccess.setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateStatusFromState();
    }

    // ── Permission flow ───────────────────────────────────────────────────────

    private void handleStart() {
        if (!Settings.canDrawOverlays(this)) {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + getPackageName()));
            startActivityForResult(i, REQ_OVERLAY);
            setStatus("Grant 'Display over other apps' permission…", "#FFA500");
        } else {
            launchOverlay();
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == REQ_OVERLAY) {
            if (Settings.canDrawOverlays(this)) launchOverlay();
            else setStatus("Permission denied — cannot show overlay", "#FF5555");
        }
    }

    private void launchOverlay() {
        Intent svc = new Intent(this, OverlayService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(svc);
        else
            startService(svc);
        setStatus("Running — open Chess.com", "#4CAF50");
    }

    // ── Status helpers ────────────────────────────────────────────────────────

    private void updateStatusFromState() {
        if (!Settings.canDrawOverlays(this))
            setStatus("Needs overlay permission", "#FFA500");
        else
            setStatus("Ready", "#4CAF50");
    }

    private void setStatus(String text, String hexColor) {
        if (tvStatus == null) return;
        tvStatus.setText("Status: " + text);
        try { tvStatus.setTextColor(android.graphics.Color.parseColor(hexColor)); }
        catch (Exception ignored) {}
    }
}
