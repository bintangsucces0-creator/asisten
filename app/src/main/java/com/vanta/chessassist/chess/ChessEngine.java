package com.vanta.chessassist.chess;

import java.util.List;

/**
 * Negamax alpha-beta search, depth 4.
 * Evaluation: material + piece-square tables (white-relative, mirrored for black).
 */
public class ChessEngine {

    private static final int DEPTH = 4;
    private static final int MATE  = 20000;

    private final MoveGenerator moveGen = new MoveGenerator();

    // ── Piece-square tables (white perspective, rank 1 = index 0) ────────────

    private static final int[] PST_PAWN = {
         0,  0,  0,  0,  0,  0,  0,  0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
         5,  5, 10, 25, 25, 10,  5,  5,
         0,  0,  0, 20, 20,  0,  0,  0,
         5, -5,-10,  0,  0,-10, -5,  5,
         5, 10, 10,-20,-20, 10, 10,  5,
         0,  0,  0,  0,  0,  0,  0,  0
    };

    private static final int[] PST_KNIGHT = {
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50
    };

    private static final int[] PST_BISHOP = {
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20
    };

    private static final int[] PST_ROOK = {
         0,  0,  0,  0,  0,  0,  0,  0,
         5, 10, 10, 10, 10, 10, 10,  5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
        -5,  0,  0,  0,  0,  0,  0, -5,
         0,  0,  0,  5,  5,  0,  0,  0
    };

    private static final int[] PST_QUEEN = {
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5,  5,  5,  5,  0,-10,
         -5,  0,  5,  5,  5,  5,  0, -5,
          0,  0,  5,  5,  5,  5,  0, -5,
        -10,  5,  5,  5,  5,  5,  0,-10,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20
    };

    private static final int[] PST_KING_MG = {
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -10,-20,-20,-20,-20,-20,-20,-10,
         20, 20,  0,  0,  0,  0, 20, 20,
         20, 30, 10,  0,  0, 10, 30, 20
    };

    private static final int[] PIECE_VALUE = {0, 100, 320, 330, 500, 900, 20000};

    // ── Public API ────────────────────────────────────────────────────────────

    public Move getBestMove(Board board) {
        List<Move> moves = moveGen.generateLegalMoves(board);
        if (moves.isEmpty()) return null;

        Move best = moves.get(0);
        int alpha = Integer.MIN_VALUE + 1;
        int beta  = Integer.MAX_VALUE;

        for (Move m : moves) {
            Board next = board.applyMove(m);
            int score = -negamax(next, DEPTH - 1, -beta, -alpha);
            if (score > alpha) { alpha = score; best = m; }
        }
        return best;
    }

    // ── Search ────────────────────────────────────────────────────────────────

    private int negamax(Board board, int depth, int alpha, int beta) {
        if (depth == 0) return evaluate(board);

        List<Move> moves = moveGen.generateLegalMoves(board);
        if (moves.isEmpty())
            return board.isInCheck(board.sideToMove) ? -(MATE + depth) : 0;

        for (Move m : moves) {
            Board next  = board.applyMove(m);
            int score   = -negamax(next, depth - 1, -beta, -alpha);
            if (score >= beta) return beta;
            alpha = Math.max(alpha, score);
        }
        return alpha;
    }

    // ── Evaluation (from sideToMove's perspective) ────────────────────────────

    private int evaluate(Board board) {
        int score = 0;
        for (int sq = 0; sq < 64; sq++) {
            int piece = board.squares[sq];
            if (piece == Piece.NONE) continue;
            int type  = Piece.type(piece);
            int color = Piece.color(piece);
            int sign  = (color == board.sideToMove) ? 1 : -1;

            // Mirror square for black so tables stay white-centric
            int tSq = (color == Piece.WHITE) ? sq : (7 - sq / 8) * 8 + (sq % 8);
            int pst  = pstValue(type, tSq);

            score += sign * (PIECE_VALUE[type] + pst);
        }
        return score;
    }

    private int pstValue(int type, int sq) {
        switch (type) {
            case Piece.PAWN:   return PST_PAWN[sq];
            case Piece.KNIGHT: return PST_KNIGHT[sq];
            case Piece.BISHOP: return PST_BISHOP[sq];
            case Piece.ROOK:   return PST_ROOK[sq];
            case Piece.QUEEN:  return PST_QUEEN[sq];
            case Piece.KING:   return PST_KING_MG[sq];
            default:           return 0;
        }
    }
}
