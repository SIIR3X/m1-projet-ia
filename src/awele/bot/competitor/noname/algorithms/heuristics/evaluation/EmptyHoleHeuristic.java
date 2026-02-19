package awele.bot.competitor.noname.algorithms.heuristics.evaluation;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitConstants;

/**
 * @author Lucas Fagioli
 * Heuristique de trous vides : comptabilise les trous vides du joueur
 */
public final class EmptyHoleHeuristic implements PositionHeuristic
{
	public static final String ID = "empty_hole";
	
	// Constantes d'extraction
	private static final int HOLES_OFFSET = BitConstants.HOLES_OFFSET;
	private static final int BITS_PER_HOLE = BitConstants.BITS_PER_HOLE;
	private static final long MASK_6_BITS = BitConstants.MASK_6_BITS;
	private static final int NB_HOLES = BitConstants.NB_HOLES;
	
	private double weight;
	
	public EmptyHoleHeuristic(double weight)
	{
		this.weight = weight;
	}
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final long raw = (player == 0) ? board.raw0() : board.raw1();
		
		int emptyCount = 0;
		
		for (int hole = 0; hole < NB_HOLES; hole++)
		{
			final int seeds = (int)((raw >> (HOLES_OFFSET + hole * BITS_PER_HOLE)) & MASK_6_BITS);
			
			if (seeds == 0)
				emptyCount++;
		}
		
		return (double)emptyCount;
	}
	
	@Override
	public double getWeight()
	{
		return weight;
	}
	
	@Override
	public void setWeight(double weight)
	{
		this.weight = weight;
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
