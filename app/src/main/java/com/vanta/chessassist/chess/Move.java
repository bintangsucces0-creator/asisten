package com.vanta.chessassist.chess;

public class Move {
    public final int from;
    public final int to;
    public final int promotion;
    public final int flags;

    public static final int FLAG_NORMAL     = 0;
    public static final int FLAG_EN_PASSANT = 1;
    public static final int FLAG_CASTLE     = 2;
    public static final int FLAG_PROMOTION  = 4;

    public Move(int from, int to) {
        this.from = from; this.to = to;
        this.promotion = Piece.NONE; this.flags = FLAG_NORMAL;
    }

    public Move(int from, int to, int flags, int promotion) {
        this.from = from; this.to = to;
        this.flags = flags; this.promotion = promotion;
    }

    public String toAlgebraic() {
        String f = squareName(from), t = squareName(to);
        if ((flags & FLAG_PROMOTION) != 0) {
            char p;
            switch (promotion) {
                case Piece.QUEEN:  p = 'q'; break;
                case Piece.ROOK:   p = 'r'; break;
                case Piece.BISHOP: p = 'b'; break;
                default:           p = 'n'; break;
            }
            return f + t + p;
        }
        return f + t;
    }

    public static String squareName(int sq) {
        char file = (char)('a' + (sq % 8));
        char rank = (char)('1' + (sq / 8));
        return "" + file + rank;
    }

    public static int squareIndex(String name) {
        int file = name.charAt(0) - 'a';
        int rank = name.charAt(1) - '1';
        return rank * 8 + file;
    }

    @Override public String toString() { return toAlgebraic(); }
}
