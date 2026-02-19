package awele.bot.competitor.noname.algorithms.training;

/**
 * @author Lucas Fagioli
 * Configuration de l'algorithme SPSA
 */
public final class SPSAConfig
{
	// ===== Paramètres de décroissance =====
	
	/**
	 * Exposant de décroissance du taux d'apprentissage
	 */
	public final double alpha;
	
	/**
	 * Exposant de décroissance du taux de perturbation
	 */
	public final double gamma;
	
	// ===== Paramètres de calibraton =====
	
	/**
	 * Amplitude initiale du taux d'apprentissage
	 */
	public final double a;
	
	/**
	 * Amplitude initiale du taux de perturbation
	 */
	public final double c;
	
	/**
	 * Paramètre de stabilité
	 */
	public final double A;
	
	// ===== Paramètres de simulation =====
	
	/**
	 * Nombre de parties à simuler à chaque évaluation
	 */
	public final int nbGamesPerEstimate;
	
	/**
	 * Profondeur de recherche pendant l'entraînement
	 */
	public final int trainingDepth;
	
	/**
	 * Temps max pour l'entraînement
	 */
	public final long timeBudgetMs;
	
	/**
	 * Poids minimum et maximum pour les paramètres entraînés
	 */
	public final double weightMin;
	public final double weightMax;
	
	public SPSAConfig(
		double alpha,
		double gamma,
		double a,
		double c,
		double A,
		int nbGamesPerEstimate,
		int trainingDepth,
		long timeBudgetMs,
		double weightMin,
		double weightMax)
	{
		this.alpha = alpha;
		this.gamma = gamma;
		this.a = a;
		this.c = c;
		this.A = A;
		this.nbGamesPerEstimate = nbGamesPerEstimate;
		this.trainingDepth = trainingDepth;
		this.timeBudgetMs = timeBudgetMs;
		this.weightMin = weightMin;
		this.weightMax = weightMax;
	}
	
	/**
	 * Calcule le taux d'apprentissage à l'itération k
	 * @param k L'itération courante
	 * @return Le taux d'apprentissage à utiliser pour l'itération k
	 */
	public double computeAk(int k)
	{
		return a / Math.pow(k + 1.0 + A, alpha);
	}
	
	/**
	 * Calcule le taux de perturbation à l'itération k
	 * @param k L'itération courante
	 * @return Le taux de perturbation à utiliser pour l'itération k
	 */
	public double computeCk(int k)
	{
		return c / Math.pow(k + 1.0, gamma);
	}
}
