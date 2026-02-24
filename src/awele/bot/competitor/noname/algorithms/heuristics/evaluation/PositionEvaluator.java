package awele.bot.competitor.noname.algorithms.heuristics.evaluation;

import awele.bot.competitor.noname.core.bitboard.BitBoard;

/**
 * @author Lucas Fagioli
 * Orchestrateur des heuristiques de position
 */
public final class PositionEvaluator
{
	// ===== Heuristiques utilisées =====
	
	private final ScoreHeuristic scoreHeuristic;
	private final KrouHeuristic krouHeuristic;
	private final ProximityToWinHeuristic proximityToWinHeuristic;
	private final LatentCaptureHeuristic latentCaptureHeuristic;
	private final StarvationThreatHeuristic starvationHeuristic;
	private final ExtraMovesHeuristic extraMovesHeuristic;
	
	// Normalisation de la fitness
	private static final double MAX_SCORE_DIFF = 48.0;
	private static final double MAX_COUNT_DIFF = 6.0;
	private static final double MAX_PROXIMITY_DIFF = 25.0;
	private static final double MAX_LATENT_DIFF = 288.0;
	private static final double MAX_STARVATION_DIFF = 12.0;
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
	 * Identifiants des heuristiques dans l'ordre d'évaluation, pour sérialisation des poids
	 */
	public static final String[] WEIGHT_IDS = {
	    ScoreHeuristic.ID,
	    KrouHeuristic.ID,
	    ProximityToWinHeuristic.ID,
	    LatentCaptureHeuristic.ID,
	    StarvationThreatHeuristic.ID,
	    ExtraMovesHeuristic.ID
	};
	
	/**
	 * Constreucteur avec poids par défaut
	 */
	public PositionEvaluator()
	{
		this(getDefaultPhaseAwareWeights());
	}
	
	/**
	 * Constructeur avec poids personnalisés
	 * @param weights Poids dans l'ordre : score, krou, proximity_to_win, latent_capture, starvation_threat, extra_moves
	 */
	public PositionEvaluator(double[] weights)
	{
		this.scoreHeuristic = new ScoreHeuristic();
		this.krouHeuristic = new KrouHeuristic();
		this.proximityToWinHeuristic = new ProximityToWinHeuristic();
		this.latentCaptureHeuristic = new LatentCaptureHeuristic();
		this.starvationHeuristic = new StarvationThreatHeuristic();
		this.extraMovesHeuristic = new ExtraMovesHeuristic();
		
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
	    final int opponent = 1 - player;
	    final double phase = computePhase(board);

	    double total = 0.0;

	    // Poids dynamiques = phases
	    final double ws = phase * wEarly[0] + (1.0 - phase) * wLate[0];
	    final double wk = phase * wEarly[1] + (1.0 - phase) * wLate[1];
	    final double wp = phase * wEarly[2] + (1.0 - phase) * wLate[2];
	    final double wlc = phase * wEarly[3] + (1.0 - phase) * wLate[3];
	    final double wst = phase * wEarly[4] + (1.0 - phase) * wLate[4];
	    final double wem = phase * wEarly[5] + (1.0 - phase) * wLate[5];

	    // Calcul des différences d'heuristiques entre le joueur et l'adversaire
	    double scoreDiff = scoreHeuristic.evaluate(board, player) - scoreHeuristic.evaluate(board, opponent);
	    double krouDiff = krouHeuristic.evaluate(board, player) - krouHeuristic.evaluate(board, opponent);
	    double proximityDiff = proximityToWinHeuristic.evaluate(board, player) - proximityToWinHeuristic.evaluate(board, opponent);
	    double latDiff = latentCaptureHeuristic.evaluate(board, player) - latentCaptureHeuristic.evaluate(board, opponent);
	    double stDiff = starvationHeuristic.evaluate(board, player) - starvationHeuristic.evaluate(board, opponent);
	    double emDiff = extraMovesHeuristic.evaluate(board, player) - extraMovesHeuristic.evaluate(board, opponent);

	    // Normalisation (éviter domniance)
	    scoreDiff /= MAX_SCORE_DIFF;
	    krouDiff /= MAX_COUNT_DIFF;
	    proximityDiff /= MAX_PROXIMITY_DIFF;
	    latDiff /= MAX_LATENT_DIFF;
	    stDiff /= MAX_STARVATION_DIFF;
	    emDiff /= MAX_EXTRA_MOVES_DIFF;

	    total += ws * scoreDiff;
	    total += wk * krouDiff;
	    total += wp * proximityDiff;
	    total += wlc * latDiff;
	    total += wst * stDiff;
	    total += wem * emDiff;

	    return total;
	}

	/**
	 * Retourne une copie du tableau des poids actuels de l'évaluation
	 * @return Tableau de poids dans l'ordre : score, krou, proximity_to_win, latent_capture, starvation_threat, extra_moves
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
	 * @return Tableau de poids dans l'ordre : score, krou, proximity_to_win, latent_capture, starvation_threat, extra_moves
	 */
	public static double[] getDefautltWeights()
	{
	    return new double[] { 5.0, 0.0, 0.0, 0.0, 0.0, 0.0 };
	}

	/**
	 * Retourne les poids par défaut de l'évaluation pour un évaluateur sensible à la phase de jeu
	 * @return Tableau de poids dans l'ordre : score, krou, proximity_to_win, latent_capture, starvation_threat, extra_moves (early), puis (late)
	 */
	public static double[] getDefaultPhaseAwareWeights()
	{
		final double[] w = getDefautltWeights();
		
		return new double[] {
			w[0], w[1], w[2], w[3], w[4], w[5],
			w[0], w[1], w[2], w[3], w[4], w[5]
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
		
		final double phase = (double)(totalSeeds - END_SEEDS) / (double)(START_SEEDS - END_SEEDS);
		
		return clamp01(phase);
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
}