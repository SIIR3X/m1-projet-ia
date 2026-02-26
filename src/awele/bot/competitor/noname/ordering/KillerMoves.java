package awele.bot.competitor.noname.ordering;

import java.util.Arrays;

public final class KillerMoves
{
	/**
	 * Tableau des killer moves, indexé par profondeur
	 */
	private static int[][] killerMoves = new int[0][0];
	
	private KillerMoves() {}
	
	public static void initialize(int maxDepth)
	{
		if (maxDepth < 0)
			maxDepth = 0;
		
		if (killerMoves.length == maxDepth + 1)
		{
			clear();
			return;
		}
		
		killerMoves = new int[maxDepth + 1][2];
		clear();
	}
	
	public static void clear()
	{
		for (int i = 0; i < killerMoves.length; i++)
		{
			Arrays.fill(killerMoves[i], -1);
		}
	}
	
	public static void record(int depthRemaining, int move)
	{
		if (depthRemaining < 0 || depthRemaining >= killerMoves.length)
			return;
		
		final int k0 = killerMoves[depthRemaining][0];
		final int k1 = killerMoves[depthRemaining][1];
		
		if (move == k0 || move == k1)
			return;
		
		killerMoves[depthRemaining][1] = k0;
		killerMoves[depthRemaining][0] = move;
	}
	
	public static int get(int depthRemaining, int index)
	{
		if (depthRemaining < 0 || depthRemaining >= killerMoves.length)
			return -1;
		
		if (index < 0 || index > 1)
			return -1;
		
		return killerMoves[depthRemaining][index];
	}
}
