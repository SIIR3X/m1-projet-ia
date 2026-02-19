package awele.bot.competitor.noname.algorithms.heuristics.evaluation;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitConstants;

/**
 * @author Lucas Fagioli
 * Heuristique des Krous : comptabilise les trous contenant 12 graines ou plus
 */
public final class KrouHeuristic implements PositionHeuristic
{
	public static final String ID = "krou";
	
	// Seuil ou on définitit un Krou
	private static final int KROU_THRESHOLD = 12;
	
	// Constantes d'extraction des trous depuis le bitboard
	private static final int HOLES_OFFSET = BitConstants.HOLES_OFFSET;
	private static final int BITS_PER_HOLE = BitConstants.BITS_PER_HOLE;
	private static final long MASK_6_BITS = BitConstants.MASK_6_BITS;
	private static final int NB_HOLES = BitConstants.NB_HOLES;
	
	private double weight;
	
	public KrouHeuristic(double weight)
	{
		this.weight = weight;
	}
	
	@Override
	public double evaluate(BitBoard board, int player)
	{
		final long raw = (player == 0) ? board.raw0() : board.raw1();
		
		int krouCount = 0;
		
		for (int hole = 0; hole < NB_HOLES; hole++)
		{
			final int seeds = (int)((raw >> (HOLES_OFFSET + hole * BITS_PER_HOLE)) & MASK_6_BITS);
			
			if (seeds >= KROU_THRESHOLD)
				krouCount++;
		}
		
		return (double)krouCount;
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
