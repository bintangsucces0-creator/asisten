package com.vanta.chessassist.chess;

public class Board {
    public int[] squares = new int[64]; // sq 0=a1 … 63=h8
    public int sideToMove;
    public int castlingRights;    // bits: 0=WK 1=WQ 2=BK 3=BQ
    public int enPassantSquare;   // -1 if none
    public int halfMoveClock;
    public int fullMoveNumber;

    public static final String STARTING_FEN =
        "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public Board()            { loadFen(STARTING_FEN); }
    public Board(String fen)  { loadFen(fen); }

    // ── FEN parsing ──────────────────────────────────────────────────────────

    public void loadFen(String fen) {
        squares = new int[64];
        String[] parts = fen.split(" ");

        String[] ranks = parts[0].split("/");
        for (int r = 0; r < 8; r++) {
            String rank = ranks[7 - r];
            int file = 0;
            for (char c : rank.toCharArray()) {
                if (Character.isDigit(c)) {
                    file += c - '0';
                } else {
                    int color = Character.isUpperCase(c) ? Piece.WHITE : Piece.BLACK;
                    int type;
                    switch (Character.toLowerCase(c)) {
                        case 'p': type = Piece.PAWN;   break;
                        case 'n': type = Piece.KNIGHT; break;
                        case 'b': type = Piece.BISHOP; break;
                        case 'r': type = Piece.ROOK;   break;
                        case 'q': type = Piece.QUEEN;  break;
                        case 'k': type = Piece.KING;   break;
                        default:  type = Piece.NONE;
                    }
                    squares[r * 8 + file] = Piece.make(color, type);
                    file++;
                }
            }
        }

        sideToMove = (parts.length > 1 && parts[1].equals("b")) ? Piece.BLACK : Piece.WHITE;

        castlingRights = 0;
        if (parts.length > 2) {
            String cr = parts[2];
            if (cr.contains("K")) castlingRights |= 1;
            if (cr.contains("Q")) castlingRights |= 2;
            if (cr.contains("k")) castlingRights |= 4;
            if (cr.contains("q")) castlingRights |= 8;
        }

        enPassantSquare = -1;
        if (parts.length > 3 && !parts[3].equals("-"))
            enPassantSquare = Move.squareIndex(parts[3]);

        halfMoveClock  = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        fullMoveNumber = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int r = 7; r >= 0; r--) {
            int empty = 0;
            for (int f = 0; f < 8; f++) {
                int piece = squares[r * 8 + f];
                if (piece == Piece.NONE) { empty++; }
                else {
                    if (empty > 0) { sb.append(empty); empty = 0; }
                    sb.append(Piece.toChar(piece));
                }
            }
            if (empty > 0) sb.append(empty);
            if (r > 0) sb.append('/');
        }
        sb.append(' ').append(sideToMove == Piece.WHITE ? 'w' : 'b').append(' ');
        if (castlingRights == 0) sb.append('-');
        else {
            if ((castlingRights & 1) != 0) sb.append('K');
            if ((castlingRights & 2) != 0) sb.append('Q');
            if ((castlingRights & 4) != 0) sb.append('k');
            if ((castlingRights & 8) != 0) sb.append('q');
        }
        sb.append(' ').append(enPassantSquare == -1 ? "-" : Move.squareName(enPassantSquare));
        sb.append(' ').append(halfMoveClock).append(' ').append(fullMoveNumber);
        return sb.toString();
    }

    // ── State management ─────────────────────────────────────────────────────

    public Board copy() {
        Board b = new Board();
        b.squares       = squares.clone();
        b.sideToMove    = sideToMove;
        b.castlingRights = castlingRights;
        b.enPassantSquare = enPassantSquare;
        b.halfMoveClock = halfMoveClock;
        b.fullMoveNumber = fullMoveNumber;
        return b;
    }

    public Board applyMove(Move move) {
        Board b = this.copy();
        int piece    = b.squares[move.from];
        int captured = b.squares[move.to];

        b.squares[move.to]   = piece;
        b.squares[move.from] = Piece.NONE;

        // En passant capture
        if ((move.flags & Move.FLAG_EN_PASSANT) != 0) {
            int epCapSq = enPassantSquare + (sideToMove == Piece.WHITE ? -8 : 8);
            b.squares[epCapSq] = Piece.NONE;
        }

        // Castling — move rook
        if ((move.flags & Move.FLAG_CASTLE) != 0) {
            if      (move.to == 6)  { b.squares[5]  = b.squares[7];  b.squares[7]  = Piece.NONE; }
            else if (move.to == 2)  { b.squares[3]  = b.squares[0];  b.squares[0]  = Piece.NONE; }
            else if (move.to == 62) { b.squares[61] = b.squares[63]; b.squares[63] = Piece.NONE; }
            else if (move.to == 58) { b.squares[59] = b.squares[56]; b.squares[56] = Piece.NONE; }
        }

        // Promotion
        if ((move.flags & Move.FLAG_PROMOTION) != 0)
            b.squares[move.to] = Piece.make(sideToMove, move.promotion);

        // Castling rights
        if (Piece.type(piece) == Piece.KING) {
            if (sideToMove == Piece.WHITE) b.castlingRights &= ~3;
            else                           b.castlingRights &= ~12;
        }
        if (move.from == 0  || move.to == 0)  b.castlingRights &= ~2;
        if (move.from == 7  || move.to == 7)  b.castlingRights &= ~1;
        if (move.from == 56 || move.to == 56) b.castlingRights &= ~8;
        if (move.from == 63 || move.to == 63) b.castlingRights &= ~4;

        // En passant square
        b.enPassantSquare = -1;
        if (Piece.type(piece) == Piece.PAWN && Math.abs(move.to - move.from) == 16)
            b.enPassantSquare = (move.from + move.to) / 2;

        if (Piece.type(piece) == Piece.PAWN || captured != Piece.NONE)
            b.halfMoveClock = 0;
        else
            b.halfMoveClock++;

        if (sideToMove == Piece.BLACK) b.fullMoveNumber++;
        b.sideToMove = (sideToMove == Piece.WHITE) ? Piece.BLACK : Piece.WHITE;
        return b;
    }

    // ── Attack detection ──────────────────────────────────────────────────────

    public int getKingSquare(int color) {
        for (int i = 0; i < 64; i++)
            if (squares[i] == Piece.make(color, Piece.KING)) return i;
        return -1;
    }

    public boolean isSquareAttacked(int sq, int byColor) {
        int rank = sq / 8, file = sq % 8;

        // Pawns
        int pDir = byColor == Piece.WHITE ? -1 : 1;
        int aRank = rank + pDir;
        if (aRank >= 0 && aRank < 8) {
            if (file > 0 && squares[aRank*8 + file-1] == Piece.make(byColor, Piece.PAWN)) return true;
            if (file < 7 && squares[aRank*8 + file+1] == Piece.make(byColor, Piece.PAWN)) return true;
        }

        // Knights
        int[][] kn = {{-2,-1},{-2,1},{-1,-2},{-1,2},{1,-2},{1,2},{2,-1},{2,1}};
        for (int[] d : kn) {
            int nr = rank+d[0], nf = file+d[1];
            if (nr>=0&&nr<8&&nf>=0&&nf<8 && squares[nr*8+nf] == Piece.make(byColor, Piece.KNIGHT)) return true;
        }

        // Bishops / Queens (diag)
        int[][] dd = {{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int[] d : dd) {
            int nr=rank+d[0], nf=file+d[1];
            while(nr>=0&&nr<8&&nf>=0&&nf<8) {
                int p = squares[nr*8+nf];
                if (p != Piece.NONE) {
                    if (Piece.color(p)==byColor && (Piece.type(p)==Piece.BISHOP||Piece.type(p)==Piece.QUEEN)) return true;
                    break;
                }
                nr+=d[0]; nf+=d[1];
            }
        }

        // Rooks / Queens (straight)
        int[][] sd = {{1,0},{-1,0},{0,1},{0,-1}};
        for (int[] d : sd) {
            int nr=rank+d[0], nf=file+d[1];
            while(nr>=0&&nr<8&&nf>=0&&nf<8) {
                int p = squares[nr*8+nf];
                if (p != Piece.NONE) {
                    if (Piece.color(p)==byColor && (Piece.type(p)==Piece.ROOK||Piece.type(p)==Piece.QUEEN)) return true;
                    break;
                }
                nr+=d[0]; nf+=d[1];
            }
        }

        // King
        int[][] km = {{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        for (int[] d : km) {
            int nr=rank+d[0], nf=file+d[1];
            if (nr>=0&&nr<8&&nf>=0&&nf<8 && squares[nr*8+nf] == Piece.make(byColor, Piece.KING)) return true;
        }

        return false;
    }

    public boolean isInCheck(int color) {
        int kSq = getKingSquare(color);
        if (kSq == -1) return false;
        return isSquareAttacked(kSq, color == Piece.WHITE ? Piece.BLACK : Piece.WHITE);
    }
}
