package awele.bot.competitor.hybridprov2;

import awele.core.Board;
import awele.core.InvalidBotException;

/**
 * EvaluationBot - Bot simple pour évaluer la force d'un génome.
 * Sert d'adversaire de référence pendant l'évolution génétique.
 */
class EvaluationBot {
    
    private double[] weights;
    private int depth;
    
    public EvaluationBot(double[] weights, int depth) {
        this.weights = weights.clone();
        this.depth = depth;
    }
    
    /**
     * Retourne la décision du bot selon ses poids.
     */
    public double[] getDecision(Board board) {
        double[] scores = new double[Board.NB_HOLES];
        int player = board.getCurrentPlayer();
        boolean[] valid = board.validMoves(player);
        
        for (int move = 0; move < Board.NB_HOLES; move++) {
            if (!valid[move]) {
                scores[move] = -Double.MAX_VALUE / 2.0;
                continue;
            }
            
            try {
                Board nextBoard = board.playMoveSimulationBoard(player, buildDecision(move));
                scores[move] = -negamax(nextBoard, this.depth - 1, 
                                        -Double.MAX_VALUE / 2.0, 
                                        Double.MAX_VALUE / 2.0);
            } catch (InvalidBotException e) {
                scores[move] = -Double.MAX_VALUE / 2.0;
            }
        }
        
        return scores;
    }
    
    private double negamax(Board board, int depth, double alpha, double beta) {
        int currentPlayer = board.getCurrentPlayer();
        
        if (board.getNbSeeds() <= 6) {
            return terminalScore(board, currentPlayer);
        }
        
        boolean[] valid = board.validMoves(currentPlayer);
        boolean hasMove = false;
        for (boolean v : valid) if (v) { hasMove = true; break; }
        if (!hasMove) {
            return terminalScore(board, currentPlayer);
        }
        
        if (depth == 0) {
            return evaluate(board, currentPlayer);
        }
        
        double best = -Double.MAX_VALUE / 2.0;
        
        for (int move = 0; move < Board.NB_HOLES; move++) {
            if (!valid[move]) continue;
            
            try {
                Board nextBoard = board.playMoveSimulationBoard(currentPlayer, buildDecision(move));
                
                double val = (nextBoard.getCurrentPlayer() == currentPlayer)
                    ? negamax(nextBoard, depth - 1, alpha, beta)
                    : -negamax(nextBoard, depth - 1, -beta, -alpha);
                
                if (val > best) best = val;
                if (val > alpha) alpha = val;
                if (alpha >= beta) break;
            } catch (InvalidBotException e) {}
        }
        
        return best;
    }
    
    private double evaluate(Board board, int currentPlayer) {
        int opponent = 1 - currentPlayer;
        
        // Évaluation avec les 6 poids
        double scoreDiff = (board.getScore(currentPlayer) - board.getScore(opponent)) * weights[0];
        
        int[] myHoles = getHolesFor(board, currentPlayer);
        int[] oppHoles = getHolesFor(board, opponent);
        
        double seedsDiff = (sumArray(myHoles) - sumArray(oppHoles)) * weights[1];
        
        double captureBonus = 0.0;
        for (int s : oppHoles) {
            if (s == 2 || s == 3) captureBonus += s * weights[2];
        }
        
        int myMobility = countTrue(board.validMoves(currentPlayer));
        int oppMobility = countTrue(board.validMoves(opponent));
        double mobilityScore = (myMobility - oppMobility) * weights[3];
        
        double controlCenter = 0.0;
        if (myHoles.length >= 4) {
            controlCenter = (myHoles[2] + myHoles[3]) * weights[4];
        }
        
        double endgame = (board.getNbSeeds() < 20) ? (scoreDiff * weights[5]) : 0.0;
        
        return scoreDiff + seedsDiff + captureBonus + mobilityScore + controlCenter + endgame;
    }
    
    private double terminalScore(Board board, int currentPlayer) {
        int opponent = 1 - currentPlayer;
        int myScore = board.getScore(currentPlayer);
        int oppScore = board.getScore(opponent);
        
        if (myScore > oppScore) return 100000.0 + (myScore - oppScore);
        if (myScore < oppScore) return -100000.0 - (oppScore - myScore);
        return 0.0;
    }
    
    private static double[] buildDecision(int move) {
        double[] decision = new double[Board.NB_HOLES];
        decision[move] = 1.0;
        return decision;
    }
    
    private static int[] getHolesFor(Board board, int player) {
        return (player == board.getCurrentPlayer())
            ? board.getPlayerHoles()
            : board.getOpponentHoles();
    }
    
    private static int sumArray(int[] arr) {
        int s = 0;
        for (int v : arr) s += v;
        return s;
    }
    
    private static int countTrue(boolean[] arr) {
        int c = 0;
        for (boolean b : arr) if (b) c++;
        return c;
    }
}
