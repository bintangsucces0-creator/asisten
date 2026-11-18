package com.vanta.chessassist.overlay;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.core.app.NotificationCompat;

import com.vanta.chessassist.MainActivity;
import com.vanta.chessassist.R;
import com.vanta.chessassist.chess.Move;
import com.vanta.chessassist.detection.ChessAccessibilityService;

/**
 * Foreground service that:
 *  1. Keeps a full-screen transparent ArrowOverlayView on top of all apps.
 *  2. Shows a small draggable control panel with the suggested move.
 *  3. Listens for board-updated broadcasts from ChessAccessibilityService.
 */
public class OverlayService extends Service {

    private static final String CHANNEL_ID = "chess_overlay_channel";
    private static final int    NOTIF_ID   = 42;

    private WindowManager windowManager;
    private ArrowOverlayView arrowView;
    private View             controlView;
    private WindowManager.LayoutParams arrowParams;
    private WindowManager.LayoutParams controlParams;

    // ── Board update receiver ─────────────────────────────────────────────────

    private final BroadcastReceiver boardReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context ctx, Intent intent) {
            String move = intent.getStringExtra(ChessAccessibilityService.EXTRA_BEST_MOVE);
            String fen  = intent.getStringExtra(ChessAccessibilityService.EXTRA_FEN);
            if (move != null) onBestMove(move, fen);
        }
    };

    // ── Service lifecycle ─────────────────────────────────────────────────────

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        setupArrowOverlay();
        setupControlPanel();
        IntentFilter filter = new IntentFilter(ChessAccessibilityService.ACTION_BOARD_UPDATED);
        registerReceiver(boardReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        try { unregisterReceiver(boardReceiver); } catch (Exception ignored) {}
        if (arrowView   != null) { try { windowManager.removeView(arrowView);   } catch (Exception ignored) {} }
        if (controlView != null) { try { windowManager.removeView(controlView); } catch (Exception ignored) {} }
        super.onDestroy();
    }

    // ── Overlay setup ─────────────────────────────────────────────────────────

    private void setupArrowOverlay() {
        arrowView = new ArrowOverlayView(this);

        int type = overlayWindowType();
        arrowParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        );
        windowManager.addView(arrowView, arrowParams);
    }

    private void setupControlPanel() {
        controlView = LayoutInflater.from(this).inflate(R.layout.overlay_control, null);

        controlParams = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        );
        controlParams.gravity = Gravity.TOP | Gravity.START;
        controlParams.x = 24;
        controlParams.y = 120;

        makeDraggable(controlView);

        ImageButton btnClose = controlView.findViewById(R.id.btn_close);
        if (btnClose != null) btnClose.setOnClickListener(v -> stopSelf());

        ImageButton btnClear = controlView.findViewById(R.id.btn_clear);
        if (btnClear != null) btnClear.setOnClickListener(v -> {
            arrowView.clearMove();
            setMoveLabel("Waiting…");
        });

        windowManager.addView(controlView, controlParams);
    }

    // ── Move display ──────────────────────────────────────────────────────────

    private void onBestMove(String move, String fen) {
        controlView.post(() -> setMoveLabel("▶ " + move.toUpperCase()));

        if (move.length() >= 4) {
            try {
                int from = Move.squareIndex(move.substring(0, 2));
                int to   = Move.squareIndex(move.substring(2, 4));

                DisplayMetrics dm = getResources().getDisplayMetrics();
                int sw = dm.widthPixels;
                int sh = dm.heightPixels;
                // Heuristic board rect — centred, occupying ~60 % of the shorter axis.
                // The user can reposition the control panel; arrow repositioning is in
                // the board calibration activity (future extension point).
                int boardSize = (int)(Math.min(sw, sh) * 0.90f);
                int boardLeft = (sw - boardSize) / 2;
                int boardTop  = (sh - boardSize) / 2;

                arrowView.post(() -> arrowView.setMove(from, to, boardLeft, boardTop, boardSize));
            } catch (Exception ignored) {}
        }
    }

    private void setMoveLabel(String text) {
        TextView tv = controlView.findViewById(R.id.tv_move);
        if (tv != null) tv.setText(text);
    }

    // ── Drag handling ─────────────────────────────────────────────────────────

    private void makeDraggable(View view) {
        view.setOnTouchListener(new View.OnTouchListener() {
            int ix, iy, tx, ty;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        ix = controlParams.x; iy = controlParams.y;
                        tx = (int) e.getRawX(); ty = (int) e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        controlParams.x = ix + (int)(e.getRawX() - tx);
                        controlParams.y = iy + (int)(e.getRawY() - ty);
                        windowManager.updateViewLayout(controlView, controlParams);
                        return true;
                }
                return false;
            }
        });
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private Notification buildNotification() {
        Intent tap = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, tap,
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Chess Assistant")
            .setContentText("Overlay active — open Chess.com")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "Chess Overlay Service", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("Keeps the move-suggestion overlay running");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private int overlayWindowType() {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;
    }
}
