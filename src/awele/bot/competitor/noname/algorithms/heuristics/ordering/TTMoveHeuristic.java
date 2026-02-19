package awele.bot.competitor.noname.algorithms.heuristics.ordering;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Heuristique de coup du Transposition Table
 */
public final class TTMoveHeuristic implements MoveHeuristic
{
	private static final int TT_BONUS = 1_000_000; // Priorité absolue car dans TT
	
	private final int ttBestMove;
	
	public TTMoveHeuristic(int ttBestMove)
	{
		this.ttBestMove = ttBestMove;
	}
	
	@Override
	public int evaluate(BitBoard board, int player, int hole)
	{
		return (hole == ttBestMove) ? TT_BONUS : 0;
	}
}
