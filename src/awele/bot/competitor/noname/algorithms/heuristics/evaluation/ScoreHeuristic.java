package awele.bot.competitor.noname.algorithms.heuristics.evaluation;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitConstants;

/**
 * @autor Lucas Fagioli
 * Heuristique de score : retourne le score actuel du joueur
 */
public final class ScoreHeuristic implements PositionHeuristic
{
	public static final String ID = "score";
	
	// Masque et offset pour extraire le score (bits 36 à 41)
	private static final long MASK = BitConstants.MASK_6_BITS;
	private static final int OFFSET = BitConstants.SCORE_OFFSET;
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final long raw = (player == 0) ? board.raw0() : board.raw1();
		
		return (double)((raw >> OFFSET) & MASK);
	}

	@Override
	public String getId()
	{
		return ID;
	}
}
