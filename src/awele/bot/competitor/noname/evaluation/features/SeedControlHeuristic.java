package awele.bot.competitor.noname.evaluation.features;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique 4/7 : Contrôle des graines, c'est à dire la pénalité pour les trous contenant 1, 2 ou 3 graines
 */
public final class SeedControlHeuristic implements PositionHeuristic
{
	public static final String ID = "seed_control";
	
	private static final int PENALITY_1_SEED = -3;
	private static final int PENALITY_2_SEEDS = -2;
	private static final int PENALITY_3_SEEDS = -1;
	private static final int BONUS_SAFE = 1;
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final int opponent = 1 - player;
		
		int playerControl = calculateSeedControl(board, player);
		int opponentControl = calculateSeedControl(board, opponent);
		
		return playerControl - opponentControl;
	}
	
	/**
	 * Calcule le score de contrôle des granes pour un joueur
	 * @param board l'état du plateau
	 * @param player le joueur pour lequel calculer le score de contrôle des graines
	 * @return un score de contrôle des graines (plus il est élevé, mieux c'est pour le joueur)
	 */
	private static int calculateSeedControl(BitBoard board, int player)
	{
		int control = 0;
		
		for (int hole = 0; hole < NB_HOLES; hole++)
		{
			final int seeds = board.getSeeds(player, hole);
			
			if (seeds == 1)
				control += PENALITY_1_SEED;
			else if (seeds == 2)
				control += PENALITY_2_SEEDS;
			else if (seeds == 3)
				control += PENALITY_3_SEEDS;
			else if (seeds >= 4)
				control += BONUS_SAFE;
		}
		
		return control;
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
