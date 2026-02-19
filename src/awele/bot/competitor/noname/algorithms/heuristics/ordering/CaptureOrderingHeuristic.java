package awele.bot.competitor.noname.algorithms.heuristics.ordering;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @autor Lucas Fagioli
 * Heuristique de capture
 */
public final class CaptureOrderingHeuristic implements MoveHeuristic
{
	@Override
	public int evaluate(BitBoard board, int player, int hole)
	{
		final int captured = board.simulateMoveScore(player, hole);
		
		return (captured > 0) ? captured: 0;
	}
}
