package awele.bot.competitor.noname.evaluation.features;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique 1/5 : score (graines capturées) du joueur.
 * Dans PositionEvaluator, on prend joueur - adversaire.
 */
public final class ScoreDifferenceHeuristic implements PositionHeuristic
{
	public static final String ID = "score";
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		return board.getScore(player);
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
