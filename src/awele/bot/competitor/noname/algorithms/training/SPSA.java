package awele.bot.competitor.noname.algorithms.training;

import java.util.Random;

import awele.bot.competitor.noname.algorithms.heuristics.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.test.TrainingLogger;

/**
 * @author Lucas Fagioli
 * Implémentation de l'algorithme SPSA (Simultaneous Perturbation Stochastic Approximation)
 */
public final class SPSA
{
	// ===== Config =====
	
	private final SPSAConfig config;
	private final Random random;
	
	// ===== Variables d'instance =====
	
	/**
	 * Meilleurs poids observés
	 */
	private double bestWeights[];
	
	/**
	 * Meilleur score moyen observé
	 */
	private double bestScore;
	
	/**
	 * Nombre d'itérations effectuées
	 */
	private int iterationCount;
	
	// ===== Fen^tre =====
	
	/**
	 * Taille de la fenêtre pour évaluer les stabilité
	 */
	private static final int WINDOW_SIZE = 10;
	
	private final double[] scoreWindow;
	private int windowIndex;
	private double windowSum;
	private boolean windowFull;
	
	public SPSA(SPSAConfig config)
	{
		this.config = config;
		this.random = new Random(86L);
		
		this.bestWeights = null;
		this.bestScore = Double.NEGATIVE_INFINITY;
		this.iterationCount = 0;
		
		this.scoreWindow = new double[WINDOW_SIZE];
		this.windowIndex = 0;
		this.windowSum = 0.0;
		this.windowFull = false;
	}
	
	/**
	 * Optimise les poids de l'évaluateur en utilisant l'algorithme SPSA
	 * @param evaluator Évaluateur de position à optimiser
	 * @param botEvaluator Évaluateur de bot pour estimer les scores pendant l'entraînement
	 */
	public void optimize(
		PositionEvaluator evaluator,
		BotEvaluator botEvaluator,
		TrainingLogger logger,
		String phaseName)
	{
		final long startTime = System.currentTimeMillis();
		final long endTime = startTime + config.timeBudgetMs;
		
		double[] theta = evaluator.getWeights();
		this.bestWeights = theta.clone();
		
		final int n = theta.length;
		
		double initialScore = Double.NEGATIVE_INFINITY;
		double finalScore = Double.NEGATIVE_INFINITY;
        
        while (System.currentTimeMillis() < endTime)
        {
        		final int k = this.iterationCount;
        		final double ak = config.computeAk(k);
        		final double ck = config.computeCk(k);
        		
        		final double[] delta = generateDelta(n);
        		
        		final double[] thetaPlus = new double[n];
        		final double[] thetaMinus = new double[n];
        		
        		for (int i = 0; i < n; i++)
			{
				thetaPlus[i] = clip(theta[i] + ck * delta[i]);
				thetaMinus[i] = clip(theta[i] - ck * delta[i]);
			}
        		
        		final AdaptiveOpponentSelector.OpponentProfile profile =
        			botEvaluator.sampleProfile(theta);
        		
        		final double scorePlus = botEvaluator.evaluate(thetaPlus, config.nbGamesPerEstimate, config.trainingDepth, profile);
        		final double scoreMinus = botEvaluator.evaluate(thetaMinus, config.nbGamesPerEstimate, config.trainingDepth, profile);
        		
        		final double scoreDiff = scorePlus - scoreMinus;
        		
        		for (int i = 0; i < n; i++)
        		{
        			final double gradient = scoreDiff / (2.0 * ck * delta[i]);
        			theta[i] = clip(theta[i] + ak * gradient);
        		}
        		
        		final double currentScore = (scorePlus + scoreMinus) / 2.0;
        		updateWindow(currentScore);
        		
        		final double windowAvg = windowFull ? (windowSum / WINDOW_SIZE) : currentScore;
        		finalScore = windowAvg;
        		
        		if (k == 0)
        			initialScore = currentScore;
        		
        		if (windowFull)
        		{
        			final double avgScore = windowSum / WINDOW_SIZE;
        			
        			if (avgScore > bestScore)
				{
					bestScore = avgScore;
					bestWeights = theta.clone();
					botEvaluator.updateBestWeights(bestWeights);
					
					if (logger != null)
					{
						logger.logBest(
							k, phaseName, System.currentTimeMillis() - startTime, bestScore, bestWeights);
					}
				}
        		}
        		
        		this.iterationCount++;
        		
        		if (logger != null)
			{
				logger.logIteration(
					k, phaseName,
					System.currentTimeMillis() - startTime,
					ak, ck,
					scorePlus, scoreMinus,
					windowAvg, bestScore,
					theta);
			}
        }
        
        evaluator.setWeights(bestWeights);
        
        if (logger != null)
        {
		    	logger.logSummary(
		    		phaseName,
		    		iterationCount,
		    		System.currentTimeMillis() - startTime,
		    		initialScore,
		    		finalScore,
		    		bestScore);
        }
	}
	
	/**
	 * Génère un vecteur de perturbation aléatoire pour les paramètres
	 * @param n Nombre de paramètres
	 * @return Vecteur de perturbation de taille n, avec des valeurs +1 ou -1
	 */
	private double[] generateDelta(int n)
	{
		final double[] delta = new double[n];
		
		for (int i = 0; i < n; i++)
			delta[i] = random.nextBoolean() ? 1.0 : -1.0;
		
		return delta;
	}
	
	/**
	 * Clip une valeur pour qu'elle reste dans les bornes définies par la config
	 * @param value Valeur à clipper
	 * @return Valeur clipée entre config.weightMin et config.weightMax
	 */
	private double clip(double value)
	{
		if (value < config.weightMin)
			return config.weightMin;
		else if (value > config.weightMax)
			return config.weightMax;
		return value;
	}
	
	/**
	 * Update la fenêtre de scores avec un nouveau score
	 * @param score Nouveau score à ajouter à la fenêtre
	 */
	private void updateWindow(double score)
	{
		windowSum -= scoreWindow[windowIndex];
		scoreWindow[windowIndex] = score;
		windowSum += score;
		
		windowIndex = (windowIndex + 1) % WINDOW_SIZE;
		
		if (!windowFull && windowIndex == 0)
			windowFull = true;
	}
	
	public int getIterationCount()
	{
		return this.iterationCount;
	}
	
	public double[] getBestWeights()
	{
		return this.bestWeights;
	}
	
	public double getBestScore()
	{
		return this.bestScore;
	}
}
