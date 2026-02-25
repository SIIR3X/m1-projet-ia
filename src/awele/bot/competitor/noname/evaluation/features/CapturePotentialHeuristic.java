package awele.bot.competitor.noname.evaluation.features;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique 3/5 : potentiel de capture, c'est à dire le nombre de coups valides qui permettraient de capturer des graines.
 */
public final class CapturePotentialHeuristic implements PositionHeuristic
{
	public static final String ID = "capture_potential";
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final boolean[] valid = board.getValidMoves(player);
		int threats = 0;
		
		for (int hole = 0; hole < valid.length; hole++)
		{
			if (!valid[hole])
				continue;
			
			int captured = board.simulateMoveScore(player, hole);
			if (captured > 0)
				threats++;
		}
		
		return threats;
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
