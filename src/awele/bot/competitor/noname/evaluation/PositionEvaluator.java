package awele.bot.competitor.noname.evaluation;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.features.CaptureValueHeuristic;
import awele.bot.competitor.noname.evaluation.features.EndgameProximityHeuristic;
import awele.bot.competitor.noname.evaluation.features.ExtraMovesPotentialHeuristic;
import awele.bot.competitor.noname.evaluation.features.FamineSafetyHeuristic;
import awele.bot.competitor.noname.evaluation.features.MobilityHeuristic;
import awele.bot.competitor.noname.evaluation.features.ScoreDifferenceHeuristic;

/**
 * @author Lucas Fagioli
 * Orchestrateur des heuristiques de position
 */
public final class PositionEvaluator
{
	private final ScoreDifferenceHeuristic scoreDiffHeuristic;
	private final MobilityHeuristic mobilityHeuristic;
	private final CaptureValueHeuristic captureValueHeuristic;
	private final FamineSafetyHeuristic famineSafetyHeuristic;
	private final EndgameProximityHeuristic endgameProximityHeuristic;
	private final ExtraMovesPotentialHeuristic extraMovesHeuristic;

	// Normalisation de la fitness
	private static final double MAX_SCORE_DIFF = 25.0;
	private static final double MAX_MOBILITY_DIFF = 6.0;
	private static final double MAX_CAPTURE_VALUE_DIFF = 30.0;
	private static final double MAX_FAMINE_THREATS_DIFF = 6.0;
	private static final double MAX_ENDGAME_PROXIMITY = 15.0;
	private static final double MAX_EXTRA_MOVES_DIFF = 6.0;

	/**
	 * Nombre d'heuristiques utilisées, pour validation des poids
	 */
	private static final int N = 6;
	
	/**
	 * Poids pour chaque heuristique en début de partie (early + mid)
	 */
	private final double[] wEarly = new double[N];
	
	/**
	 * Poids pour chaque heuristique en fin de partie (endgame)
	 */
	private final double[] wLate = new double[N];
	
	/**
	 * Constreucteur avec poids par défaut
	 */
	public PositionEvaluator()
	{
		this(getDefaultWeights());
	}
	
	/**
	 * Constructeur avec poids personnalisés
	 * @param weights Poids dans l'ordre
	 */
	public PositionEvaluator(double[] weights)
	{
		this.scoreDiffHeuristic = new ScoreDifferenceHeuristic();
		this.mobilityHeuristic = new MobilityHeuristic();
		this.captureValueHeuristic = new CaptureValueHeuristic();
		this.famineSafetyHeuristic = new FamineSafetyHeuristic();
		this.endgameProximityHeuristic = new EndgameProximityHeuristic();
		this.extraMovesHeuristic = new ExtraMovesPotentialHeuristic();

		setWeights(weights);
	}
	
	/**
	 * Évalue la position actuelle du plateau pour le joueur donné, en combinant les différentes heuristiques avec leurs poids respectifs
	 * @param board Le plateau de jeu à évaluer
	 * @param player Le numéro du joueur pour lequel on évalue la position (0 ou 1)
	 * @return Une évaluation numérique de la position, plus c'est élevé, mieux c'est pour le joueur
	 */
	public double evaluate(BitBoard board, int player)
	{
	    final double phase = computePhase(board);

		// Interpole les poids selon la phase
		final double w0 = lerp(wEarly[0], wLate[0], phase);
		final double w1 = lerp(wEarly[1], wLate[1], phase);
		final double w2 = lerp(wEarly[2], wLate[2], phase);
		final double w3 = lerp(wEarly[3], wLate[3], phase);
		final double w4 = lerp(wEarly[4], wLate[4], phase);
		final double w5 = lerp(wEarly[5], wLate[5], phase);

		// Évaluation des heuristiques
		double scoreDiff = scoreDiffHeuristic.evaluate(board, player);
		double mobilityDiff = mobilityHeuristic.evaluate(board, player);
		double captureValueDiff = captureValueHeuristic.evaluate(board, player);
		double famineSafetyDiff = famineSafetyHeuristic.evaluate(board, player);
		double endgameProximity = endgameProximityHeuristic.evaluate(board, player);
		double extraMovesDiff = extraMovesHeuristic.evaluate(board, player);

		// Normalisation dans [-1, 1]
		scoreDiff = clamp11(scoreDiff / MAX_SCORE_DIFF);
		mobilityDiff = clamp11(mobilityDiff / MAX_MOBILITY_DIFF);
		captureValueDiff = clamp11(captureValueDiff / MAX_CAPTURE_VALUE_DIFF);
		famineSafetyDiff = clamp11(famineSafetyDiff / MAX_FAMINE_THREATS_DIFF);
		endgameProximity = clamp11(endgameProximity / MAX_ENDGAME_PROXIMITY);
		extraMovesDiff = clamp11(extraMovesDiff / MAX_EXTRA_MOVES_DIFF);

		// Somme pondérée
		double total = 0.0;
		total += w0 * scoreDiff;
		total += w1 * mobilityDiff;
		total += w2 * captureValueDiff;
		total += w3 * famineSafetyDiff;
		total += w4 * endgameProximity;
		total += w5 * extraMovesDiff;

		return total;
	}

	/**
	 * Retourne une copie du tableau des poids actuels de l'évaluation
	 * @return Tableau de poids dans l'ordre
	 */
	public double[] getWeights()
	{
		final double[] weights = new double[2 * N];
		
		System.arraycopy(wEarly, 0, weights, 0, N);
		System.arraycopy(wLate, 0, weights, N, N);
		
		return weights;
	}
	
	/**
	 * Met à jour les poids de toutes les heuristiques
	 * @param weights Poids dans l'ordre : score, krou, proximity_to_win, latent_capture, starvation_threat, extra_moves
	 */
	public void setWeights(double[] weights)
	{
		if (weights.length == N)
		{
			System.arraycopy(weights, 0, wEarly, 0, N);
			System.arraycopy(weights, 0, wLate, 0, N);
		}
		else if (weights.length == N * 2)
		{
			System.arraycopy(weights, 0, wEarly, 0, N);
			System.arraycopy(weights, N, wLate, 0, N);
		}
		else
			throw new IllegalArgumentException("Erreur");
	}
	
	public int getNbWeights()
	{
		return N * 2;
	}
	
	/**
	 * Retourne les poids par défaut de l'évaluation
	 * 
	 * Point de départ pour l'entraînement
	 * Seront écrasée assez vite (sigma = 0.8, variation rapide sur [0 ; 15]
	 * Peut être arrondi à .0 change pas grand chose
	 * 
	 * Idée de départ :
	 * - Score diff : très important en fin de partie, moins en début
	 * - Mobilité : plus important en début de partie, moins en fin
	 * - Capture value : important tout au long de la partie, un peu plus en fin
	 * - Famine safety : important tout au long de la partie, un peu plus en début
	 * - Proximity to win : pas important en début de partie, très important en fin de partie
	 * - Extra moves : plus important en début de partie, moins en fin
	 */
	public static double[] getDefaultWeights()
	{
	    return new double[] {
	        // Early game
	        10.1, // ScoreDifference
	        7.9, // Mobility
	        4.6, // CaptureValue
	        8.0, // FamineSafety
	        4.6, // EndgameProximity
	        1.4, // ExtraMoves

	        // Late game
	        11.0, // ScoreDifference
	        0.7, // Mobility
	        5.8, // CaptureValue
	        10.6, // FamineSafety
	        7.8, // EndgameProximity
	        0.5 // ExtraMoves
	    };
	}
	
	/**
	 * Calcule la phase de la partie en fonction du nombre total de graines restantes sur le plateau
	 * @param board Le plateau de jeu à évaluer
	 * @return Un nombre entre 0.0 (début de partie) et 1.0 (fin de partie) représentant la phase actuelle de la partie
	 */
	private static double computePhase(BitBoard board)
	{
	    final int totalSeeds = board.getTotalSeeds(0) + board.getTotalSeeds(1);

	    final int START_SEEDS = 48;
	    final int END_SEEDS = 12;

	    final double phase = (double)(START_SEEDS - totalSeeds) / (double)(START_SEEDS - END_SEEDS);

	    return clamp01(phase);
	}
	
	/**
	 * Interpolation linéaire
	 */
	private static double lerp(double a, double b, double t)
	{
		return a + (b - a) * t;
	}
	
	/**
	 * Clamp une valeur entre 0.0 et 1.0
	 * @param x La valeur à clamp
	 * @return 0.0 si x < 0.0, 1.0 si x > 1.0, sinon x
	 */
	private static double clamp01(double x)
	{
		if (x < 0.0)
			return 0.0;
		if (x > 1.0)
			return 1.0;
		return x;
	}
	
	/**
	 * Clamp une valeur entre -1.0 et 1.0
	 * @param x La valeur à clamp
	 * @return -1.0 si x < -1.0, 1.0 si x > 1.0, sinon x
	 */
	private static double clamp11(double x)
	{
		if (x < -1.0)
			return -1.0;
		if (x > 1.0)
			return 1.0;
		return x;
	}
}