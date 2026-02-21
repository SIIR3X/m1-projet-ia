package awele.bot.competitor.noname.algorithms.training.genetic;

import java.util.Arrays;
import java.util.Random;

import awele.bot.competitor.noname.algorithms.heuristics.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.algorithms.training.common.AdaptiveOpponentSelector;
import awele.bot.competitor.noname.algorithms.training.common.BotEvaluator;

/**
 * @author Lucas Fagioli
 *
 * Algorithme génétique destiné à fournir des poids initiaux approximatifs au SPSA.
 *
 * <p>Inspiré de Saillot, le schéma par génération est le suivant :</p>
 * <ol>
 *   <li>Tirage de C {@link #NB_FIGHTERS} "combattants" parmi la population
 *       (le meilleur de la génération précédente est toujours inclus).</li>
 *   <li>Évaluation de chaque individu non évalué : il joue {@code nbGamesPerEval}
 *       parties contre chaque combattant + 2 parties contre le BOSS (×2).</li>
 *   <li>Réévaluation du meilleur individu de la génération précédente avec les
 *       mêmes combattants, pour que son score reste comparable.</li>
 *   <li>Tri par fitness décroissante, mise à jour du meilleur global.</li>
 *   <li>Construction de la génération suivante par élitisme + croisement + mutation.</li>
 * </ol>
 */
public final class GeneticAlgorithm
{
	// ===== Composants =====

	private final GeneticConfig    config;
	private final BotEvaluator     botEvaluator;
	private final GeneticOperators operators;
	private final GeneticLogger    logger;
	private final Random           random;

	// ===== État =====

	private Individual[] population;
	private Individual   bestIndividual;       // meilleur tout-temps
	private Individual   prevGenerationBest;   // meilleur de la génération précédente (à réévaluer)
	private int          generationsCompleted;

	// ===== Constantes =====

	private static final long SEED = 0xDEADBEEFL;
	private static final double BLX_PROBABILITY = 0.5;

	/**
	 * Nombre de combattants C tirés par génération pour l'évaluation.
	 * Inspiré de Saillot (C = 5). Le meilleur de la génération précédente
	 * est toujours inclus, donc on tire NB_FIGHTERS - 1 individus aléatoirement.
	 */
	private static final int NB_FIGHTERS = 5;

	// ===== Constructeur =====

	public GeneticAlgorithm(GeneticConfig config, BotEvaluator botEvaluator, boolean verbose)
	{
		this.config               = config;
		this.botEvaluator         = botEvaluator;
		this.operators            = new GeneticOperators(config, SEED);
		this.logger               = new GeneticLogger(verbose);
		this.random               = new Random(SEED);
		this.bestIndividual       = null;
		this.prevGenerationBest   = null;
		this.generationsCompleted = 0;
	}

	// ===== Point d'entrée =====

	/**
	 * Lance l'algorithme génétique et retourne les meilleurs poids trouvés.
	 *
	 * @param evaluator L'évaluateur dont on veut optimiser les poids
	 * @return Les meilleurs poids trouvés
	 */
	public double[] optimize(PositionEvaluator evaluator)
	{
		final int  nbWeights  = evaluator.getNbWeights();
		final long startTime  = System.currentTimeMillis();
		final long deadline   = startTime + config.timeBudgetMs;

		initPopulation(nbWeights);

		for (int gen = 0; gen < config.nbGenerations; gen++)
		{
			if (System.currentTimeMillis() >= deadline)
			{
				logger.logTimeLimitReached(gen);
				break;
			}

			logger.logGenerationStart(gen, config.nbGenerations);

			// 1. Tirer les combattants de cette génération
			final Individual[] fighters = selectFighters();

			// 2. Évaluer tous les individus non encore notés contre les combattants
			evaluateAll(fighters);

			// 3. Réévaluer le meilleur de la génération précédente avec les mêmes combattants
			//    (son score doit rester comparable aux autres dans le nouveau contexte)
			reevaluatePrevBest(fighters);

			// 4. Tri + mise à jour du meilleur global
			Arrays.sort(population);
			updateBest();

			// 5. Logging
			logger.logGenerationResult(
				gen,
				System.currentTimeMillis() - startTime,
				bestIndividual.getFitness(),
				computeAvgFitness(),
				bestIndividual.getWeights());

			// 6. Mémoriser le meilleur avant de construire la nouvelle génération
			prevGenerationBest = new Individual(population[0].getWeightsRef());
			prevGenerationBest.setFitness(population[0].getFitness());

			// 7. Construire la génération suivante
			population = buildNextGeneration(nbWeights);

			generationsCompleted++;
		}

		// Évaluation finale de la dernière génération
		final Individual[] finalFighters = selectFighters();
		evaluateAll(finalFighters);
		Arrays.sort(population);
		updateBest();

		final double[] best = bestIndividual.getWeights();

		logger.logSummary(
			generationsCompleted,
			System.currentTimeMillis() - startTime,
			bestIndividual.getFitness(),
			best);

		evaluator.setWeights(best);
		return best;
	}

	// ===== Initialisation =====

	private void initPopulation(int nbWeights)
	{
		population = new Individual[config.populationSize];
		population[0] = new Individual(buildDefaultSeed(nbWeights));

		for (int i = 1; i < config.populationSize; i++)
			population[i] = operators.randomIndividual(nbWeights);
	}

	private double[] buildDefaultSeed(int nbWeights)
	{
		final double[] defaults = PositionEvaluator.getDefautltWeights();
		final double[] seed     = new double[nbWeights];

		for (int i = 0; i < nbWeights; i++)
			seed[i] = defaults[i % defaults.length];

		return seed;
	}

	// ===== Combattants =====

	/**
	 * Sélectionne les combattants de la génération courante.
	 *
	 * <p>Règle de Saillot : le meilleur individu de la génération précédente est
	 * toujours inclus. Les NB_FIGHTERS - 1 autres sont tirés aléatoirement dans
	 * la population courante (sans remise).</p>
	 *
	 * @return Tableau de NB_FIGHTERS combattants
	 */
	private Individual[] selectFighters()
	{
		final Individual[] fighters = new Individual[NB_FIGHTERS];
		int idx = 0;

		// Le meilleur de la génération précédente est toujours combattant
		if (prevGenerationBest != null)
			fighters[idx++] = prevGenerationBest;

		// Tirage sans remise dans la population courante
		final boolean[] used = new boolean[population.length];
		while (idx < NB_FIGHTERS)
		{
			int pick = random.nextInt(population.length);

			// Éviter de tirer deux fois le même individu
			if (used[pick]) continue;
			used[pick] = true;

			// Éviter de dupliquer prevGenerationBest si par hasard il est encore dans la pop
			final Individual candidate = population[pick];
			if (prevGenerationBest != null &&
				Arrays.equals(candidate.getWeightsRef(), prevGenerationBest.getWeightsRef()))
				continue;

			fighters[idx++] = candidate;
		}

		return fighters;
	}

	// ===== Évaluation =====

	/**
	 * Évalue tous les individus non encore notés contre l'ensemble des combattants.
	 *
	 * <p>Pour chaque combattant, l'individu joue {@code nbGamesPerEval} parties
	 * (profil tiré par l'AdaptiveOpponentSelector) + 2 parties BOSS.
	 * La fitness totale est la somme sur tous les combattants.</p>
	 */
	private void evaluateAll(Individual[] fighters)
	{
		for (final Individual ind : population)
		{
			if (ind.isEvaluated()) continue;
			ind.setFitness(evaluateAgainstFighters(ind, fighters));
		}
	}

	/**
	 * Réévalue le meilleur individu de la génération précédente avec les combattants
	 * courants, puis met à jour sa fitness dans la population si il y est encore présent.
	 *
	 * <p>Sans cette étape, le score de l'ancien meilleur serait calculé avec d'anciens
	 * combattants et ne serait plus comparable aux scores de la génération courante.</p>
	 */
	private void reevaluatePrevBest(Individual[] fighters)
	{
		if (prevGenerationBest == null) return;

		final double newFitness = evaluateAgainstFighters(prevGenerationBest, fighters);
		prevGenerationBest.setFitness(newFitness);

		// Si le même individu est encore dans la population (via élitisme),
		// on met à jour son score pour qu'il soit cohérent avec les autres.
		for (final Individual ind : population)
		{
			if (Arrays.equals(ind.getWeightsRef(), prevGenerationBest.getWeightsRef()))
			{
				ind.setFitness(newFitness);
				break;
			}
		}
	}

	/**
	 * Évalue un individu contre tous les combattants et retourne la fitness totale.
	 *
	 * @param ind      L'individu à évaluer
	 * @param fighters Les combattants de la génération
	 * @return La fitness totale accumulée (incluant les parties BOSS via BotEvaluator)
	 */
	private double evaluateAgainstFighters(Individual ind, Individual[] fighters)
	{
		final double[] w = ind.getWeightsRef();
		double totalFitness = 0.0;

		for (final Individual fighter : fighters)
		{
			if (fighter == null) continue;

			// On utilise les poids du combattant comme adversaire temporaire via SELF
			// en les injectant dans l'AdaptiveOpponentSelector avant l'évaluation
			botEvaluator.updateBestWeights(fighter.getWeightsRef());

			final AdaptiveOpponentSelector.OpponentProfile profile =
				botEvaluator.sampleProfile(w);

			totalFitness += botEvaluator.evaluate(w, config.nbGamesPerEval, config.evalDepth, profile);
		}

		return totalFitness;
	}

	// ===== Sélection & Reproduction =====

	/**
	 * Construit la génération suivante par élitisme + croisement + mutation.
	 * Les parents sont tirés par tournoi dans le top 50% de la population triée.
	 */
	private Individual[] buildNextGeneration(int nbWeights)
	{
		final int nbElites = Math.max(1, (int) Math.round(config.populationSize * config.elitismRate));
		final Individual[] next = new Individual[config.populationSize];

		// --- Élitisme : les nbElites meilleurs survivent sans réévaluation ---
		for (int i = 0; i < nbElites; i++)
		{
			final Individual elite = new Individual(population[i].getWeightsRef());
			elite.setFitness(population[i].getFitness());
			next[i] = elite;
		}

		// --- Reproduction depuis le top 50% ---
		final int parentPoolSize = Math.max(2, config.populationSize / 2);
		final Individual[] parentPool = Arrays.copyOfRange(population, 0, parentPoolSize);

		for (int i = nbElites; i < config.populationSize; i++)
		{
			final Individual parentA = operators.tournamentSelect(parentPool);
			final Individual parentB = operators.tournamentSelect(parentPool);

			final Individual child = (random.nextDouble() < BLX_PROBABILITY)
				? operators.blxAlphaCrossover(parentA, parentB)
				: operators.uniformCrossover(parentA, parentB);

			operators.mutate(child);
			next[i] = child;
		}

		return next;
	}

	// ===== Utilitaires =====

	private void updateBest()
	{
		final Individual genBest = population[0];

		if (bestIndividual == null || genBest.getFitness() > bestIndividual.getFitness())
		{
			bestIndividual = new Individual(genBest.getWeightsRef());
			bestIndividual.setFitness(genBest.getFitness());
			botEvaluator.updateBestWeights(bestIndividual.getWeights());
		}
	}

	private double computeAvgFitness()
	{
		double sum = 0.0;
		for (final Individual ind : population)
			if (ind.isEvaluated()) sum += ind.getFitness();
		return sum / population.length;
	}

	// ===== Accesseurs =====

	public int getGenerationsCompleted() { return generationsCompleted; }
	public double getBestFitness() { return bestIndividual != null ? bestIndividual.getFitness() : Double.NEGATIVE_INFINITY; }
	public double[] getBestWeights() { return bestIndividual != null ? bestIndividual.getWeights() : null; }
}