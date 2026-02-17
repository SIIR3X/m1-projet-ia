package awele.bot.competitor.noname.algorithms.transposition;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;

import java.util.Random;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Calcul du hash de Zobrist pour identifier uniquement les positions
 * 
 * Voir https://en.wikipedia.org/wiki/Zobrist_hashing pour plus d'informations
 * Le hash de Zobrist utilise des XOR de nombres aléatoires pré-calculés
 */
public final class ZobristHash
{
	private static final int NB_PLAYERS = 2;
	private static final int MAX_SEEDS = 64;
	
	/**
	 * Table de hash
	 */
	private static final long[][][] zobristTable = new long[NB_PLAYERS][NB_HOLES][MAX_SEEDS];
	
	/**
	 * Hash spécial pour indiquer que c'est au tour du jouer1
	 */
	private static final long zobristPlayerOne;
	
	// Initialisation de la table
	static
	{
		Random random = new Random(57);
		
		for (int player = 0; player < NB_PLAYERS; player++)
		{
			for (int hole = 0; hole < NB_HOLES; hole++)
			{
				for (int seeds = 0; seeds < MAX_SEEDS; seeds++)
				{
					zobristTable[player][hole][seeds] = random.nextLong();
				}
			}
		}
		
		zobristPlayerOne = random.nextLong();
	}
	
	// Empeche l'instanciation
	private ZobristHash()
	{
		throw new AssertionError("Classe utilitaire, ne doit pas être instanciée");
	}
	
	/**
	 * Calcule le hash de Zobrist d'une position
	 * @param board Le plateau de jeu à hasher
	 * @return Le hash de Zobrist de la position
	 */
	public static long calculate(BitBoard board)
	{
		long hash = 0L;
		
		// On hash les graines
		for (int player = 0; player < NB_PLAYERS; player++)
		{
			for (int hole = 0; hole < NB_HOLES; hole++)
			{
				int seeds = board.getSeeds(player, hole);
				
				if (seeds > 0 && seeds < MAX_SEEDS)
					hash ^= zobristTable[player][hole][seeds];
			}
		}
		
		// Hash du joueur courant
		if (board.getCurrentPlayer() == 1)
			hash ^= zobristPlayerOne;
		
		return hash;
	}
}
