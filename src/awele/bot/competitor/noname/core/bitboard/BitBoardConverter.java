package awele.bot.competitor.noname.core.bitboard;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.BITS_PER_HOLE;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.CURRENT_PLAYER_OFFSET;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.FIRST_MOVE_OFFSET;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.HOLES_OFFSET;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.INITIAL_SEEDS;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.MASK_1_BIT;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.MASK_3_BITS;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.MASK_6_BITS;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_MOVES_OFFSET;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.OWNER_OFFSET;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.SCORE_OFFSET;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.VALID_MOVES_OFFSET;

import awele.core.Board;

/**
 * @author Lucas Fagioli
 * Convertisseur entre Board (du projet) et BitBoard
 * Permet de faire le pont entre le moteur existant et mon moteur bit à bit
 */
public final class BitBoardConverter
{
	private BitBoardConverter(){}
	
	/**
	 * Convertit un Board en BitBoard
	 * @param board Le Board à convertir
	 * @return Un BitBoard représentant l'état du Board donné
	 */
	public static BitBoard fromBoard(Board board)
	{
		BitBoard bitBoard = new BitBoard();
		long[] boards = new long[2];
		
		int currentPlayer = board.getCurrentPlayer();
		int opponent = 1 - currentPlayer;
		
		// Conversion des trous
		int[] playerHoles = board.getPlayerHoles();
		int[] opponentHoles = board.getOpponentHoles();
		
		for (int i = 0; i < NB_HOLES; i++)
		{
			// Trous du joueur actuel
			int offset = HOLES_OFFSET + (i * BITS_PER_HOLE);
			boards[currentPlayer] |= ((long)playerHoles[i] & MASK_6_BITS) << offset;
			
			// Trous de l'adversaire
			boards[opponent] |= ((long)opponentHoles[i] & MASK_6_BITS) << offset;
		}
		
		// Conversion des scores
		boards[currentPlayer] |= ((long)board.getScore(currentPlayer) & MASK_6_BITS) << SCORE_OFFSET;
		boards[opponent] |= ((long)board.getScore(opponent) & MASK_6_BITS) << SCORE_OFFSET;
		
		// Propriétaires
		boards[1] |= (1L << OWNER_OFFSET); // Le joueur 1 est propriétaire du board 1
		
		// Joueur actuel
		boards[0] |= ((long)currentPlayer & MASK_1_BIT) << CURRENT_PLAYER_OFFSET;
		
		// Coups valides
		boolean[] validMovesPlayer = board.validMoves(currentPlayer);
		boolean[] validMovesOpponent = board.validMoves(opponent);
		
		int nbValidPlayer = 0;
		int nbValidOpponent = 0;
		
		for (int i = 0; i < NB_HOLES; i++)
		{
			if (validMovesPlayer[i])
			{
				boards[currentPlayer] |= (1L << (VALID_MOVES_OFFSET + i));
				nbValidPlayer++;
			}
			
			if (validMovesOpponent[i])
			{
				boards[opponent] |= (1L << (VALID_MOVES_OFFSET + i));
				nbValidOpponent++;
			}
		}
		
		// Nombre de coups valides
		boards[currentPlayer] |= ((long)nbValidPlayer & MASK_3_BITS) << NB_MOVES_OFFSET;
		boards[opponent] |= ((long)nbValidOpponent & MASK_3_BITS) << NB_MOVES_OFFSET;
		
		// Premier coup
		boolean isFirstMovePlayer = true;
		boolean isFirstMoveOpponent = true;
		
		for (int i = 0; i < NB_HOLES; i++)
		{
			if (playerHoles[i] != INITIAL_SEEDS)
				isFirstMovePlayer = false;
			
			if (opponentHoles[i] != INITIAL_SEEDS)
				isFirstMoveOpponent = false;
		}
		
		if (isFirstMovePlayer)
			boards[currentPlayer] |= (1L << FIRST_MOVE_OFFSET);
		
		if (isFirstMoveOpponent)
			boards[opponent] |= (1L << FIRST_MOVE_OFFSET);
		
		// Définit les boards internes
		bitBoard.setBoardsArray(boards);
		
		return bitBoard;
	}
	
	/**
	 * Convertit un tableau long[2] en BitBoard
	 * @param boards Un tableau de deux longs représentant les deux boards
	 * @return Un BitBoard construit à partir du tableau donné
	 */
	public static BitBoard fromLongArray(long[] boards)
	{
		BitBoard bitBoard = new BitBoard();
		bitBoard.setBoardsArray(boards);
		return bitBoard;
	}
	
	/**
	 * Extrait les trous de l'adversaire à partir d'un Board
	 * @param board Le Board dont on veut extraire les trous de l'adversaire
	 * @return Un tableau d'entiers représentant les trous de l'adversaire
	 */
	public static int[] getOpponentHolesFromBoard(Board board)
	{
		return board.getOpponentHoles();
	}
	
	/**
	 * Créer un BitBoard à partir de l'état complet
	 * @param playerHoles Trous du joueur actuel
	 * @param opponentHoles Trous de l'adversaire
	 * @param playerScore Score du joueur actuel
	 * @param opponentScore Score de l'adversaire
	 * @param currentPlayer ID du joueur actuel (0 ou 1)
	 * @return Un BitBoard construit à partir des informations données
	 */
	public static BitBoard createBitBoard(
		int[] playerHoles, int[] opponentHoles,
		int playerScore, int opponentScore,
		int currentPlayer)
	{
		BitBoard bitBoard = new BitBoard();
		long[] boards = new long[2];
		
		int opponent = 1 - currentPlayer;
		
		// Trous des joueurs
		for (int i = 0; i < NB_HOLES; i++)
		{
			int offset = HOLES_OFFSET + (i * BITS_PER_HOLE);
			
			boards[currentPlayer] |= ((long)playerHoles[i] & MASK_6_BITS) << offset;
			boards[opponent] |= ((long)opponentHoles[i] & MASK_6_BITS) << offset;
		}
		
		// Scores
		boards[currentPlayer] |= ((long)playerScore & MASK_6_BITS) << SCORE_OFFSET;
		boards[opponent] |= ((long)opponentScore & MASK_6_BITS) << SCORE_OFFSET;
		
		// Propriétaires
		boards[1] |= (1L << OWNER_OFFSET); // Le joueur 1 est propriétaire du board 1
		
		// Joueur actuel
		boards[0] |= ((long)currentPlayer & MASK_1_BIT) << CURRENT_PLAYER_OFFSET;
		
		bitBoard.setBoardsArray(boards);
		
		return bitBoard;
	}
	
}
