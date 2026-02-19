package awele.bot.competitor.noname.algorithms.heuristics.evaluation;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitConstants;

/**
 * @author Lucas Fagioli
 * Heuristique de trou dangereux : comptabilise les trous contenant 1 ou 2 graines, qui sont des cibles faciles pour l'adversaire
 */
public final class DangerousHoleHeuristic implements PositionHeuristic
{
	public static final String ID = "dangerous_hole";
	
	// Les bornes de définition d'un trou dangereux
	private static final int DANGER_MIN = 1;
	private static final int DANGER_MAX = 2;
	
	// Constantes d'extraction
	private static final int HOLES_OFFSET = BitConstants.HOLES_OFFSET;
	private static final int BITS_PER_HOLE = BitConstants.BITS_PER_HOLE;
	private static final long MASK_6_BITS = BitConstants.MASK_6_BITS;
	private static final int NB_HOLES = BitConstants.NB_HOLES;
		
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final long raw = (player == 0) ? board.raw0() : board.raw1();
		
		int dangerousCount = 0;
		
		for (int hole = 0; hole < NB_HOLES; hole++)
		{
			final int seeds = (int)((raw >> (HOLES_OFFSET + hole * BITS_PER_HOLE)) & MASK_6_BITS);
			
			if (seeds >= DANGER_MIN && seeds <= DANGER_MAX)
				dangerousCount++;
		}
		
		return (double)dangerousCount;
	}

	@Override
	public String getId()
	{
		return ID;
	}
}
