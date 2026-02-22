package awele.bot.competitor.noname.algorithms.heuristics.evaluation;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.MASK_3_BITS;
import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_MOVES_OFFSET;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Heuristique de capture latente : valorise les coups qui permettraient de capturer des graines à l'adversaire
 * même si ce n'est pas le cas actuellement
 */
public final class LatentCaptureHeuristic implements PositionHeuristic
{
	public static final String ID = "latent_capture";
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final long raw = (player == 0) ? board.raw0() : board.raw1();
		final int nbMoves = (int)((raw >> NB_MOVES_OFFSET) & MASK_3_BITS);
		
		if (nbMoves == 0)
			return 0.0;
		
		final boolean[] validMoves = board.getValidMoves(player);
		double totalCaptures = 0.0;
		
		for (int hole = 0; hole < validMoves.length; hole++)
		{
			if (!validMoves[hole])
				continue;
			
			final int captured = board.simulateMoveScore(player, hole);
			
			if (captured > 0)
				totalCaptures += captured;
		}
		
		return totalCaptures;
	}

	@Override
	public String getId()
	{
		return ID;
	}
}
