package com.vanta.chessassist.chess;

public class Piece {
    public static final int NONE   = 0;
    public static final int PAWN   = 1;
    public static final int KNIGHT = 2;
    public static final int BISHOP = 3;
    public static final int ROOK   = 4;
    public static final int QUEEN  = 5;
    public static final int KING   = 6;

    public static final int WHITE = 8;
    public static final int BLACK = 16;

    public static int type(int piece)      { return piece & 7; }
    public static int color(int piece)     { return piece & 24; }
    public static boolean isWhite(int p)   { return (p & WHITE) != 0; }
    public static boolean isBlack(int p)   { return (p & BLACK) != 0; }
    public static int make(int color, int type) { return color | type; }

    public static String toChar(int piece) {
        int t = type(piece);
        char c;
        switch (t) {
            case PAWN:   c = 'p'; break;
            case KNIGHT: c = 'n'; break;
            case BISHOP: c = 'b'; break;
            case ROOK:   c = 'r'; break;
            case QUEEN:  c = 'q'; break;
            case KING:   c = 'k'; break;
            default: return ".";
        }
        return isWhite(piece) ? String.valueOf(Character.toUpperCase(c)) : String.valueOf(c);
    }
}
