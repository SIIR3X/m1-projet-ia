package awele.bot.competitor.noname.ordering.heuristics;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.ordering.MoveHeuristic;

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
