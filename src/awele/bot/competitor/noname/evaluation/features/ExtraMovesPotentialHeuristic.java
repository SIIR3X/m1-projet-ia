package awele.bot.competitor.noname.evaluation.features;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique 7/7 : Potentiel de coups supplémentaires
 */
public final class ExtraMovesPotentialHeuristic implements PositionHeuristic
{
	public static final String ID = "extra_moves_potential";
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final int opponent = 1 - player;
		
		int playerExtraMoves = countExtraMoves(board, player);
		int opponentExtraMoves = countExtraMoves(board, opponent);
		
		return playerExtraMoves - opponentExtraMoves;
	}
	
	/**
	 * Compte le nombre de coups valides qui permettraient de rejouer
	 * @param board Le plateau de jeu
	 * @param player Le joueur à évaluer
	 * @return Le nombre de coups donnant un tour supplémentaire
	 */
	private static int countExtraMoves(BitBoard board, int player)
	{
		final boolean[] validMoves = board.getValidMoves(player);
		int extraMovesCount = 0;
		
		for (int hole = 0; hole < validMoves.length; hole++)
		{
			if (!validMoves[hole])
				continue;
			
			final int seeds = board.getSeeds(player, hole);
			
			int distanceToEnd = (NB_HOLES - 1) - hole;
			
			if (seeds == distanceToEnd + 1)
				extraMovesCount++;
			else if (seeds > NB_HOLES)
			{
				int remaining = seeds - distanceToEnd - 1;
				
				if (remaining > 0 && remaining % (NB_HOLES * 2) == 0)
					extraMovesCount++;
			}
		}
		
		return extraMovesCount;
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
