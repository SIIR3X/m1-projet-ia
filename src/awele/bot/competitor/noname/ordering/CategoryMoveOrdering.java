package awele.bot.competitor.noname.ordering;

import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 */
public final class CategoryMoveOrdering
{
	/**
	 * Nombre max de graines sur le plateau
	 */
	private static final int MAX_SEEDS_PLUS_ONE = 49;
	
	/**
	 * Nombre de catégories
	 */
	private static final int CATEGORY_COUNT = NB_HOLES * MAX_SEEDS_PLUS_ONE * MAX_SEEDS_PLUS_ONE;
	
	private static final int MIN_CAT_SCORE = -5_000;
	private static final int MAX_CAT_SCORE = 5_000;
	
	/**
	 * Scores de chaque catégorie
	 */
	private static final int[] categoryScores = new int[CATEGORY_COUNT];

	public static boolean LEARNING_ENABLED = false;
	
	private CategoryMoveOrdering() {}
	
	public static int category(BitBoard board, int currentPlayer, int startHole)
	{
		final int seedsStart = clampSeeds(board.getSeeds(currentPlayer, startHole));
		
		final int startAbs = currentPlayer * NB_HOLES + startHole;
		final int destAbs = landingPitAbsIndex(startAbs, seedsStart);
		
		final int destPlayer = destAbs / NB_HOLES;
		final int destHole = destAbs % NB_HOLES;
		
		final int seedsArrive = clampSeeds(board.getSeeds(destPlayer, destHole));
		
		// Formule inspirée de Mathis Saillot
		// cf son rapport
		final int cat = startHole + NB_HOLES * seedsStart + NB_HOLES * MAX_SEEDS_PLUS_ONE * seedsArrive;
		
		return (cat < 0) ? 0 : (cat >= CATEGORY_COUNT) ? (CATEGORY_COUNT - 1) : cat;
	}
	
	public static int score(int category)
	{
		return categoryScores[category];
	}
	
	public static void addScore(int category, int score)
	{
		if (!LEARNING_ENABLED)
			return;
		
		int v = categoryScores[category] + score;
		if (v < MIN_CAT_SCORE)
			v = MIN_CAT_SCORE;
		else if (v > MAX_CAT_SCORE)
			v = MAX_CAT_SCORE;
		
		categoryScores[category] = v;
	}
	
	public static void removeScore(int category, int score)
	{
		if (!LEARNING_ENABLED)
			return;
		
		int v = categoryScores[category] - score;
		if (v < MIN_CAT_SCORE)
			v = MIN_CAT_SCORE;
		else if (v > MAX_CAT_SCORE)
			v = MAX_CAT_SCORE;
		
		categoryScores[category] = v;
	}
	
	public static void resetScores()
	{
		for (int i = 0; i < CATEGORY_COUNT; i++)
		{
			categoryScores[i] = 0;
		}
	}
	
	private static int clampSeeds(int seeds)
	{
		if (seeds < 0)
			return 0;
		else if (seeds >= MAX_SEEDS_PLUS_ONE)
			return MAX_SEEDS_PLUS_ONE - 1;
		return seeds;
	}
	
	private static int landingPitAbsIndex(int startAbs, int seeds)
	{
		if (seeds == 0)
			return startAbs;
		
		int pos = startAbs;
		int remaining = seeds;
		
		final boolean skipStartOnWrap = (seeds > 11);
		
		while (remaining > 0)
		{
			pos = (pos + 1) % (2 * NB_HOLES);
			
			if (skipStartOnWrap && pos == startAbs)
				pos = (pos + 1) % (2 * NB_HOLES);
			
			remaining--;
		}
		
		return pos;
	}
}
