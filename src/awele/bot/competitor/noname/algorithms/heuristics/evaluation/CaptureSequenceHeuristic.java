package awele.bot.competitor.noname.algorithms.heuristics.evaluation;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.core.bitboard.BitConstants;

/**
 * @author Lucas Fagioli
 * Heuristique de séquences de capture : valorise les longues séquences de trous capturables
 */
public final class CaptureSequenceHeuristic implements PositionHeuristic
{
	public static final String ID = "capture_sequence";
	
	// Bornes de capture
	private static final int CAPTURE_MIN = BitConstants.MIN_CAPTURE;
	private static final int CAPTURE_MAX = BitConstants.MAX_CAPTURE;
	
	// Constantes d'extraction
	private static final int HOLES_OFFSET = BitConstants.HOLES_OFFSET;
	private static final int BITS_PER_HOLE = BitConstants.BITS_PER_HOLE;
	private static final long MASK_6_BITS = BitConstants.MASK_6_BITS;
	private static final int NB_HOLES = BitConstants.NB_HOLES;

	@Override
	public double evaluate(BitBoard board, int player)
	{
		final long raw = (player == 0) ? board.raw0() : board.raw1();
		
		double score = 0.0;
		int currentSequenceLength = 0;
		
		for (int hole = 0; hole < NB_HOLES; hole++)
		{
			final int seeds = (int)((raw >> (HOLES_OFFSET + hole * BITS_PER_HOLE)) & MASK_6_BITS);
			
			if (seeds >= CAPTURE_MIN && seeds <= CAPTURE_MAX)
				currentSequenceLength++;
			else
			{
				if (currentSequenceLength > 0)
				{
					// Valorise fortemenbt les longues séquences
					score += (double)(currentSequenceLength * currentSequenceLength);
					currentSequenceLength = 0;
				}
			}
		}
		
		if (currentSequenceLength > 0)
			score += (double)(currentSequenceLength * currentSequenceLength);
		
		return score;
	}
	
	@Override
	public String getId()
	{
		return ID;
	}
}
