package awele.bot.competitor.noname.evaluation.features;

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

	    final boolean[] validOpp = board.getValidMoves(opponent);
	    int famineThreats = 0;

	    for (int hole = 0; hole < validOpp.length; hole++)
	    {
	        if (!validOpp[hole])
	            continue;

	        BitBoard next = board.simulateMove(opponent, hole);

	        // "Affamer" = au début du tour de player, il a 0 graines
	        if (next.getCurrentPlayer() == player && next.getTotalSeeds(player) == 0)
	            famineThreats++;
	    }

	    // Opposé = risque (plus il y a de menaces de famine, pire c'est)
	    return -famineThreats;
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
