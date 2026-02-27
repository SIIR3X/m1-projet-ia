package awele.bot.competitor.noname.evaluation.features;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique 1/7 : Différence de score (score du joueur - score de l'adversaire)
 */
public final class ScoreDifferenceHeuristic implements PositionHeuristic
{
	public static final String ID = "score";
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		return board.getScore(player) - board.getScore(1 - player);
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
