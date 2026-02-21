package awele.bot.competitor.noname.algorithms.training.genetic;

import java.util.Arrays;

/**
 * @author Lucas Fagioli
 * Représente un individu dans la population génétique.
 * Un individu correspond à un vecteur de poids (chromosome) et son score de fitness.
 */
public final class Individual implements Comparable<Individual>
{
	/**
	 * Vecteur de poids : gènes du chromosome
	 */
	private final double[] weights;

	/**
	 * Score de fitness (win-rate moyen sur nbGamesPerEval parties)
	 * Double.NEGATIVE_INFINITY tant que l'individu n'a pas été évalué.
	 */
	private double fitness;

	/**
	 * Indique si la fitness a déjà été calculée
	 */
	private boolean evaluated;

	public Individual(double[] weights)
	{
		this.weights   = weights.clone();
		this.fitness   = Double.NEGATIVE_INFINITY;
		this.evaluated = false;
	}

	// ===== Accesseurs =====

	public double[] getWeights()
	{
		return weights.clone();
	}

	public double[] getWeightsRef()
	{
		return weights;
	}

	public double getFitness()
	{
		return fitness;
	}

	public void setFitness(double fitness)
	{
		this.fitness   = fitness;
		this.evaluated = true;
	}

	public boolean isEvaluated()
	{
		return evaluated;
	}

	// ===== Comparable (ordre décroissant de fitness) =====

	@Override
	public int compareTo(Individual other)
	{
		// Ordre décroissant : le meilleur individu en premier
		return Double.compare(other.fitness, this.fitness);
	}

	@Override
	public String toString()
	{
		return "Individual{fitness=" + String.format("%.4f", fitness)
			+ ", weights=" + Arrays.toString(weights) + "}";
	}
}