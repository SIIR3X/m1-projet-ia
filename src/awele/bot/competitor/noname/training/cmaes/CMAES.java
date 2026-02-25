package awele.bot.competitor.noname.training.cmaes;

import java.util.Arrays;
import java.util.Random;

import awele.bot.competitor.noname.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.test.TrainingLogger;
import awele.bot.competitor.noname.training.BotEvaluator;
import awele.bot.competitor.noname.training.AdaptiveOpponentSelector.OpponentProfile;

public final class CMAES
{
	private final CMAESConfig config;
	private final BotEvaluator botEvaluator;
	private final Random random;
	
	// Meilleur résultat observé depuis le dé"but de l'apprentissage
	private double[] bestWeights;
	private double bestFitness;
	private int generationCount;
	
	public CMAES(CMAESConfig config, BotEvaluator botEvaluator)
	{
		this.config = config;
		this.botEvaluator = botEvaluator;
		this.random = new Random(86L);
		
		this.bestWeights = config.xmean.clone();
		this.bestFitness = Double.NEGATIVE_INFINITY;
		this.generationCount = 0;
	}
	
	public double[] getBestWeights()
	{
		return this.bestWeights.clone();
	}
	
	public double getBestFitness()
	{
		return this.bestFitness;
	}
	
	public int getGenerationCount()
	{
		return this.generationCount;
	}
	
	///
	/// Algorithme CMA-ES
	/// Traduit directement de https://en.wikipedia.org/wiki/CMA-ES
	///
	
	public void optimize(
		PositionEvaluator evaluator,
		TrainingLogger logger,
		String phaseName)
	{		
		///
		/// Initialisation - Section "User defined input parameters"
		///
		final int N = config.xmean.length;
		
		///
		/// Initialisation - Section "Strategy parameter setting: Selection"
		///
		// Taille de la population (nb indiv générés a chaque génération)
		final int lambda = 4 + (int)Math.floor(3 * Math.log(N));
		
		// Nombre de parents sélectionnés pour la recombinaison
		final int mu = lambda / 2;
		
		// Poids de recombinaison
		// plus le parent est bon plus il contribue
		final double[] weights = new double[mu];
		for (int i = 0; i < mu; i++)
		{
			weights[i] = Math.log(mu + 0.5) - Math.log(i + 1);
		}
		double sumWeights = 0.0;
		for (double w : weights)
		{
			sumWeights += w;
		}
		for (int i = 0; i < mu; i++)
		{
			weights[i] /= sumWeights;
		}
		
		// Variance effective des poids : mesure combien de parents contribuent vraiment à la recombinaison
		// mueff = mu quand les poids sont uniformes, < mu sinon
		double sumW = 0.0, sumW2 = 0.0;
		for (double w : weights)
		{
			sumW += w;
			sumW2 += w * w;
		}
		final double mueff = (sumW * sumW) / sumW2;
		
		///
		/// Intialisation - Section "Strategy parameter setting: Adaptation"
		///
		// Constante de temps pour l'accumulation du chemin d'évolution de C
		final double cc = (4.0 + mueff / N) / (N + 4.0 + 2.0 * mueff / N);
		
		// Constaet de temps pour l'accumulation du chemin d'évolution de sigma
		final double cs = (mueff + 2.0) / (N + mueff + 5.0);
		
		// Taux d'apprentissage pour la mise à jour rang-1 de C
		final double c1 = 2.0 / ((N + 1.3) * (N + 1.3) + mueff);
		
		// Taux d'apprentissage pour la mise à jour rang-mu de C
		final double cmu = Math.min(1.0 - c1, 2.0 * (mueff - 2.0 + 1.0 / mueff) / ((N + 2.0) * (N + 2.0) + mueff));
		
		// Facteur d'amortissement pour sigma (en général proche de 0)
		final double damps = 1.0 + 2.0 * Math.max(0.0, Math.sqrt((mueff - 1.0) / (N + 1.0)) - 1.0) + cs;
		
		///
		/// Initialisation - Section "Initialize dynamic strategy parameters"
		///
		// Chemins d'évolution pour C et sigma, initialisés à zéro
		final double[] pc = new double[N]; // chemin d'évolution pour C
		final double[] ps = new double[N]; // chemin d'évolution pour sigma
		
		// définit le système de coordonnées
		final double[][] B = identityMatrix(N);
		
		// contient les écarts-types selon chaque axe propre
		final double[] D = new double[N];
		Arrays.fill(D, 1.0);
		
		// Matrice de covariance
		final double[][] C = identityMatrix(N);
		
		// C^{-1/2}
		double[][] invsqrtC = identityMatrix(N);
		
		// Compteur d'évaluations depuis la derni_re décomposition propre de C
		long eigeneval = 0;
		
		// ||N(0,I)|| == norm(randn(N,1))
		final double chiN = Math.sqrt(N) * (1.0 - 1.0 / (4.0 * N) + 1.0 / (21.0 * N * N));
		
		///
		/// Boucle de génération - Section "Generation Loop"
		///
		
		double[] xmean = config.xmean.clone();
		double sigma = config.sigma0;
		long counteval = 0;
		final long startTime = System.currentTimeMillis();
		final long endTime = startTime + config.timeBudgetMs;
		double initialFitness = Double.NEGATIVE_INFINITY;
		double finalFitness = Double.NEGATIVE_INFINITY;
		
		while (counteval < config.stopEval && System.currentTimeMillis() < endTime)
		{
			///
			/// Générer et évaluer lambda individus
			///
			final double[][] arx = new double[lambda][N]; // individus non transformés
			final double[] arfitness = new double[lambda]; // leurs fitness
			
			for (int k = 0; k < lambda; k++)
			{
				final double[] z = new double[N];
				for (int i = 0; i < N; i++)
				{
					z[i] = random.nextGaussian();
				}
				
				for (int i = 0; i < N; i++)
				{
					double sum = 0.0;
					for (int j = 0; j < N; j++)
					{
						sum += B[i][j] * D[j] * z[j];
					}
					arx[k][i] = xmean[i] + sigma * sum;
				}
				
				clip(arx[k]);
				
				final OpponentProfile profile = botEvaluator.sampleProfile(arx[k]);
				arfitness[k] = botEvaluator.evaluate(arx[k], config.nbGamesPerEval, config.trainingDepth, profile);
				counteval++;
			}
			
			///
			/// Tri par fitness et calcul de la nouvelle moyenne xmean
			///
			final int[] arindex = sortedIndiceDescending(arfitness);
			
			
			final double[] xold = xmean.clone();
			
			xmean = new double[N];
			for (int i = 0; i < mu; i++)
			{
				for (int j = 0; j < N; j++)
				{
					xmean[j] += weights[i] * arx[arindex[i]][j];
				}
			}
			
			///
			/// Accumulation : mettre à jour les chemins d'évolution
			///
			// D éplacement de la moyenne normalisé par sigma
			final double[] step = new double[N];
			for (int i = 0; i < N; i++)
			{
				step[i] = (xmean[i] - xold[i]) / sigma;
			}
			
			// Mise à jour du chemin ps
			// ps = (1 - cs) * ps + sqrt(cs * (2 - cs) * mueff) * C^{-1/2} * step
			final double[] invsqrtCStep = multiply(invsqrtC, step);
			final double csSqrt = Math.sqrt(cs * (2.0 - cs) * mueff);
			for (int i = 0; i < N; i++)
			{
				ps[i] = (1.0 - cs) * ps[i] + csSqrt * invsqrtCStep[i];
			}
			
			final double psNorm = norm(ps);
			//final double hSigThreshold = (1.4 + 2.0 / (N + 1.0)) * chiN;
			final boolean hSig = psNorm / Math.sqrt(1.0 - Math.pow(1.0 - cs, 2.0 * counteval / lambda)) / chiN < 1.4 + 2.0 / (N + 1.0);
			
			final double ccSqrt = Math.sqrt(cc * (2.0 - cc) * mueff);
			for (int i = 0; i < N; i++)
			{
			    pc[i] = (1.0 - cc) * pc[i] + (hSig ? 1.0 : 0.0) * ccSqrt * step[i];
			}
			
			///
			/// Adapter la matrice de covariance C
			///
			final double[][] artmp = new double[N][mu];
			for (int i = 0; i < N; i++)
			{
				for (int k = 0; k < mu; k++)
				{
					artmp[i][k] = (arx[arindex[k]][i] - xold[i]) / sigma;
				}
			}
			
//			for (int i = 0; i < N; i++)
//			{
//				for (int j = 0; j < N; j++)
//				{
//					C[i][j] = (1.0 - c1 - cmu) * C[i][j] + c1 * (pc[i] * pc[j] + (1.0 - hSig) * cc * (2.0 - cc) * C[i][j]);
//					
//					for (int k = 0; k < mu; k++)
//					{
//						C[i][j] += cmu * weights[k] * artmp[i][k] * artmp[j][k];
//					}
//				}
			
			for (int i = 0; i < N; i++)
			{
				for (int j = 0; j < N; j++)
				{
					double rankMu = 0.0;
					for (int k = 0; k < mu; k++)
					{
						rankMu += weights[k] * artmp[i][k] * artmp[j][k];
					}
					
					final double correction = (hSig ? 0.0 : c1 * cc * (2.0 - cc)) * C[i][j];
					
					C[i][j] = (1.0 - c1 - cmu) * C[i][j] + c1 * (pc[i] * pc[j] + correction) + cmu * rankMu;
				}
			}
			
			///
			/// Adapter le step size sigma
			///
			sigma = sigma * Math.exp((cs / damps) * (psNorm / chiN - 1.0));
			
			///
			/// Décomposition propre de C en B*diag(D.^2)*B' (diagonalization)
			///
			if (counteval - eigeneval > lambda / (c1 + cmu) / N / 10.0)
			{
				eigeneval = counteval;
				
				for (int i = 0; i < N; i++)
				{
					for (int j = i + 1; j < N; j++)
					{
						C[i][j] = C[j][i];
					}
				}
				
				// Décomposition propre : C = B*diag(D.^2)*B'
				// Voir la section "Décomposition par la méthode de Jacobi" plus bas pour l'implémentation de eigenDecomposition
				// Basé sur https://en.wikipedia.org/wiki/Jacobi_eigenvalue_algorithm
				eigenDecomposition(C, B, D);
				
				for (int i = 0; i < N; i++)
				{
					for (int j = 0; j < N; j++)
					{
						double sum = 0.0;
						
						for (int k = 0; k < N; k++)
						{
							sum += B[i][k] * (1.0 / D[k]) * B[j][k];
						}
						invsqrtC[i][j] = sum;
					}
				}
			}
			
			///
			/// Critère d'arrêt'
			///
			double dMin = Double.MAX_VALUE, dMax = Double.MIN_VALUE;
			
			for (double d : D)
			{
				if (d < dMin)
				{
					dMin = d;
				}
				if (d > dMax)
				{
					dMax = d;
				}
			}
			
			if (dMax / dMin > 1e7)
				break;
			
			final double genBestFitness = arfitness[arindex[0]];
			final double genAvgFitness = averageFitness(arfitness);
			
			if (generationCount == 0)
				initialFitness = genBestFitness;
			finalFitness = genBestFitness;
			
			if (genBestFitness > bestFitness)
			{
				bestFitness = genBestFitness;
				bestWeights = xmean.clone();
				botEvaluator.updateBestWeights(bestWeights);
				
                if (logger != null)
                    logger.logBest(
                        generationCount, phaseName,
                        System.currentTimeMillis() - startTime,
                        bestFitness, bestWeights);
			}
			if (logger != null)
			{
				logger.logIteration(
	               generationCount, phaseName,
	               System.currentTimeMillis() - startTime,
	               sigma,
	               dMax / Math.max(dMin, 1e-20),
	               genBestFitness,
	               genAvgFitness,
	               bestFitness,
	               bestFitness,
	               xmean);
			}
			
			generationCount++;
		}
		
		evaluator.setWeights(bestWeights);
		
		if (logger != null)
		{
            logger.logSummary(
                    phaseName, generationCount,
                    System.currentTimeMillis() - startTime,
                    initialFitness, finalFitness, bestFitness);
		}
	}
	
	
	///
	/// Décomposition par la méthode de Jacobi
	/// Traduit directement https://en.wikipedia.org/wiki/Jacobi_eigenvalue_algorithm
	/// Et dans l'algo de https://en.wikipedia.org/wiki/CMA-ES traduit [B, D] = eig(C) (algo mathlab)
	///
	
	/**
	 * function maxind(k) : retourn l'index de l'élément hors-diagonale
	 * de valeur absolue maximale dans la ligne k de S
	 * 
	 * correspond à :
	 *   function maxind(k ∈ N) ∈ N ! index of largest off-diagonal element in row k
		    m := k+1
		    for i := k+2 to n do
		      if │Ski│ > │Skm│ then m := i endif
		    endfor
		    return m
		  endfunc
	 */
	private static int maxind(double[][] S, int k, int n)
	{
		int m = k + 1 < n ? k + 1 : 0;
		
		for (int i = k + 2; i < n; i++)
		{
			if (Math.abs(S[k][i]) > Math.abs(S[k][m]))
			{
				m = i;
			}
		}
		
		return m;
	}
	
	/**
	 * procedure update(k, t) : met à jour e[k] et son statut de changement
	 * 
	 * correspond à :
     *   procedure update(k ∈ N; t ∈ R)
     *     y := ek ; ek := y + t
     *     if changed[k] and (y = ek)      then changed[k] := false ; state := state - 1
     *     elsif (not changed[k]) and (y ≠ ek) then changed[k] := true  ; state := state + 1
	 */
	private static int updateState(
		double[] e,
		boolean[] changed,
		int k,
		double t,
		int state)
	{
		final double y = e[k];
		e[k] = y + t;
		
		if (changed[k] && y == e[k])
		{
			changed[k] = false;
			state--;
		}
		else if (!changed[k] && y != e[k])
		{
			changed[k] = true;
			state++;
		}
		
		return state;
	}
	
	/**
	 * procedure rotate(i, j) : fait tourner les éléments S[i][k], S[i][l], S[j][k], S[j][l]
	 * 
	 * Correspond à :
	 *   procedure rotate(k,l,i,j ∈ N) ! perform rotation of Sij, Skl
		    ┌   ┐    ┌     ┐┌   ┐
		    │Skl│    │c  −s││Skl│
		    │   │ := │     ││   │
		    │Sij│    │s   c││Sij│
		    └   ┘    └     ┘└   ┘
		  endproc
	 */
	private static void rotate(
		double[][] S,
		int k,
		int l,
		int i,
		int j,
		double c,
		double s)
	{
		final double Skl = S[k][l];
		final double Sij = S[i][j];
		S[k][l] = c * Skl - s * Sij;
		S[l][k] = S[k][l];
		S[i][j] = s * Skl + c * Sij;
		S[j][i] = S[i][j];
	}
	
	/**
	 * Calcule la décomposition propre de la matrice symétrique C
	 */
	private static void eigenDecomposition(
		double[][] C,
		double[][] B,
		double[] D)
	{
		final int n = C.length;
		
		// S = copie de C
		final double[][] S = new double[n][n];
		for (int i = 0; i < n; i++)
		{
			S[i] = C[i].clone();
		}
		
		// e = vecteur des valeurs propres courantes
		final double[] e = new double[n];
		for (int k = 0; k < n; k++)
		{
			e[k] = S[k][k];
		}
		
		// E = matrice des vecteurs propres
		final double[][] E = identityMatrix(n);
		
		// indices = indices triés par valeur propre décroissante
		final int[] ind = new int[n];
		for (int k = 0; k < n; k++)
		{
			ind[k] = maxind(S, k, n);
		}
		
		// changed = true si ek a changé depuis la derniere rotation
		// state = nombre de valeurs propres encore en train de changer
		final boolean[] changed = new boolean[n];
		for (int k = 0; k < n; k++)
		{
			changed[k] = true;
		}
		int state = n;
		
		while (state != 0)
		{
			int m = 0;
			for (int k = 1; k < n; k++)
			{
				if (Math.abs(S[k][ind[k]]) > Math.abs(S[m][ind[m]]))
				{
					m = k;
				}
			}
			
			// k et l sont les indices de ligne et colonne du pivot p = S[k][l]
			// correspond à : k = m, l = indm, p = Skl
			final int k = m;
			final int l = ind[m];
			final double p = S[k][l];
			
			final double y = (e[l] - e[k]) / 2.0;
			final double d = Math.abs(y) + Math.sqrt(p * p + y * y);
			final double r = Math.sqrt(p * p + d * d);
			double c = d / r; // cosinus de l'angle de rotation
			double s = p / r; // sinus de l'angle de rotation
			double t  = (p * p) / d; //variation sur la valeur propre
			
			if (y < 0)
			{
				s = -s;
				t = -t;
			}
			
			// Annule le pivot : S[k][l] = 0
			// Correspond à : Skl = 0.0
			S[k][l] = 0.0;
			S[l][k] = 0.0;
			
			// Met à jour les approximatiopons des valeurs propres e[k] et e[l]
			// et leur statut de changement
			// Correspond à : update(k, -t), update(l, t)
			state = updateState(e, changed, k, -t, state);
			state = updateState(e, changed, l, t, state);
			
			// On fait tourner les lignes et colonnes k et l dans S
			// Correspond à :
			// for i := 1 to k-1   do rotate(i,k, i,l) endfor
			// for i := k+1 to l-1 do rotate(k,i, i,l) endfor
			// for i := l+1 to n   do rotate(k,i, l,i) endfor
			for (int i = 0; i < k; i++)
			{
				rotate(S, i, k, i, l, c, s);
			}
			for (int i = k + 1; i < l; i++)
			{
				rotate(S, k, i, i, l, c, s);
			}
			for (int i = l + 1; i < n; i++)
			{
				rotate(S, k, i, l, i, c, s);
			}
			
			// On fait tourner les vecteurs propres
			// Correspond à :
			// for i := 1 to n do
			// ┌   ┐    ┌     ┐┌   ┐
			// │Eik│    │c  −s││Eik│
			// │   │ := │     ││   │
			// │Eil│    │s   c││Eil│
			// └   ┘    └     ┘└   ┘
			// endfor
			
//			for (int i = 0; i < n; i++)
//			{
//				final double Eik = E[i][k];
			
//				final double Eil = E[i][l];
			
//				E[i][k] = c * Eil - s * Eik;
//				E[i][l] = s * Eik + c * Eil;
//			}
			
			for (int i = 0; i < n; i++)
			{
				final double Eik = E[i][k];
				final double Eil = E[i][l];
				E[i][k] = c * Eik - s * Eil;
				E[i][l] = s * Eik + c * Eil;
			}
			
			// On recalcule ind[k] et ind[l]
			// Correspond à :
			// for i := 1 to n do indi := maxind(i) endfor
			for (int i = 0; i < n; i++)
			{
				ind[i] = maxind(S, i, n);
			}
		}
		
		// A la fin de l'algorithme, les valeurs propres sont dans e et les vecteurs propres dans E
		// On peut alors extraire les valeurs finales
		for (int i = 0; i < n; i++)
		{
			D[i] = Math.sqrt(Math.max(e[i], 1e-20));
			
			for (int j = 0; j < n; j++)
			{
				B[i][j] = E[j][i];
			}
		}
	}
	
	///
	/// Méthodes privées
	/// Ici principalement des méthods simples
	/// Opérations sur matrix etc...
	/// A déplacer plus tard (pas vriament CMAES spécifique)
	/// 
	
	/**
	 * Multiplie une matrice par un vecteur
	 * @param M Matrice de taille (m x n)
	 * @param v Vecteur de taille n
	 * @return Résultat de la multiplication
	 */
	private static double[] multiply(double[][] M, double[] v)
	{
		final int n = v.length;
		final double[] result = new double[n];
		
		for (int i = 0; i < n; i++)
		{
			for (int j = 0; j < n; j++)
			{
				result[i] += M[i][j] * v[j];
			}
		}
		
		return result;
	}
	
	/**
	 * Nomalise un vecteur
	 * @param v Vecteur à normaliser
	 * @return Vecteur normalisé
	 */
	private static double norm(double[] v)
	{
		double sum = 0.0;
		
		for (double x : v)
		{
			sum += x * x;
		}
		
		return Math.sqrt(sum);
	}
	
	/**
	 * Génère une matrice identité de taille n x n
	 * @param n Taille de la matrice
	 * @return Matrice identité de taille n x n
	 */
	private static double[][] identityMatrix(int n)
	{
		double[][] I = new double[n][n];
		
		for (int i = 0; i < n; i++)
		{
			I[i][i] = 1.0;
		}
		
		return I;
	}
	
	/**
	 * Retourne les indices triés par fitness décroissante
	 * @param fitness Tableau de fitness pour chaque individu
	 * @return Tableau d'indices triés par fitness décroissante
	 */
	private static int[] sortedIndiceDescending(double[] fitness)
	{
		final Integer[] indexes = new Integer[fitness.length];
		
		for (int i = 0; i < fitness.length; i++)
		{
			indexes[i] = i;
		}
		
		Arrays.sort(indexes, (i1, i2) -> Double.compare(fitness[i2], fitness[i1]));
		
		final int[] result = new int[fitness.length];
		
		for (int i = 0; i < fitness.length; i++)
		{
			result[i] = indexes[i];
		}
		
		return result;
	}
	
	/**
	 * Calcule la fitness moyenne d'une population
	 * @param fitness Tableau de fitness pour chaque individu
	 * @return Fitness moyenne de la population
	 */
	private static double averageFitness(double[] fitness)
	{
		double sum = 0.0;
		
		for (double f : fitness)
		{
			sum += f;
		}
		
		return sum / fitness.length;
	}
	
	/**
	 * Cilp chaque poids dans l'interval de la config
	 * @param individual Individu à clipper
	 */
	private void clip(double[] individual)
	{
		for (int i = 0; i < individual.length; i++)
		{
			if (individual[i] < config.weightMin)
				individual[i] = config.weightMin;
			else if (individual[i] > config.weightMax)
				individual[i] = config.weightMax;
		}
	}
}
