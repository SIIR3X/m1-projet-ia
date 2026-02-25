package awele.bot.competitor.noname.evaluation.features;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique 5/5 : sécurité face à la famine, c'est à dire le nombre de coups valides qui permettraient
 * à l'adversaire de nous affamer (avoir 0 graines au début de son tour).
 */
public final class FamineSafetyHeuristic implements PositionHeuristic
{
	public static final String ID = "famine_safety";
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final int opponent = 1 - player;
		
		final boolean[] valid = board.getValidMoves(player);
		int famineThreats = 0;
		
		for (int hole = 0; hole < NB_HOLES; hole++)
		{
			if (!valid[hole])
				continue;
			
			BitBoard next = board.simulateMove(opponent, hole);
			
			if (next.getCurrentPlayer() == player && next.getTotalSeeds(player) == 0)
				famineThreats++;
		}
		
		return -famineThreats;
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
