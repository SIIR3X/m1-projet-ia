package awele.bot.competitor.noname.evaluation.features;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique 4/5 : anti-capture, c'est à dire le nombre de graines que l'adversaire pourrait capturer au prochain tour,
 * en supposant qu'il joue le meilleur coup pour capturer.
 */
public final class AntiCaptureHeuristic implements PositionHeuristic
{
	public static final String ID = "anti_capture";
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
	    final int opponent = 1 - player;

	    final boolean[] validOpp = board.getValidMoves(opponent);
	    int maxCapturedByOpponent = 0;

	    for (int hole = 0; hole < validOpp.length; hole++)
	    {
	        if (!validOpp[hole])
	            continue;

	        int capturedByOpponent = board.simulateMoveScore(opponent, hole);
	        if (capturedByOpponent > maxCapturedByOpponent)
	            maxCapturedByOpponent = capturedByOpponent;
	    }

	    // Opposé = risque (plus l'adversaire peut capturer, pire c'est)
	    return -maxCapturedByOpponent;
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
