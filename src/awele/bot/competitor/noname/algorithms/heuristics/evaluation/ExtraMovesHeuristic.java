package awele.bot.competitor.noname.algorithms.heuristics.evaluation;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Heuristique de coups supplémentaires : valorise les positions où le joueur peut jouer plusieurs fois de suite
 */
public final class ExtraMovesHeuristic implements PositionHeuristic
{
	public static final String ID = "extra_moves";
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final boolean[] validMoves = board.getValidMoves(player);
		int extraMovesCount = 0;
		
		for (int hole = 0; hole < validMoves.length; hole++)
		{
			if (!validMoves[hole])
				continue;
			
			final BitBoard copy = board.clone();
			final double[] decision = new double[6];
			decision[hole] = 1.0;
			
			copy.playMove(decision);
				
			if (copy.getCurrentPlayer() == player)
				extraMovesCount++;
		}
		
		return (double)extraMovesCount;
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
