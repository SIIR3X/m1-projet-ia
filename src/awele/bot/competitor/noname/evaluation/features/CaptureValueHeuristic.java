package awele.bot.competitor.noname.evaluation.features;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique 3/6 : Valeur de capture nette
 */
public final class CaptureValueHeuristic implements PositionHeuristic
{
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final int opponent = 1 - player;
		
		int playerCaptureValue = calculateCaptureValue(board, player);
		int opponentCaptureValue = calculateCaptureValue(board, opponent);
		
		return playerCaptureValue - opponentCaptureValue;
	}
	
	/**
	 * Calcule la valeur totale de capture pour un joueur donné
	 * @param board l'état du plateau
	 * @param player le joueur pour lequel calculer la valeur de capture
	 * @return la valeur totale de capture (nombre de graines capturées si le joueur joue tous ses coups valides)
	 */
	private static int calculateCaptureValue(BitBoard board, int player)
	{
		final boolean[] valid = board.getValidMoves(player);
		int captureValue = 0;
		
		for (int hole = 0; hole < valid.length; hole++)
		{
			if (!valid[hole])
				continue;
			
			int captured = board.simulateMoveScore(player, hole);
			if (captured > 0)
				captureValue += captured;
		}
		
		return captureValue;
	}
}
