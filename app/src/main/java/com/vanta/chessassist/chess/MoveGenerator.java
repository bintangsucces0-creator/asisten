package com.vanta.chessassist.chess;

import java.util.ArrayList;
import java.util.List;

public class MoveGenerator {

    public List<Move> generateLegalMoves(Board board) {
        List<Move> pseudo = generatePseudoLegalMoves(board);
        List<Move> legal  = new ArrayList<>();
        for (Move m : pseudo) {
            Board after = board.applyMove(m);
            if (!after.isInCheck(board.sideToMove))
                legal.add(m);
        }
        return legal;
    }

    private List<Move> generatePseudoLegalMoves(Board board) {
        List<Move> moves = new ArrayList<>();
        int color = board.sideToMove;

        for (int sq = 0; sq < 64; sq++) {
            int piece = board.squares[sq];
            if (piece == Piece.NONE || Piece.color(piece) != color) continue;
            int r = sq / 8, f = sq % 8;

            switch (Piece.type(piece)) {
                case Piece.PAWN:   generatePawnMoves(board, sq, r, f, color, moves);                break;
                case Piece.KNIGHT: generateKnightMoves(board, sq, r, f, color, moves);              break;
                case Piece.BISHOP: generateSliding(board, sq, r, f, color, moves, true,  false);    break;
                case Piece.ROOK:   generateSliding(board, sq, r, f, color, moves, false, true);     break;
                case Piece.QUEEN:  generateSliding(board, sq, r, f, color, moves, true,  true);     break;
                case Piece.KING:   generateKingMoves(board, sq, r, f, color, moves);                break;
            }
        }
        return moves;
    }

    // ── Piece move generators ─────────────────────────────────────────────────

    private void generatePawnMoves(Board board, int sq, int rank, int file,
                                   int color, List<Move> moves) {
        int dir        = color == Piece.WHITE ? 1 : -1;
        int startRank  = color == Piece.WHITE ? 1 : 6;
        int promoRank  = color == Piece.WHITE ? 7 : 0;

        // Single push
        int fwd = sq + 8 * dir;
        if (fwd >= 0 && fwd < 64 && board.squares[fwd] == Piece.NONE) {
            addPawnMove(sq, fwd, fwd / 8, promoRank, moves);
            // Double push
            if (rank == startRank) {
                int dbl = sq + 16 * dir;
                if (board.squares[dbl] == Piece.NONE)
                    moves.add(new Move(sq, dbl));
            }
        }

        // Captures
        for (int cf : new int[]{file - 1, file + 1}) {
            if (cf < 0 || cf > 7) continue;
            int capSq = (rank + dir) * 8 + cf;
            if (capSq < 0 || capSq >= 64) continue;
            int target  = board.squares[capSq];
            boolean isEP = capSq == board.enPassantSquare;
            if ((target != Piece.NONE && Piece.color(target) != color) || isEP) {
                int flags = isEP ? Move.FLAG_EN_PASSANT : Move.FLAG_NORMAL;
                if (capSq / 8 == promoRank) {
                    for (int p : new int[]{Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT})
                        moves.add(new Move(sq, capSq, Move.FLAG_PROMOTION, p));
                } else {
                    moves.add(new Move(sq, capSq, flags, Piece.NONE));
                }
            }
        }
    }

    private void addPawnMove(int from, int to, int toRank, int promoRank, List<Move> moves) {
        if (toRank == promoRank) {
            for (int p : new int[]{Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT})
                moves.add(new Move(from, to, Move.FLAG_PROMOTION, p));
        } else {
            moves.add(new Move(from, to));
        }
    }

    private void generateKnightMoves(Board board, int sq, int rank, int file,
                                     int color, List<Move> moves) {
        int[][] d = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] delta : d) {
            int nr = rank + delta[0], nf = file + delta[1];
            if (nr < 0 || nr > 7 || nf < 0 || nf > 7) continue;
            int target = board.squares[nr * 8 + nf];
            if (target == Piece.NONE || Piece.color(target) != color)
                moves.add(new Move(sq, nr * 8 + nf));
        }
    }

    private void generateSliding(Board board, int sq, int rank, int file,
                                  int color, List<Move> moves, boolean diag, boolean straight) {
        int[][] dirs;
        if (diag && straight)  dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        else if (diag)         dirs = new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}};
        else                   dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};

        for (int[] d : dirs) {
            int nr = rank + d[0], nf = file + d[1];
            while (nr >= 0 && nr < 8 && nf >= 0 && nf < 8) {
                int target = board.squares[nr * 8 + nf];
                if (target == Piece.NONE) {
                    moves.add(new Move(sq, nr * 8 + nf));
                } else {
                    if (Piece.color(target) != color) moves.add(new Move(sq, nr * 8 + nf));
                    break;
                }
                nr += d[0]; nf += d[1];
            }
        }
    }

    private void generateKingMoves(Board board, int sq, int rank, int file,
                                   int color, List<Move> moves) {
        int[][] d = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int[] delta : d) {
            int nr = rank + delta[0], nf = file + delta[1];
            if (nr < 0 || nr > 7 || nf < 0 || nf > 7) continue;
            int target = board.squares[nr * 8 + nf];
            if (target == Piece.NONE || Piece.color(target) != color)
                moves.add(new Move(sq, nr * 8 + nf));
        }

        // Castling
        int opp = color == Piece.WHITE ? Piece.BLACK : Piece.WHITE;
        if (!board.isInCheck(color)) {
            if (color == Piece.WHITE) {
                if ((board.castlingRights & 1) != 0
                        && board.squares[5] == Piece.NONE && board.squares[6] == Piece.NONE
                        && !board.isSquareAttacked(5, opp) && !board.isSquareAttacked(6, opp))
                    moves.add(new Move(4, 6, Move.FLAG_CASTLE, Piece.NONE));
                if ((board.castlingRights & 2) != 0
                        && board.squares[1] == Piece.NONE && board.squares[2] == Piece.NONE
                        && board.squares[3] == Piece.NONE
                        && !board.isSquareAttacked(2, opp) && !board.isSquareAttacked(3, opp))
                    moves.add(new Move(4, 2, Move.FLAG_CASTLE, Piece.NONE));
            } else {
                if ((board.castlingRights & 4) != 0
                        && board.squares[61] == Piece.NONE && board.squares[62] == Piece.NONE
                        && !board.isSquareAttacked(61, opp) && !board.isSquareAttacked(62, opp))
                    moves.add(new Move(60, 62, Move.FLAG_CASTLE, Piece.NONE));
                if ((board.castlingRights & 8) != 0
                        && board.squares[57] == Piece.NONE && board.squares[58] == Piece.NONE
                        && board.squares[59] == Piece.NONE
                        && !board.isSquareAttacked(58, opp) && !board.isSquareAttacked(59, opp))
                    moves.add(new Move(60, 58, Move.FLAG_CASTLE, Piece.NONE));
            }
        }
    }
}
