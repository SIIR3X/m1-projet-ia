package awele.bot.competitor.noname.evaluation.features;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique 1/6 : Différence de score (score du joueur - score de l'adversaire)
 */
public final class ScoreDifferenceHeuristic implements PositionHeuristic
{
	@Override
	public double evaluate(BitBoard board, int player)
	{
		return board.getScore(player) - board.getScore(1 - player);
	}
}
