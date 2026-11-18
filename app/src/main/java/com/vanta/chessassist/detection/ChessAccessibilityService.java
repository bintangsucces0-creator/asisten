package com.vanta.chessassist.detection;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.vanta.chessassist.chess.Board;
import com.vanta.chessassist.chess.ChessEngine;
import com.vanta.chessassist.chess.Move;
import com.vanta.chessassist.chess.Piece;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads the Chess.com board state from the accessibility tree.
 *
 * Chess.com (web and app) exposes pieces with ARIA content descriptions
 * matching patterns like:
 *   "White Pawn at e2"  |  "black-king e8"  |  "wP e2"
 *
 * When board state changes, runs the engine on a background thread and
 * broadcasts the best move to OverlayService via local intent.
 */
public class ChessAccessibilityService extends AccessibilityService {

    public static final String ACTION_BOARD_UPDATED = "com.vanta.chessassist.BOARD_UPDATED";
    public static final String EXTRA_BEST_MOVE      = "best_move";
    public static final String EXTRA_FEN            = "fen";
    public static final String EXTRA_SIDE           = "side_to_move";

    // Chess.com app + major Android browsers
    private static final String[] TARGETS = {
        "com.chess",
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.microsoft.emmx",
        "com.brave.browser",
        "com.opera.browser"
    };

    // "white pawn e2", "black king at e8", "wp e2", "white-pawn-e2", etc.
    private static final Pattern SQ_PATTERN = Pattern.compile("\\b([a-h][1-8])\\b");

    private final ChessEngine engine       = new ChessEngine();
    private volatile String   lastFen      = "";
    private volatile boolean  analyzing    = false;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Override
    protected void onServiceConnected() {
        AccessibilityServiceInfo info = new AccessibilityServiceInfo();
        info.eventTypes       = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
                              | AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED;
        info.feedbackType     = AccessibilityServiceInfo.FEEDBACK_GENERIC;
        info.flags            = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
                              | AccessibilityServiceInfo.FLAG_REQUEST_ENHANCED_WEB_ACCESSIBILITY;
        info.notificationTimeout = 150;
        setServiceInfo(info);
    }

    @Override
    public void onInterrupt() { /* no-op */ }

    // ── Event handler ─────────────────────────────────────────────────────────

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || analyzing) return;

        CharSequence pkg = event.getPackageName();
        if (pkg == null || !isTargetPackage(pkg.toString())) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        try {
            int[] board = new int[64];
            int[] sidePref = {Piece.WHITE}; // default; override if detectable
            walkTree(root, board, sidePref);

            if (countPieces(board) >= 4) {
                String fen = buildFen(board, sidePref[0]);
                if (!fen.equals(lastFen)) {
                    lastFen = fen;
                    scheduleAnalysis(fen);
                }
            }
        } finally {
            root.recycle();
        }
    }

    // ── Tree walk ─────────────────────────────────────────────────────────────

    private void walkTree(AccessibilityNodeInfo node, int[] board, int[] sidePref) {
        if (node == null) return;

        // Check content description and text
        CharSequence cd = node.getContentDescription();
        CharSequence tx = node.getText();
        if (cd != null) parsePiece(cd.toString(), board, sidePref);
        if (tx != null) parsePiece(tx.toString(), board, sidePref);

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                walkTree(child, board, sidePref);
                child.recycle();
            }
        }
    }

    // ── Piece parser ──────────────────────────────────────────────────────────

    /**
     * Accepts common Chess.com accessibility description formats:
     *   "White Pawn at e2"
     *   "black king on e8"
     *   "white-bishop-f1"
     *   "wP e2" / "bK e8"   (compact notation)
     *   "White to move" → side hint
     */
    private void parsePiece(String raw, int[] board, int[] sidePref) {
        if (raw == null || raw.length() < 2) return;
        String s = raw.toLowerCase().replace('-', ' ').replace('_', ' ');

        // Side-to-move hint
        if (s.contains("white to move")) { sidePref[0] = Piece.WHITE; return; }
        if (s.contains("black to move")) { sidePref[0] = Piece.BLACK; return; }

        // Color
        int color;
        if      (s.startsWith("w") || s.contains("white")) color = Piece.WHITE;
        else if (s.startsWith("b") || s.contains("black")) color = Piece.BLACK;
        else return;

        // Type
        int type;
        if      (s.contains("pawn"))   type = Piece.PAWN;
        else if (s.contains("knight")) type = Piece.KNIGHT;
        else if (s.contains("bishop")) type = Piece.BISHOP;
        else if (s.contains("rook"))   type = Piece.ROOK;
        else if (s.contains("queen"))  type = Piece.QUEEN;
        else if (s.contains("king"))   type = Piece.KING;
        else {
            // Try compact: second char is piece letter
            if (raw.length() >= 2) {
                char p = Character.toLowerCase(raw.charAt(1));
                switch (p) {
                    case 'p': type = Piece.PAWN;   break;
                    case 'n': type = Piece.KNIGHT; break;
                    case 'b': type = Piece.BISHOP; break;
                    case 'r': type = Piece.ROOK;   break;
                    case 'q': type = Piece.QUEEN;  break;
                    case 'k': type = Piece.KING;   break;
                    default:  return;
                }
            } else return;
        }

        // Square
        Matcher m = SQ_PATTERN.matcher(s);
        if (!m.find()) return;
        int sq = Move.squareIndex(m.group(1));
        if (sq >= 0 && sq < 64)
            board[sq] = Piece.make(color, type);
    }

    // ── FEN builder ───────────────────────────────────────────────────────────

    private String buildFen(int[] board, int sideToMove) {
        StringBuilder sb = new StringBuilder();
        for (int r = 7; r >= 0; r--) {
            int empty = 0;
            for (int f = 0; f < 8; f++) {
                int p = board[r * 8 + f];
                if (p == Piece.NONE) { empty++; }
                else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    sb.append(Piece.toChar(p));
                }
            }
            if (empty > 0) sb.append(empty);
            if (r > 0) sb.append('/');
        }
        sb.append(' ').append(sideToMove == Piece.WHITE ? 'w' : 'b')
          .append(" - - 0 1");
        return sb.toString();
    }

    // ── Analysis thread ───────────────────────────────────────────────────────

    private void scheduleAnalysis(final String fen) {
        analyzing = true;
        new Thread(() -> {
            try {
                Board b    = new Board(fen);
                Move best  = engine.getBestMove(b);
                if (best != null) {
                    Intent intent = new Intent(ACTION_BOARD_UPDATED);
                    intent.setPackage(getPackageName());
                    intent.putExtra(EXTRA_BEST_MOVE, best.toAlgebraic());
                    intent.putExtra(EXTRA_FEN, fen);
                    intent.putExtra(EXTRA_SIDE, b.sideToMove == Piece.WHITE ? "white" : "black");
                    sendBroadcast(intent);
                }
            } catch (Exception ignored) {
            } finally {
                analyzing = false;
            }
        }).start();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private boolean isTargetPackage(String pkg) {
        for (String t : TARGETS) if (t.equals(pkg)) return true;
        return false;
    }

    private int countPieces(int[] board) {
        int n = 0;
        for (int p : board) if (p != Piece.NONE) n++;
        return n;
    }
}
