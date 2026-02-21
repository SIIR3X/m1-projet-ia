package awele.bot.competitor.noname.algorithms.training.cmaes;

/**
 * @author Lucas Fagioli
 * Configuration pour l'algorithme CMA-ES
 * Contient uniquement les paramètres de configuration, pas d'état d'exécution
 */
public final class CMAESConfig
{
	/**
	 * Point de départ de la recherche (paramètres initiaux)
	 * Pour moi c'est les poids du PositionEvaluator, mais on peut imaginer d'autres choses
	 */
	public final double[] xmean;
	
	/**
	 * Écart-type initial (step size)
	 */
	public final double sigma0;
	
	/**
	 * Nombre maximum d'évaluations de la fonction objectif (budget d'apprentissage)
	 * Correspond à stopeval dans l'algorithme de Wikipédia
	 * Ici pas besoin (car budget de temps à la place), mais je le laisse pour rester fidèle à l'algorithme original
	 */
	public final long stopEval;
	
	/**
	 * Budget temps maximum pour l'apprentissage (en ms)
	 * Pour moi il va remplacer le criètre d'arrêt stopEval
	 */
	public final long timeBudgetMs;
	
	/**
	 * Nombre de parties jouées pour estimer la fitness d'un individu
	 * Plus c'est élevé, moins le bruit est important mais du coup plus long
	 */
	public final int nbGamesPerEval;
	
	/**
	 * Profondeur de recherche utilisée pendant l'entraînement
	 * Plus c'est élevé, plus les évaluations sont précises mais plus c'est long
	 */
	public final int trainingDepth;
	
	/**
	 * Bornes des poids
	 * Les individus sont clippés dans cet intervalle après évaluation
	 */
	public final double weightMin;
	public final double weightMax;
	
	public CMAESConfig(
		double[] xmean,
		double sigma0,
		long stopEval,
		long timeBudgetMs,
		int nbGamesPerEval,
		int trainingDepth,
		double weightMin,
		double weightMax)
	{
		this.xmean = xmean;
		this.sigma0 = sigma0;
		this.stopEval = stopEval;
		this.timeBudgetMs = timeBudgetMs;
		this.nbGamesPerEval = nbGamesPerEval;
		this.trainingDepth = trainingDepth;
		this.weightMin = weightMin;
		this.weightMax = weightMax;
	}
	
	/**
	 * Config par défaut
	 * @param initialWeights Poids initiaux pour la recherche (xmean)
	 * @param timeBudgetMs Budget de temps total pour l'apprentissage (en ms)
	 * @return Config par défaut pour CMA-ES, avec des paramètres raisonnables (j'espère) pour l'entraînement d'un bot de Awélé
	 */
	public static CMAESConfig defaultConfig(double[] initialWeights, long timeBudgetMs)
	{
		final double wMin = -20.0;
		final double wMax = 20.0;
		final double sigma0 = (wMax - wMin) / 3.0;
		final long stopEval = 86L;
		
		return new CMAESConfig(
			initialWeights,
			sigma0,
			stopEval,
			timeBudgetMs,
			16,
			6,
			wMin,
			wMax
		);
	}
}
