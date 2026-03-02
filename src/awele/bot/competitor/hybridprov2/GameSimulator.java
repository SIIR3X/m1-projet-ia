package awele.bot.competitor.hybridprov2;

import awele.core.Board;
import awele.core.InvalidBotException;

/**
 * GameSimulator - Simule une partie complète entre deux bots.
 * Optimisé pour la vitesse (utilisé des milliers de fois pendant l'évolution).
 */
class GameSimulator {
    
    private static final int MAX_MOVES = 500;
    
    /**
     * Simule une partie entre deux EvaluationBots.
     * 
     * @return tableau [scorePlayer0, scorePlayer1, winner]
     *         winner = 0 si joueur 0 gagne, 1 si joueur 1 gagne, -1 si égalité
     */
    public static int[] simulateGame(EvaluationBot bot0, EvaluationBot bot1) {
        Board board = new Board();
        int moves = 0;
        
        while (moves < MAX_MOVES && board.getNbSeeds() > 6) {
            int currentPlayer = board.getCurrentPlayer();
            boolean[] valid = board.validMoves(currentPlayer);
            
            boolean hasMove = false;
            for (boolean v : valid) if (v) { hasMove = true; break; }
            if (!hasMove) break;
            
            try {
                double[] decision;
                if (currentPlayer == 0) {
                    decision = bot0.getDecision(board);
                } else {
                    decision = bot1.getDecision(board);
                }
                board = board.playMoveSimulationBoard(currentPlayer, decision);
            } catch (InvalidBotException e) {
                break;
            }
            
            moves++;
        }
        
        // Scores finaux
        int score0 = board.getScore(0);
        int score1 = board.getScore(1);
        
        // Ajouter graines restantes
        if (board.getNbSeeds() > 0 && board.getNbSeeds() <= 6) {
            int[] h0 = board.getPlayerHoles();
            int[] h1 = board.getOpponentHoles();
            if (board.getCurrentPlayer() == 0) {
                score0 += sumArray(h0);
                score1 += sumArray(h1);
            } else {
                score0 += sumArray(h1);
                score1 += sumArray(h0);
            }
        }
        
        int winner = (score0 > score1) ? 0 : (score1 > score0) ? 1 : -1;
        
        return new int[] {score0, score1, winner};
    }
    
    /**
     * Simule plusieurs parties et retourne le taux de victoire de bot0.
     * 
     * @return score entre 0.0 (bot0 perd tout) et 1.0 (bot0 gagne tout)
     */
    public static double evaluateWinRate(EvaluationBot bot0, EvaluationBot bot1, int numGames) {
        double wins = 0.0;
        
        for (int i = 0; i < numGames; i++) {
            int[] result = simulateGame(bot0, bot1);
            if (result[2] == 0) wins += 1.0;
            else if (result[2] == -1) wins += 0.5;
        }
        
        return wins / numGames;
    }
    
    private static int sumArray(int[] arr) {
        int s = 0;
        for (int v : arr) s += v;
        return s;
    }
}
