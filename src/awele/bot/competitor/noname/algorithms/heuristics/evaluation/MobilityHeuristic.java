package awele.bot.competitor.noname.algorithms.heuristics.evaluation;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.MASK_3_BITS;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_MOVES_OFFSET;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Heuristique de mobilité : valorise les positions où le joueur a plus de coups possibles
 */
public final class MobilityHeuristic implements PositionHeuristic
{
	public static final String ID = "mobility";
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final long raw = (player == 0) ? board.raw0() : board.raw1();
		return (double)((raw >> NB_MOVES_OFFSET) & MASK_3_BITS);
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
