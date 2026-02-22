package awele.bot.competitor.noname.algorithms.heuristics.evaluation;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.BITS_PER_HOLE;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.HOLES_OFFSET;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.MASK_6_BITS;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Heuristique de menace de famine : valorise les positions où l'adversaire a des trous vides ou gelés, ce qui le met en danger de famine
 */
public final class StarvationThreatHeuristic implements PositionHeuristic
{
	public static final String ID = "starvation_threat";
	
	private static final double WEIGHT_EMPTY = 2.0;
	private static final double WEIGHT_FROZEN = 1.0;
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final int opponent = 1 - player;
		
		final long raw = (opponent == 0) ? board.raw0() : board.raw1();
		
		double score = 0.0;
		
		for (int hole = 0; hole < NB_HOLES; hole++)
		{
			final int seeds = (int)((raw >> (HOLES_OFFSET + hole * BITS_PER_HOLE)) & MASK_6_BITS);
			
			if (seeds == 0)
				score += WEIGHT_EMPTY;
			else if (seeds == 1)
				score += WEIGHT_FROZEN;
		}
		
		return score;
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
