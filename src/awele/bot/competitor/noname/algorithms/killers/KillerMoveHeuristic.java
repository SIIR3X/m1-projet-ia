package awele.bot.competitor.noname.algorithms.killers;

import awele.bot.competitor.noname.algorithms.heuristics.MoveHeuristic;
import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Heuristique basée sur les killer moves
 * Donne un bonus aux coups qui ont causé des coupes alpha-beta
 */
public class KillerMoveHeuristic implements MoveHeuristic
{
	private static final int WEIGHT = 1;
	
	private final int depth;
	
	/**
	 * Constructeur
	 * @param depth La profondeur courante de la recherce
	 */
	public KillerMoveHeuristic(int depth)
	{
		this.depth = depth;
	}
	
	@Override
	public int evaluate(BitBoard board, int player, int hole)
	{
		return KillerMoveTable.getBonus(depth, hole);
	}
	
	@Override
	public int getWeight()
	{
		return WEIGHT;
	}
}
