package awele.bot.competitor.noname.test;

import java.util.Random;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.core.Board;
import awele.core.InvalidBotException;

/**
 * @author Lucas Fagioli
 * Test de performance Board vs BitBoard
 * Compare la vitesse d'exécution et la consommation mémoire
 */
public class BitBoardPerformanceTest
{
    // Configuration du test
    private static final int NB_GAMES = 1000000;  // Nombre de parties à jouer
    private static final int MAX_MOVES_PER_GAME = 200;  // Limite de coups par partie
    
    private static Random random = new Random(42);  // Seed fixe pour reproductibilité
    
    /**
     * Point d'entrée du test
     */
    public static void main(String[] args)
    {
        System.out.println("========================================");
        System.out.println("  Test de performance : Board vs BitBoard");
        System.out.println("========================================");
        System.out.println("Nombre de parties : " + NB_GAMES);
        System.out.println();
        
        // Force le garbage collector avant les tests
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        
        // Test Board classique
        System.out.println("--- Test Board classique ---");
        long boardTime = testBoardPerformance();
        long boardMemory = getUsedMemory();
        
        // Force le garbage collector entre les tests
        System.gc();
        try { Thread.sleep(100); } catch (InterruptedException e) {}
        
        // Test BitBoard
        System.out.println("\n--- Test BitBoard optimisé ---");
        long bitBoardTime = testBitBoardPerformance();
        long bitBoardMemory = getUsedMemory();
        
        // Affichage des résultats comparatifs
        System.out.println("\n========================================");
        System.out.println("  Résultats comparatifs");
        System.out.println("========================================");
        
        System.out.println("\nTemps d'exécution :");
        System.out.println("  Board classique : " + boardTime + " ms");
        System.out.println("  BitBoard        : " + bitBoardTime + " ms");
        System.out.println("  Gain de vitesse : x" + String.format("%.2f", (double) boardTime / bitBoardTime));
        
        System.out.println("\nMémoire utilisée :");
        System.out.println("  Board classique : " + formatMemory(boardMemory));
        System.out.println("  BitBoard        : " + formatMemory(bitBoardMemory));
        if (bitBoardMemory > 0)
        {
            System.out.println("  Gain de mémoire : x" + String.format("%.2f", (double) boardMemory / bitBoardMemory));
        }
        
        System.out.println("\n========================================");
    }
    
    /**
     * Test de performance avec Board classique
     * @return Temps d'exécution en millisecondes
     */
    private static long testBoardPerformance()
    {
        // Réinitialise le Random pour avoir les mêmes parties
        random = new Random(42);
        
        long startTime = System.currentTimeMillis();

        int totalMoves = 0;
        int completedGames = 0;
        
        for (int game = 0; game < NB_GAMES; game++)
        {
            Board board = new Board();
            int moves = 0;
            
            while (moves < MAX_MOVES_PER_GAME && !isGameOver(board))
            {
                int currentPlayer = board.getCurrentPlayer();
                double[] decision = getRandomDecision();
                
                try
                {
                    // Board n'expose pas playMove publiquement, on utilise playMoveSimulationBoard
                    board = board.playMoveSimulationBoard(currentPlayer, decision);
                }
                catch (InvalidBotException e)
                {
                    break;
                }
                
                moves++;
            }
            
            totalMoves += moves;
            completedGames++;
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("Parties jouées : " + completedGames);
        System.out.println("Coups moyens par partie : " + (totalMoves / completedGames));
        System.out.println("Temps total : " + duration + " ms");
        System.out.println("Temps moyen par partie : " + (duration / completedGames) + " ms");
        
        return duration;
    }
    
    /**
     * Test de performance avec BitBoard
     * @return Temps d'exécution en millisecondes
     */
    private static long testBitBoardPerformance()
    {
        // Réinitialise le Random pour avoir les mêmes parties
        random = new Random(42);
        
        long startTime = System.currentTimeMillis();

        int totalMoves = 0;
        int completedGames = 0;
        
        for (int game = 0; game < NB_GAMES; game++)
        {
            BitBoard board = new BitBoard();
            int moves = 0;
            
            while (moves < MAX_MOVES_PER_GAME && !board.isGameOver())
            {
                double[] decision = getRandomDecision();
                board.playMove(decision);
                moves++;
            }
            
            totalMoves += moves;
            completedGames++;
        }
        
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        
        System.out.println("Parties jouées : " + completedGames);
        System.out.println("Coups moyens par partie : " + (totalMoves / completedGames));
        System.out.println("Temps total : " + duration + " ms");
        System.out.println("Temps moyen par partie : " + (duration / completedGames) + " ms");
        
        return duration;
    }
    
    /**
     * Vérifie si une partie Board est terminée
     */
    private static boolean isGameOver(Board board)
    {
        // Un joueur a gagné
        if (board.getScore(0) >= 25 || board.getScore(1) >= 25)
        {
            return true;
        }
        
        // Moins de 6 graines
        if (board.getNbSeeds() <= 6)
        {
            return true;
        }
        
        // Pas de coup valide
        int current = board.getCurrentPlayer();
        boolean[] valid = board.validMoves(current);
        for (boolean v : valid)
        {
            if (v) return false;
        }
        
        return true;
    }
    
    /**
     * Génère une décision aléatoire
     */
    private static double[] getRandomDecision()
    {
        double[] decision = new double[6];
        for (int i = 0; i < 6; i++)
        {
            decision[i] = random.nextDouble();
        }
        return decision;
    }
    
    /**
     * Récupère la mémoire utilisée
     */
    private static long getUsedMemory()
    {
        return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
    }
    
    /**
     * Formate la mémoire en unités lisibles
     */
    private static String formatMemory(long bytes)
    {
        int unit = 1024;
        if (bytes < unit) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(unit));
        String pre = ("KMGTPE").charAt(exp - 1) + "i";
        return String.format("%.1f %sB", bytes / Math.pow(unit, exp), pre);
    }
}