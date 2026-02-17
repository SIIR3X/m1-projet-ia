package awele.bot.competitor.noname.test;

import java.util.Arrays;
import java.util.Random;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.core.Board;
import awele.core.InvalidBotException;

/**
 * @author Lucas Fagioli
 * Test de correction : vérifie que Board et BitBoard produisent les mêmes résultats
 * Compare état par état, coup par coup
 */
public class BitBoardCorrectnessTest
{
    private static final int NB_GAMES_TO_TEST = 100;
    private static final int MAX_MOVES_PER_GAME = 200;
    private static Random random = new Random(12345);  // Seed fixe
    
    private static int totalErrors = 0;
    private static int totalMoves = 0;
    
    public static void main(String[] args)
    {
        System.out.println("========================================");
        System.out.println("  Test de correction : Board vs BitBoard");
        System.out.println("========================================");
        System.out.println("Nombre de parties à vérifier : " + NB_GAMES_TO_TEST);
        System.out.println();
        
        for (int game = 0; game < NB_GAMES_TO_TEST; game++)
        {
            boolean success = testGame(game);
            
            if (!success)
            {
                System.out.println("❌ Partie " + game + " : ERREURS DÉTECTÉES");
            }
            else if (game % 10 == 0)
            {
                System.out.println("✓ Partie " + game + " : OK");
            }
        }
        
        System.out.println();
        System.out.println("========================================");
        System.out.println("  Résultats du test");
        System.out.println("========================================");
        System.out.println("Parties testées : " + NB_GAMES_TO_TEST);
        System.out.println("Coups vérifiés : " + totalMoves);
        System.out.println("Erreurs détectées : " + totalErrors);
        
        if (totalErrors == 0)
        {
            System.out.println();
            System.out.println("🎉 SUCCÈS ! Board et BitBoard sont identiques !");
        }
        else
        {
            System.out.println();
            System.out.println("⚠️  ATTENTION : Des différences ont été détectées !");
        }
        System.out.println("========================================");
    }
    
    /**
     * Test d'une partie complète
     */
    private static boolean testGame(int gameNumber)
    {
        Board board = new Board();
        BitBoard bitBoard = new BitBoard();
        
        int moves = 0;
        boolean hasError = false;
        
        // Vérification de l'état initial
        if (!verifyState(board, bitBoard, 0, "Initialisation"))
        {
            System.out.println("  Erreur dès l'initialisation de la partie " + gameNumber);
            hasError = true;
        }
        
        // Joue la partie coup par coup
        while (moves < MAX_MOVES_PER_GAME && !isGameOverBoard(board) && !bitBoard.isGameOver())
        {
            int currentPlayer = board.getCurrentPlayer();
            double[] decision = getRandomDecision();
            
            // Joue le coup sur Board
            Board newBoard;
            try
            {
                newBoard = board.playMoveSimulationBoard(currentPlayer, decision);
            }
            catch (InvalidBotException e)
            {
                break;
            }
            
            // Joue le coup sur BitBoard
            bitBoard.playMove(decision);
            
            moves++;
            totalMoves++;
            
            // Vérification après chaque coup
            if (!verifyState(newBoard, bitBoard, moves, "Coup " + moves))
            {
                System.out.println("  Divergence détectée à la partie " + gameNumber + ", coup " + moves);
                hasError = true;
                totalErrors++;
                
                // Affiche les détails de l'erreur
                System.out.println("    Décision jouée : " + Arrays.toString(decision));
                System.out.println("    Board:");
                printBoardState(newBoard);
                System.out.println("    BitBoard:");
                printBitBoardState(bitBoard);
                
                break;  // Arrête cette partie
            }
            
            board = newBoard;
        }
        
        // Vérification finale : même fin de partie
        if (!hasError)
        {
            boolean boardOver = isGameOverBoard(board);
            boolean bitBoardOver = bitBoard.isGameOver();
            
            if (boardOver != bitBoardOver)
            {
                System.out.println("  Divergence sur isGameOver à la partie " + gameNumber);
                System.out.println("    Board.isGameOver : " + boardOver);
                System.out.println("    BitBoard.isGameOver : " + bitBoardOver);
                hasError = true;
                totalErrors++;
            }
        }
        
        return !hasError;
    }
    
    /**
     * Vérifie que Board et BitBoard ont le même état
     */
    private static boolean verifyState(Board board, BitBoard bitBoard, int moveNumber, String context)
    {
        boolean identical = true;
        
        // 1. Vérification du joueur actuel
        int boardCurrentPlayer = board.getCurrentPlayer();
        int bitBoardCurrentPlayer = bitBoard.getCurrentPlayer();
        
        if (boardCurrentPlayer != bitBoardCurrentPlayer)
        {
            System.out.println("  [" + context + "] Joueur actuel différent : Board=" + boardCurrentPlayer + ", BitBoard=" + bitBoardCurrentPlayer);
            identical = false;
        }
        
        // 2. Vérification des trous du joueur actuel
        int[] boardPlayerHoles = board.getPlayerHoles();
        int[] bitBoardPlayerHoles = bitBoard.getHoles(boardCurrentPlayer);
        
        if (!Arrays.equals(boardPlayerHoles, bitBoardPlayerHoles))
        {
            System.out.println("  [" + context + "] Trous joueur actuel différents :");
            System.out.println("    Board     : " + Arrays.toString(boardPlayerHoles));
            System.out.println("    BitBoard  : " + Arrays.toString(bitBoardPlayerHoles));
            identical = false;
        }
        
        // 3. Vérification des trous de l'adversaire
        int[] boardOpponentHoles = board.getOpponentHoles();
        int opponent = 1 - boardCurrentPlayer;
        int[] bitBoardOpponentHoles = bitBoard.getHoles(opponent);
        
        if (!Arrays.equals(boardOpponentHoles, bitBoardOpponentHoles))
        {
            System.out.println("  [" + context + "] Trous adversaire différents :");
            System.out.println("    Board     : " + Arrays.toString(boardOpponentHoles));
            System.out.println("    BitBoard  : " + Arrays.toString(bitBoardOpponentHoles));
            identical = false;
        }
        
        // 4. Vérification des scores
        int boardScore0 = board.getScore(0);
        int boardScore1 = board.getScore(1);
        int bitBoardScore0 = bitBoard.getScore(0);
        int bitBoardScore1 = bitBoard.getScore(1);
        
        if (boardScore0 != bitBoardScore0 || boardScore1 != bitBoardScore1)
        {
            System.out.println("  [" + context + "] Scores différents :");
            System.out.println("    Board     : " + boardScore0 + " - " + boardScore1);
            System.out.println("    BitBoard  : " + bitBoardScore0 + " - " + bitBoardScore1);
            identical = false;
        }
        
        // 5. Vérification des coups valides
        boolean[] boardValidMoves = board.validMoves(boardCurrentPlayer);
        boolean[] bitBoardValidMoves = bitBoard.getValidMoves(boardCurrentPlayer);
        
        if (!Arrays.equals(boardValidMoves, bitBoardValidMoves))
        {
            System.out.println("  [" + context + "] Coups valides différents :");
            System.out.println("    Board     : " + Arrays.toString(boardValidMoves));
            System.out.println("    BitBoard  : " + Arrays.toString(bitBoardValidMoves));
            identical = false;
        }
        
        // 6. Vérification du nombre de graines
        int boardSeeds = board.getNbSeeds();
        int bitBoardSeeds = getTotalSeeds(bitBoard);
        
        if (boardSeeds != bitBoardSeeds)
        {
            System.out.println("  [" + context + "] Nombre total de graines différent :");
            System.out.println("    Board     : " + boardSeeds);
            System.out.println("    BitBoard  : " + bitBoardSeeds);
            identical = false;
        }
        
        return identical;
    }
    
    /**
     * Calcule le total de graines sur BitBoard
     */
    private static int getTotalSeeds(BitBoard bitBoard)
    {
        int total = 0;
        for (int i = 0; i < 6; i++)
        {
            total += bitBoard.getHoles(0)[i];
            total += bitBoard.getHoles(1)[i];
        }
        return total;
    }
    
    /**
     * Vérifie si une partie Board est terminée
     */
    private static boolean isGameOverBoard(Board board)
    {
        if (board.getScore(0) >= 25 || board.getScore(1) >= 25)
            return true;
        
        if (board.getNbSeeds() <= 6)
            return true;
        
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
     * Affiche l'état d'un Board
     */
    private static void printBoardState(Board board)
    {
        System.out.println("      Joueur actuel : " + board.getCurrentPlayer());
        System.out.println("      Trous joueur  : " + Arrays.toString(board.getPlayerHoles()));
        System.out.println("      Trous adverse : " + Arrays.toString(board.getOpponentHoles()));
        System.out.println("      Scores        : " + board.getScore(0) + " - " + board.getScore(1));
        System.out.println("      Graines total : " + board.getNbSeeds());
    }
    
    /**
     * Affiche l'état d'un BitBoard
     */
    private static void printBitBoardState(BitBoard bitBoard)
    {
        System.out.println("      Joueur actuel : " + bitBoard.getCurrentPlayer());
        System.out.println("      Trous joueur 0: " + Arrays.toString(bitBoard.getHoles(0)));
        System.out.println("      Trous joueur 1: " + Arrays.toString(bitBoard.getHoles(1)));
        System.out.println("      Scores        : " + bitBoard.getScore(0) + " - " + bitBoard.getScore(1));
        System.out.println("      Graines total : " + getTotalSeeds(bitBoard));
    }
}