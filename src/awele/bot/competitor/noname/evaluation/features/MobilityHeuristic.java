package awele.bot.competitor.noname.evaluation.features;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.PositionHeuristic;

/**
 * @author Lucas Fagioli
 * Heuristique 2/7 : Différence de mobilité (nombre de coups valides du joueur - nombre de coups valides de l'adversaire)
 */
public final class MobilityHeuristic implements PositionHeuristic
{
	public static final String ID = "mobility";
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final int opponent = 1 - player;
		
		final boolean[] playerMoves = board.getValidMoves(player);
		final boolean[] opponentMoves = board.getValidMoves(opponent);
		
		int playerMobility = 0;
		int opponentMobility = 0;
		
		for (int i = 0; i < playerMoves.length; i++)
		{
			if (playerMoves[i])
				playerMobility++;
			if (opponentMoves[i])
				opponentMobility++;
		}
		
		return playerMobility - opponentMobility;
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
