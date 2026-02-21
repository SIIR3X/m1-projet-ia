package awele.bot.competitor.noname.algorithms.training.genetic;

import java.util.Random;

/**
 * @author Lucas Fagioli
 * Opérateurs génétiques : sélection par tournoi, croisement uniforme et mutation gaussienne.
 */
public final class GeneticOperators
{
	private final GeneticConfig config;
	private final Random random;

	/** Taille du tournoi de sélection */
	private static final int TOURNAMENT_SIZE = 3;

	public GeneticOperators(GeneticConfig config, long seed)
	{
		this.config = config;
		this.random = new Random(seed);
	}

	// ===== Sélection par tournoi =====

	/**
	 * Sélectionne un parent par tournoi : on tire TOURNAMENT_SIZE individus au hasard
	 * et on retourne le meilleur.
	 * @param population Population triée (index 0 = meilleur)
	 * @return L'individu sélectionné
	 */
	public Individual tournamentSelect(Individual[] population)
	{
		Individual best = null;

		for (int i = 0; i < TOURNAMENT_SIZE; i++)
		{
			final Individual candidate = population[random.nextInt(population.length)];

			if (best == null || candidate.getFitness() > best.getFitness())
				best = candidate;
		}

		return best;
	}

	// ===== Croisement uniforme =====

	/**
	 * Croisement uniforme : pour chaque gène, on tire au sort lequel des deux parents
	 * contribue. Produit un enfant.
	 * @param parentA Premier parent
	 * @param parentB Deuxième parent
	 * @return Un nouvel individu enfant
	 */
	public Individual uniformCrossover(Individual parentA, Individual parentB)
	{
		final double[] wa = parentA.getWeightsRef();
		final double[] wb = parentB.getWeightsRef();
		final int n = wa.length;

		final double[] childWeights = new double[n];

		for (int i = 0; i < n; i++)
			childWeights[i] = random.nextBoolean() ? wa[i] : wb[i];

		return new Individual(childWeights);
	}

	/**
	 * Croisement arithmétique BLX-α : l'enfant est tiré uniformément dans
	 * [min - α·range, max + α·range] pour chaque gène, puis clippé.
	 * Complète bien le croisement uniforme pour explorer l'espace entre les parents.
	 * α = 0.5 est la valeur classique.
	 */
	public Individual blxAlphaCrossover(Individual parentA, Individual parentB)
	{
		final double ALPHA = 0.5;
		final double[] wa = parentA.getWeightsRef();
		final double[] wb = parentB.getWeightsRef();
		final int n = wa.length;

		final double[] childWeights = new double[n];

		for (int i = 0; i < n; i++)
		{
			final double lo    = Math.min(wa[i], wb[i]);
			final double hi    = Math.max(wa[i], wb[i]);
			final double range = hi - lo;

			final double lower = lo - ALPHA * range;
			final double upper = hi + ALPHA * range;

			childWeights[i] = clip(lower + random.nextDouble() * (upper - lower));
		}

		return new Individual(childWeights);
	}

	// ===== Mutation gaussienne =====

	/**
	 * Applique une mutation gaussienne sur les gènes de l'individu avec la probabilité
	 * config.mutationRate. La perturbation est tirée dans N(0, mutationAmplitude).
	 * Modifie l'individu en place.
	 * @param individual L'individu à muter
	 */
	public void mutate(Individual individual)
	{
		final double[] w = individual.getWeightsRef();

		for (int i = 0; i < w.length; i++)
		{
			if (random.nextDouble() < config.mutationRate)
				w[i] = clip(w[i] + random.nextGaussian() * config.mutationAmplitude);
		}
	}

	// ===== Initialisation aléatoire =====

	/**
	 * Crée un individu avec des poids tirés uniformément dans [weightMin, weightMax].
	 * @param nbWeights Nombre de poids
	 * @return Nouvel individu aléatoire
	 */
	public Individual randomIndividual(int nbWeights)
	{
		final double[] weights = new double[nbWeights];
		final double range = config.weightMax - config.weightMin;

		for (int i = 0; i < nbWeights; i++)
			weights[i] = config.weightMin + random.nextDouble() * range;

		return new Individual(weights);
	}

	// ===== Utilitaire =====

	private double clip(double value)
	{
		if (value < config.weightMin) return config.weightMin;
		if (value > config.weightMax) return config.weightMax;
		return value;
	}
}