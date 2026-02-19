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
	private final DangerousHoleHeuristic dangerousHoleHeuristic;
	private final EmptyHoleHeuristic emptyHoleHeuristic;
	private final CaptureSequenceHeuristic captureSequenceHeuristic;
	
	private final PositionHeuristic[] heuristics;

	public static final String[] WEIGHT_IDS = {
		ScoreHeuristic.ID,
		KrouHeuristic.ID,
		DangerousHoleHeuristic.ID,
		EmptyHoleHeuristic.ID,
		CaptureSequenceHeuristic.ID
	};
	
	/**
	 * Constreucteur avec poids par défaut (Joan Sala / JADT)
	 */
	public PositionEvaluator()
	{
		this(new double[] { 10.0, 2.8, -5.4, -3.6, 2.0 });
	}
	
	/**
	 * Constructeur avec poids personnalisés
	 * @param weights Poids dans l'ordre : score, krou, trous dangereux, trous vides, séquences de capture
	 */
	public PositionEvaluator(double[] weights)
	{
		if (weights.length != WEIGHT_IDS.length)
			throw new IllegalArgumentException("Les poids doivent être fournis pour toutes les heuristiques");
		
		this.scoreHeuristic = new ScoreHeuristic(weights[0]);
		this.krouHeuristic = new KrouHeuristic(weights[1]);
		this.dangerousHoleHeuristic = new DangerousHoleHeuristic(weights[2]);
		this.emptyHoleHeuristic = new EmptyHoleHeuristic(weights[3]);
		this.captureSequenceHeuristic = new CaptureSequenceHeuristic(weights[4]);
		
		this.heuristics = new PositionHeuristic[] {
			scoreHeuristic,
			krouHeuristic,
			dangerousHoleHeuristic,
			emptyHoleHeuristic,
			captureSequenceHeuristic
		};
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
		double total = 0.0;
		
		for (final PositionHeuristic heuristic : heuristics)
		{
			final double weight = heuristic.getWeight();
			total += weight * (heuristic.evaluate(board, player) - heuristic.evaluate(board, opponent));
		}
		
		return total;
	}
	
	/**
	 * Retourne une copie du tableau des poids actuels de l'évaluation
	 * @return Tableau de poids dans l'ordre : score, krou, trous dangereux, trous vides, séquences de capture
	 */
	public double[] getWeights()
	{
		final double[] weights = new double[heuristics.length];
		
		for (int i = 0; i < heuristics.length; i++)
			weights[i] = heuristics[i].getWeight();
		
		return weights;
	}
	
	/**
	 * Met à jour les poids de toutes les heuristiques
	 * @param weights Poids dans l'ordre : score, krou, trous dangereux, trous vides, séquences de capture
	 */
	public void setWeights(double[] weights)
	{
		if (weights.length != WEIGHT_IDS.length)
			throw new IllegalArgumentException("Les poids doivent être fournis pour toutes les heuristiques");
		
		for (int i = 0; i < heuristics.length; i++)
			heuristics[i].setWeight(weights[i]);
	}
	
	public int getNbWeights()
	{
		return heuristics.length;
	}
	
	/**
	 * Retourne les poids par défaut de l'évaluation (Joan Sala / JADT)
	 * @return Tableau de poids dans l'ordre : score, krou, trous dangereux, trous vides, séquences de capture
	 */
	public static double[] getDefautltWeights()
	{
		return new double[] { 10.0, 2.8, -5.4, -3.6, 2.0 };
	}
}
