package com.vanta.chessassist.overlay;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

/**
 * Full-screen transparent overlay that draws a chess move arrow
 * from one board square to another.
 *
 * Board coordinates are supplied externally (boardLeft, boardTop, boardSize)
 * because the view has no way to know where Chess.com placed the board.
 */
public class ArrowOverlayView extends View {

    private final Paint arrowPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private int fromSquare = -1;
    private int toSquare   = -1;
    private int boardLeft, boardTop, boardSize;

    public ArrowOverlayView(Context ctx) { super(ctx); init(); }
    public ArrowOverlayView(Context ctx, AttributeSet a) { super(ctx, a); init(); }

    private void init() {
        arrowPaint.setColor(Color.argb(210, 0, 185, 65));
        arrowPaint.setStyle(Paint.Style.FILL);

        circlePaint.setColor(Color.argb(180, 255, 220, 0));
        circlePaint.setStyle(Paint.Style.STROKE);
    }

    // ── Public control ────────────────────────────────────────────────────────

    public void setMove(int from, int to, int boardLeft, int boardTop, int boardSize) {
        this.fromSquare = from;
        this.toSquare   = to;
        this.boardLeft  = boardLeft;
        this.boardTop   = boardTop;
        this.boardSize  = boardSize;
        postInvalidate();
    }

    public void clearMove() {
        fromSquare = -1;
        toSquare   = -1;
        postInvalidate();
    }

    // ── Drawing ───────────────────────────────────────────────────────────────

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (fromSquare < 0 || toSquare < 0 || boardSize == 0) return;

        float cell = (float) boardSize / 8f;
        float[] from = squareCenter(fromSquare, cell);
        float[] to   = squareCenter(toSquare,   cell);
        float width  = cell * 0.14f;

        drawArrow(canvas, from[0], from[1], to[0], to[1], width);

        circlePaint.setStrokeWidth(width * 0.45f);
        canvas.drawCircle(from[0], from[1], width * 1.6f, circlePaint);
    }

    private float[] squareCenter(int sq, float cell) {
        int file = sq % 8;
        int rank = sq / 8;
        float x = boardLeft + file * cell + cell / 2f;
        float y = boardTop  + (7 - rank) * cell + cell / 2f;
        return new float[]{x, y};
    }

    /**
     * Draws a filled arrow from (x1,y1) → (x2,y2) with given half-width.
     * The arrowhead is an equilateral triangle; the shaft is a rectangle.
     */
    private void drawArrow(Canvas canvas, float x1, float y1, float x2, float y2, float w) {
        float dx = x2 - x1, dy = y2 - y1;
        float len = (float) Math.sqrt(dx * dx + dy * dy);
        if (len < 1f) return;

        float ux = dx / len, uy = dy / len; // unit along arrow
        float px = -uy,      py =  ux;      // unit perpendicular

        float headLen  = w * 2.8f;
        float headBase = w * 1.8f;

        // Shaft rectangle end (before head starts)
        float sx2 = x2 - ux * headLen;
        float sy2 = y2 - uy * headLen;

        Path path = new Path();
        // Shaft
        path.moveTo(x1 + px * w,  y1 + py * w);
        path.lineTo(sx2 + px * w, sy2 + py * w);
        // Right wing of head
        path.lineTo(sx2 + px * headBase, sy2 + py * headBase);
        // Tip
        path.lineTo(x2, y2);
        // Left wing of head
        path.lineTo(sx2 - px * headBase, sy2 - py * headBase);
        // Back along shaft
        path.lineTo(sx2 - px * w, sy2 - py * w);
        path.lineTo(x1 - px * w,  y1 - py * w);
        path.close();

        canvas.drawPath(path, arrowPaint);
    }
}
