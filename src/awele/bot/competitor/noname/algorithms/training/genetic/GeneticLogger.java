package awele.bot.competitor.noname.algorithms.training.genetic;

import java.util.Arrays;

/**
 * @author Lucas Fagioli
 * Logger simple pour l'algorithme génétique. Affiche les statistiques par génération
 * sur la sortie standard (même convention que TrainingLogger du SPSA).
 */
public final class GeneticLogger
{
	private final boolean enabled;

	public GeneticLogger(boolean enabled)
	{
		this.enabled = enabled;
	}

	public void logGenerationStart(int generation, int total)
	{
		if (!enabled) return;
		System.out.printf("[GA] === Génération %d / %d ===%n", generation + 1, total);
	}

	public void logGenerationResult(
		int generation,
		long elapsedMs,
		double bestFitness,
		double avgFitness,
		double[] bestWeights)
	{
		if (!enabled) return;
		System.out.printf(
			"[GA] Gen %3d | Temps : %5d ms | Best fitness : %+.4f | Avg fitness : %+.4f%n",
			generation + 1, elapsedMs, bestFitness, avgFitness);
		System.out.printf("[GA] Best weights : %s%n", Arrays.toString(bestWeights));
	}

	public void logTimeLimitReached(int generation)
	{
		if (!enabled) return;
		System.out.printf("[GA] Budget temps atteint à la génération %d.%n", generation + 1);
	}

	public void logSummary(
		int generationsCompleted,
		long totalMs,
		double bestFitness,
		double[] bestWeights)
	{
		if (!enabled) return;
		System.out.println("[GA] ====== Résumé algorithme génétique ======");
		System.out.printf("[GA] Générations complètes : %d%n", generationsCompleted);
		System.out.printf("[GA] Temps total           : %d ms%n", totalMs);
		System.out.printf("[GA] Meilleure fitness     : %+.4f%n", bestFitness);
		System.out.printf("[GA] Meilleurs poids       : %s%n", Arrays.toString(bestWeights));
		System.out.println("[GA] ===========================================");
	}
}