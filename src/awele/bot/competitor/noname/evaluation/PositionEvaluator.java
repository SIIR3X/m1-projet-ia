package awele.bot.competitor.noname.evaluation;

import awele.bot.competitor.noname.core.bitboard.BitBoard;
import awele.bot.competitor.noname.evaluation.features.AntiCaptureHeuristic;
import awele.bot.competitor.noname.evaluation.features.CapturePotentialHeuristic;
import awele.bot.competitor.noname.evaluation.features.FamineSafetyHeuristic;
import awele.bot.competitor.noname.evaluation.features.MobilityHeuristic;
import awele.bot.competitor.noname.evaluation.features.ScoreDifferenceHeuristic;

/**
 * @author Lucas Fagioli
 * Orchestrateur des heuristiques de position
 */
public final class PositionEvaluator
{
	// ===== Heuristiques utilisées =====
	
    private final ScoreDifferenceHeuristic scoreDifferenceHeuristic;
    private final MobilityHeuristic mobilityHeuristic;
    private final CapturePotentialHeuristic capturePotentialHeuristic;
    private final AntiCaptureHeuristic antiCaptureHeuristic;
    private final FamineSafetyHeuristic famineSafetyHeuristic;
	
	// Normalisation de la fitness
    private static final double MAX_SCORE_DIFF = 25.0;
    private static final double MAX_MOBILITY_DIFF = 6.0;
    private static final double MAX_CAPTURE_POT_DIFF = 6.0;
    private static final double MAX_ANTI_CAPTURE_DIFF = 18.0;
    private static final double MAX_FAMINE_THREATS_DIFF = 6.0;

	/**
	 * Nombre d'heuristiques utilisées, pour validation des poids
	 */
	private static final int N = 5;
	
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
        ScoreDifferenceHeuristic.ID,
        MobilityHeuristic.ID,
        CapturePotentialHeuristic.ID,
        AntiCaptureHeuristic.ID,
        FamineSafetyHeuristic.ID
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
	 * @param weights Poids dans l'ordre : score, mobility, capture_potential, anti_capture, famine_safety
	 */
	public PositionEvaluator(double[] weights)
	{
        this.scoreDifferenceHeuristic = new ScoreDifferenceHeuristic();
        this.mobilityHeuristic = new MobilityHeuristic();
        this.capturePotentialHeuristic = new CapturePotentialHeuristic();
        this.antiCaptureHeuristic = new AntiCaptureHeuristic();
        this.famineSafetyHeuristic = new FamineSafetyHeuristic();
		
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

	    // Poids dynamiques = phases
        final double ws  = phase * wEarly[0] + (1.0 - phase) * wLate[0];
        final double wm  = phase * wEarly[1] + (1.0 - phase) * wLate[1];
        final double wcp = phase * wEarly[2] + (1.0 - phase) * wLate[2];
        final double wac = phase * wEarly[3] + (1.0 - phase) * wLate[3];
        final double wfs = phase * wEarly[4] + (1.0 - phase) * wLate[4];

	    // Calcul des différences d'heuristiques entre le joueur et l'adversaire
        double scoreDiff = scoreDifferenceHeuristic.evaluate(board, player) - scoreDifferenceHeuristic.evaluate(board, opponent);
        double mobilityDiff = mobilityHeuristic.evaluate(board, player) - mobilityHeuristic.evaluate(board, opponent);
        double capturePotDiff = capturePotentialHeuristic.evaluate(board, player) - capturePotentialHeuristic.evaluate(board, opponent);
        double antiCaptureDiff = antiCaptureHeuristic.evaluate(board, player) - antiCaptureHeuristic.evaluate(board, opponent);
        double famineSafetyDiff = famineSafetyHeuristic.evaluate(board, player) - famineSafetyHeuristic.evaluate(board, opponent);

	    // Normalisation (éviter domniance)
        scoreDiff /= MAX_SCORE_DIFF;
        mobilityDiff /= MAX_MOBILITY_DIFF;
        capturePotDiff /= MAX_CAPTURE_POT_DIFF;
        antiCaptureDiff /= MAX_ANTI_CAPTURE_DIFF;
        famineSafetyDiff /= MAX_FAMINE_THREATS_DIFF;

        scoreDiff = clamp11(scoreDiff);
        mobilityDiff = clamp11(mobilityDiff);
        capturePotDiff = clamp11(capturePotDiff);
        antiCaptureDiff = clamp11(antiCaptureDiff);
        famineSafetyDiff = clamp11(famineSafetyDiff);
        
        double total = 0.0;
        total += ws  * scoreDiff;
        total += wm  * mobilityDiff;
        total += wcp * capturePotDiff;
        total += wac * antiCaptureDiff;
        total += wfs * famineSafetyDiff;

	    return total;
	}

	/**
	 * Retourne une copie du tableau des poids actuels de l'évaluation
	 * @return Tableau de poids dans l'ordre : score, mobility, capture_potential, anti_capture, famine_safety
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
	 * @return Tableau de poids dans l'ordre : score, mobility, capture_potential, anti_capture, famine_safety
	 */
	public static double[] getDefautltWeights()
	{
        return new double[] { 11, 11, 0, 6, 8, 15, 2, 4, 3, 9 };
	}

	/**
	 * Retourne les poids par défaut de l'évaluation pour un évaluateur sensible à la phase de jeu
	 * @return Tableau de poids dans l'ordre : score, mobility, capture_potential, anti_capture, famine_safety
	 */
	public static double[] getDefaultPhaseAwareWeights()
	{
		final double[] w = getDefautltWeights();
		
		return new double[] {
			w[0], w[1], w[2], w[3], w[4],
			w[0], w[1], w[2], w[3], w[4]
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