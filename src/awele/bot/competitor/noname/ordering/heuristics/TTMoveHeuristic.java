package awele.bot.competitor.noname.ordering.heuristics;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.ordering.MoveHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique de coup du Transposition Table
 */
public final class TTMoveHeuristic implements MoveHeuristic
{
	private final int ttBestMove;
	
	public TTMoveHeuristic(int ttBestMove)
	{
		this.ttBestMove = ttBestMove;
	}
	
	@Override
	public int evaluate(BitBoard board, int player, int hole)
	{
		return (hole == ttBestMove) ? 1 : 0;
	}
}
