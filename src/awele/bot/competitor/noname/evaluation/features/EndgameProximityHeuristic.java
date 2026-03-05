package awele.bot.competitor.noname.evaluation.features;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.WINNING_SCORE;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique 5/6 : Proximté de la victoire
 */
public final class EndgameProximityHeuristic implements PositionHeuristic
{
	private static final int ENDGAME_THRESHOLD = WINNING_SCORE - 5;
	private static final double URGENCY_MULTIPLIER = 3.0;
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final int playerScore = board.getScore(player);
		final int opponentScore = board.getScore(1 - player);
		
		double value = 0.0;
		
		if (playerScore >= ENDGAME_THRESHOLD)
		{
			int proximity = playerScore - ENDGAME_THRESHOLD;
			value += URGENCY_MULTIPLIER * proximity;
		}
		
		if (opponentScore >= ENDGAME_THRESHOLD)
		{
			int proximity = opponentScore - ENDGAME_THRESHOLD;
			value -= URGENCY_MULTIPLIER * proximity;
		}
		
		return value;
	}
}
