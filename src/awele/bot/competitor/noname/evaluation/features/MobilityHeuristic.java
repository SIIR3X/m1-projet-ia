package awele.bot.competitor.noname.evaluation.features;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique 2/5 : mobilité du joueur, c'est à dire le nombre de coups valides disponibles.
 */
public final class MobilityHeuristic implements PositionHeuristic
{
	public static final String ID = "mobility";
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		return countTrue(board.getValidMoves(player));
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
	
	/**
	 * Compte le nombre de cases valides (true) dans un tableau de booléens
	 * @param b le tableau de booléens à compter
	 * @return le nombre de cases valides (true) dans le tableau
	 */
	private static int countTrue(boolean[] b)
	{
		int count = 0;
		
		for (boolean v : b)
		{
			if (v)
				count++;
		}
		
		return count;
	}
}
