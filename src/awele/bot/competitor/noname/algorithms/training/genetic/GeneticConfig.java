package awele.bot.competitor.noname.algorithms.training.genetic;

/**
 * @author Lucas Fagioli
 * Configuration de l'algorithme génétique pour l'initialisation des poids
 */
public final class GeneticConfig
{
	// ===== Paramètres de population =====

	public final int populationSize;
	public final int nbGenerations;
	public final double elitismRate;

	// ===== Paramètres génétiques =====

	public final double mutationRate;
	public final double mutationAmplitude;

	// ===== Paramètres d'évaluation =====

	/**
	 * Nombre de parties jouées par individu contre les profils normaux.
	 * En plus, BotEvaluator joue toujours 2 parties contre le BOSS.
	 */
	public final int nbGamesPerEval;

	/**
	 * Profondeur de recherche du candidat et des profils normaux.
	 * Le BOSS utilise AdaptiveOpponentSelector.BOSS_DEPTH (fixe, indépendant).
	 */
	public final int evalDepth;

	// ===== Bornes des poids =====

	public final double weightMin;
	public final double weightMax;

	// ===== Budget temps =====

	public final long timeBudgetMs;

	public GeneticConfig(
		int populationSize,
		int nbGenerations,
		double elitismRate,
		double mutationRate,
		double mutationAmplitude,
		int nbGamesPerEval,
		int evalDepth,
		double weightMin,
		double weightMax,
		long timeBudgetMs)
	{
		if (populationSize < 2)
			throw new IllegalArgumentException("La population doit contenir au moins 2 individus.");
		if (elitismRate <= 0.0 || elitismRate >= 1.0)
			throw new IllegalArgumentException("Le taux d'élitisme doit être dans ]0, 1[.");
		if (mutationRate < 0.0 || mutationRate > 1.0)
			throw new IllegalArgumentException("Le taux de mutation doit être dans [0, 1].");

		this.populationSize    = populationSize;
		this.nbGenerations     = nbGenerations;
		this.elitismRate       = elitismRate;
		this.mutationRate      = mutationRate;
		this.mutationAmplitude = mutationAmplitude;
		this.nbGamesPerEval    = nbGamesPerEval;
		this.evalDepth         = evalDepth;
		this.weightMin         = weightMin;
		this.weightMax         = weightMax;
		this.timeBudgetMs      = timeBudgetMs;
	}

	/**
	 * Configuration par défaut.
	 *
	 * <p>Chaque évaluation = {@code nbGamesPerEval} parties contre un profil normal
	 * (profondeur {@code evalDepth}) + 2 parties contre le BOSS
	 * (profondeur {@link awele.bot.competitor.noname.algorithms.training.common.AdaptiveOpponentSelector#BOSS_DEPTH},
	 * fitness ×2). Le BOSS garantit que la fitness reste discriminante même quand
	 * tous les profils normaux sont battus à 100%.</p>
	 */
	public static GeneticConfig defaultConfig()
	{
		return new GeneticConfig(
			40,          // populationSize  : diversité accrue
			25,          // nbGenerations
			0.15,        // elitismRate     : top 15% survivent
			0.20,        // mutationRate    : 20% des gènes mutent
			3.0,         // mutationAmplitude
			6,           // nbGamesPerEval  : 6 normales + 2 boss = 8 évals/individu
			4,           // evalDepth       : profondeur des profils normaux
			-20.0,       // weightMin
			20.0,        // weightMax
			5 * 60_000L  // timeBudgetMs    : 5 min
		);
	}
}