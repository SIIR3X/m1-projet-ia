package awele.bot.competitor.noname.algorithms.heuristics;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @autor Lucas Fagioli
 */
public final class CaptureOrderingHeuristic implements MoveHeuristic
{
	private static final int WEIGHT = 100;
	
	@Override
	public int evaluate(BitBoard board, int player, int hole)
	{
		final int captured = board.simulateMoveScore(player, hole);
		
		return (captured > 0) ? captured: 0;
	}
	
	@Override
	public int getWeight()
	{
		return WEIGHT;
	}
}
