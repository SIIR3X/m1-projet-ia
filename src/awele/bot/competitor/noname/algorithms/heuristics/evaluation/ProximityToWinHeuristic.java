package awele.bot.competitor.noname.algorithms.heuristics.evaluation;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.MASK_6_BITS;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.SCORE_OFFSET;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Heuristique de position gagnante : valorise les positions où le joueur est proche de gagner (score élevé par rapport à l'adversaire)
 */
public final class ProximityToWinHeuristic implements PositionHeuristic
{
	public static final String ID = "proximity_to_win";
	
	private static final int WINNING_SCORE = 25;
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		// Mon score
		final long raw = (player == 0) ? board.raw0() : board.raw1();
		final int score = (int)((raw >> SCORE_OFFSET) & MASK_6_BITS);
	
		final int distanceToWin = Math.max(0, WINNING_SCORE - score);
		
		return -(double)distanceToWin;
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
