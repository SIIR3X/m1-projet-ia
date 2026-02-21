package awele.bot.competitor.noname.algorithms.training.cmaes;

import java.util.Arrays;

/**
 * @author Lucas Fagioli
 * État interne de CMA-ES : distribution gaussienne multivariée N(m, σ²·C).
 *
 * <p>Contient toute la mathématique de l'algorithme :</p>
 * <ul>
 *   <li>Adaptation du pas (CSA — Cumulative Step-size Adaptation)</li>
 *   <li>Adaptation de la covariance (CMA)</li>
 *   <li>Échantillonnage depuis la distribution courante</li>
 * </ul>
 *
 * <p>Référence : Hansen N. (2016). The CMA Evolution Strategy: A Tutorial.
 * arXiv:1604.00772</p>
 *
 * <p>Cette classe est volontairement sans dépendance au reste du projet
 * (pas d'import de BitBoard, BotEvaluator, etc.) pour rester testable isolément.</p>
 */
public final class CMAESState
{
    // ===== Dimension =====

    private final int n;

    // ===== Distribution =====

    /** Moyenne de la distribution (vecteur de paramètres courant) */
    private final double[] mean;

    /** Step size (σ) */
    private double sigma;

    /** Matrice de covariance C (n×n) */
    private final double[][] C;

    // ===== Chemins d'évolution =====

    /** Chemin d'évolution pour CSA (contrôle de σ) */
    private final double[] ps;

    /** Chemin d'évolution pour CMA (mise à jour de C) */
    private final double[] pc;

    // ===== Décomposition propre de C =====

    /** Vecteurs propres de C (colonnes = eigenvectors), mis en cache */
    private double[][] B;

    /** Racines des valeurs propres (diag de D), telles que C = B·D²·Bᵀ */
    private double[] D;

    /** Inverse de BD (utilisé pour le chemin CSA) */
    private double[][] BDinv;

    /** Compteur de générations depuis la dernière décomposition propre */
    private int eigenDecompAge;

    // ===== Paramètres stratégiques (calculés depuis n et mu) =====

    private final double[] recombinationWeights; // w_i pour i = 0..mu-1
    private final double muEff;                  // variance effective des poids

    private final double cc;   // taux de mémorisation pour pc
    private final double cs;   // taux de mémorisation pour ps
    private final double c1;   // taux d'apprentissage pour la mise à jour rang-1
    private final double cmu;  // taux d'apprentissage pour la mise à jour rang-mu
    private final double damps; // facteur d'amortissement pour σ

    private final double chiN; // E[‖N(0,I)‖] ≈ √n · (1 - 1/(4n) + 1/(21n²))

    // ===== Compteur de générations =====

    private int generation;

    // ===== Constructeur =====

    /**
     * Initialise l'état CMA-ES depuis un point de départ et une configuration.
     *
     * @param initialMean Point de départ (les poids initiaux de l'évaluateur)
     * @param sigma0      Écart-type initial
     * @param mu          Nombre de parents (individus sélectionnés)
     * @param lambda      Taille de la population (total des individus)
     */
    public CMAESState(double[] initialMean, double sigma0, int mu, int lambda)
    {
        this.n     = initialMean.length;
        this.sigma = sigma0;
        this.mean  = initialMean.clone();

        // Matrice de covariance initiale = identité
        this.C = new double[n][n];
        for (int i = 0; i < n; i++) this.C[i][i] = 1.0;

        // Chemins d'évolution initialisés à zéro
        this.ps = new double[n];
        this.pc = new double[n];

        // Décomposition propre initiale (C = I → B = I, D = 1)
        this.B = identityMatrix(n);
        this.D = onesVector(n);
        this.BDinv = identityMatrix(n);
        this.eigenDecompAge = 0;

        // ===== Poids de recombinaison (Hansen 2016, eq. 46) =====
        this.recombinationWeights = computeWeights(mu, lambda);
        this.muEff = computeMuEff(recombinationWeights);

        // ===== Paramètres d'adaptation =====
        this.cc    = (4.0 + muEff / n) / (n + 4.0 + 2.0 * muEff / n);
        this.cs    = (muEff + 2.0) / (n + muEff + 5.0);
        this.c1    = 2.0 / ((n + 1.3) * (n + 1.3) + muEff);
        this.cmu   = Math.min(
                        1.0 - c1,
                        2.0 * (muEff - 2.0 + 1.0 / muEff) / ((n + 2.0) * (n + 2.0) + muEff));
        this.damps = 1.0 + 2.0 * Math.max(0.0, Math.sqrt((muEff - 1.0) / (n + 1.0)) - 1.0) + cs;
        this.chiN  = Math.sqrt(n) * (1.0 - 1.0 / (4.0 * n) + 1.0 / (21.0 * n * n));

        this.generation = 0;
    }

    // ===== Échantillonnage =====

    /**
     * Génère lambda individus depuis la distribution courante N(m, σ²·C).
     *
     * @param rng Source de nombres aléatoires (passe un tableau de vecteurs gaussiens)
     * @return Tableau de lambda vecteurs de paramètres (non clippés)
     */
    public double[][] samplePopulation(java.util.Random rng, int lambda)
    {
        final double[][] population = new double[lambda][n];

        for (int k = 0; k < lambda; k++)
        {
            // z ~ N(0, I)
            final double[] z = new double[n];
            for (int i = 0; i < n; i++)
                z[i] = rng.nextGaussian();

            // y = B · D · z → x = mean + sigma · y
            final double[] y = multiplyBD(z);
            for (int i = 0; i < n; i++)
                population[k][i] = mean[i] + sigma * y[i];
        }

        return population;
    }

    // ===== Mise à jour de la distribution =====

    /**
     * Met à jour la distribution (mean, sigma, C) depuis les mu meilleurs individus.
     *
     * @param sortedBest Individus triés par fitness décroissante (index 0 = meilleur),
     *                   seuls les {@code recombinationWeights.length} premiers sont utilisés.
     */
    public void update(double[][] sortedBest)
    {
        final int mu = recombinationWeights.length;

        // ===== 1. Mise à jour de la moyenne (recombinaison pondérée) =====
        final double[] oldMean = mean.clone();
        for (int i = 0; i < n; i++)
        {
            double sum = 0.0;
            for (int k = 0; k < mu; k++)
                sum += recombinationWeights[k] * sortedBest[k][i];
            mean[i] = sum;
        }

        // ===== 2. Mise à jour du chemin ps (CSA) =====
        // ps ← (1 - cs) · ps + √(cs(2-cs)·μeff) · C^{-1/2} · (m_new - m_old) / σ
        final double[] displacement = new double[n]; // (m_new - m_old) / σ
        for (int i = 0; i < n; i++)
            displacement[i] = (mean[i] - oldMean[i]) / sigma;

        final double[] BDinvDisp = multiplyBDinv(displacement);
        final double csSqrt = Math.sqrt(cs * (2.0 - cs) * muEff);
        for (int i = 0; i < n; i++)
            ps[i] = (1.0 - cs) * ps[i] + csSqrt * BDinvDisp[i];

        // ===== 3. Mise à jour du pas σ (CSA) =====
        final double psNorm = norm(ps);
        sigma *= Math.exp((cs / damps) * (psNorm / chiN - 1.0));

        // ===== 4. Indicateur h_sigma (évite la mise à jour pc prématurée) =====
        final int g = generation + 1;
        final double hSigmaThreshold = (1.4 + 2.0 / (n + 1.0)) * chiN;
        final boolean hSigma = (psNorm / Math.sqrt(1.0 - Math.pow(1.0 - cs, 2.0 * g))) < hSigmaThreshold;

        // ===== 5. Mise à jour du chemin pc (CMA) =====
        // pc ← (1 - cc) · pc + h_sigma · √(cc(2-cc)·μeff) · (m_new - m_old) / σ
        final double ccSqrt = Math.sqrt(cc * (2.0 - cc) * muEff);
        for (int i = 0; i < n; i++)
            pc[i] = (1.0 - cc) * pc[i] + (hSigma ? ccSqrt : 0.0) * displacement[i];

        // ===== 6. Mise à jour de la matrice de covariance C =====
        updateCovarianceMatrix(sortedBest, oldMean, hSigma, mu);

        generation++;

        // ===== 7. Décomposition propre (mise à jour lazy) =====
        // On recompute C = B·D²·Bᵀ toutes les max(1, n/(10·(c1+cmu)·lambda)) générations
        eigenDecompAge++;
        final int eigenInterval = Math.max(1, (int) (n / (10.0 * (c1 + cmu) * /* lambda approx */ (4 + 3 * Math.log(n)))));
        if (eigenDecompAge >= eigenInterval)
        {
            recomputeEigen();
            eigenDecompAge = 0;
        }
    }

    /**
     * Mise à jour de C par combinaison rang-1 (chemin pc) et rang-mu (les mu parents).
     */
    private void updateCovarianceMatrix(double[][] sortedBest, double[] oldMean, boolean hSigma, int mu)
    {
        // Terme de correction pour h_sigma = 0
        final double deltaHSigma = (hSigma ? 0.0 : 1.0) * cc * (2.0 - cc);

        // C ← (1 - c1 - cmu) · C
        //     + c1 · [pc · pcᵀ + δ(hσ) · C]          (rang-1)
        //     + cmu · Σ w_i · y_i · y_iᵀ              (rang-mu)

        // Pré-calcul des y_i = (x_i - m_old) / σ  pour les mu parents
        final double[][] ys = new double[mu][n];
        for (int k = 0; k < mu; k++)
            for (int i = 0; i < n; i++)
                ys[k][i] = (sortedBest[k][i] - oldMean[i]) / sigma;

        for (int i = 0; i < n; i++)
        {
            for (int j = i; j < n; j++) // C est symétrique
            {
                double val = (1.0 - c1 - cmu + deltaHSigma * c1) * C[i][j];

                // Rang-1 : c1 · pc[i] · pc[j]
                val += c1 * pc[i] * pc[j];

                // Rang-mu : cmu · Σ w_k · y_k[i] · y_k[j]
                for (int k = 0; k < mu; k++)
                    val += cmu * recombinationWeights[k] * ys[k][i] * ys[k][j];

                C[i][j] = val;
                C[j][i] = val; // symétrie
            }
        }
    }

    // ===== Décomposition propre (Jacobi) =====

    /**
     * Recalcule B et D depuis C par la méthode de Jacobi (robuste pour petites matrices).
     * Met à jour BDinv = B · diag(1/D).
     */
    private void recomputeEigen()
    {
        final double[][] Ccopy = copyMatrix(C);
        final double[]   eigenvalues = new double[n];
        final double[][] eigenvectors = new double[n][n];

        jacobiEigen(Ccopy, eigenvalues, eigenvectors);

        // D = sqrt des valeurs propres (clampées à ε pour éviter des valeurs négatives dues au bruit)
        for (int i = 0; i < n; i++)
            D[i] = Math.sqrt(Math.max(1e-20, eigenvalues[i]));

        B = eigenvectors;

        // BDinv[i][j] = B[i][j] / D[j]  (colonne j divisée par D[j])
        BDinv = new double[n][n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                BDinv[i][j] = B[i][j] / D[j];
    }

    /**
     * Algorithme de Jacobi pour matrices symétriques réelles.
     * Converge pour n ≤ ~50 en quelques dizaines de sweeps.
     *
     * @param A           Matrice symétrique en entrée (modifiée en place → contient les vecteurs propres en sortie)
     * @param eigenvalues Tableau de sortie pour les valeurs propres
     * @param eigenvectors Matrice de sortie (colonnes = vecteurs propres)
     */
    private static void jacobiEigen(double[][] A, double[] eigenvalues, double[][] eigenvectors)
    {
        final int n = A.length;

        // Initialiser V = I (accumulera les rotations)
        final double[][] V = identityMatrix(n);

        final int maxSweeps = 100;
        final double eps = 1e-12;

        for (int sweep = 0; sweep < maxSweeps; sweep++)
        {
            // Vérifier la convergence (somme des éléments hors-diagonale)
            double offDiag = 0.0;
            for (int i = 0; i < n - 1; i++)
                for (int j = i + 1; j < n; j++)
                    offDiag += A[i][j] * A[i][j];
            if (offDiag < eps) break;

            // Un sweep = balayage de tous les couples (i, j), i < j
            for (int p = 0; p < n - 1; p++)
            {
                for (int q = p + 1; q < n; q++)
                {
                    if (Math.abs(A[p][q]) < eps) continue;

                    // Calculer la rotation de Jacobi
                    final double theta = (A[q][q] - A[p][p]) / (2.0 * A[p][q]);
                    final double t = Math.signum(theta) / (Math.abs(theta) + Math.sqrt(1.0 + theta * theta));
                    final double c = 1.0 / Math.sqrt(1.0 + t * t);
                    final double s = t * c;

                    // Appliquer la rotation : A ← Jᵀ · A · J
                    applyJacobiRotation(A, V, p, q, c, s, n);
                }
            }
        }

        // Extraire les valeurs propres (diagonale) et les vecteurs propres (colonnes de V)
        for (int i = 0; i < n; i++)
        {
            eigenvalues[i] = A[i][i];
            for (int j = 0; j < n; j++)
                eigenvectors[j][i] = V[j][i]; // colonne i = vecteur propre i
        }
    }

    private static void applyJacobiRotation(
        double[][] A, double[][] V, int p, int q, double c, double s, int n)
    {
        // Sauvegardes
        final double App = A[p][p], Aqq = A[q][q], Apq = A[p][q];

        // Mise à jour diagonale
        A[p][p] = c * c * App + 2.0 * s * c * Apq + s * s * Aqq;
        A[q][q] = s * s * App - 2.0 * s * c * Apq + c * c * Aqq;
        A[p][q] = 0.0;
        A[q][p] = 0.0;

        // Mise à jour des autres éléments de la ligne/colonne p et q
        for (int r = 0; r < n; r++)
        {
            if (r == p || r == q) continue;
            final double Arp = A[r][p], Arq = A[r][q];
            A[r][p] = A[p][r] = c * Arp + s * Arq;
            A[r][q] = A[q][r] = -s * Arp + c * Arq;
        }

        // Accumulation des rotations dans V
        for (int r = 0; r < n; r++)
        {
            final double Vrp = V[r][p], Vrq = V[r][q];
            V[r][p] = c * Vrp + s * Vrq;
            V[r][q] = -s * Vrp + c * Vrq;
        }
    }

    // ===== Opérations matricielles internes =====

    /** Multiplie B·D·z (transforme un vecteur isostrope en vecteur selon C) */
    private double[] multiplyBD(double[] z)
    {
        final double[] y = new double[n];
        for (int i = 0; i < n; i++)
            for (int j = 0; j < n; j++)
                y[i] += B[i][j] * D[j] * z[j];
        return y;
    }

    /** Multiplie BDinv·v (transforme un déplacement en espace isotrope) */
    private double[] multiplyBDinv(double[] v)
    {
        // BDinv = B · diag(1/D), donc BDinvᵀ · v = somme sur j de (B[i][j]/D[j]) * v[i]
        // On veut BDinvᵀ · v  (parce que B est orthogonale, BDinvᵀ = D^{-1} · Bᵀ)
        final double[] result = new double[n];
        for (int j = 0; j < n; j++)
        {
            double sum = 0.0;
            for (int i = 0; i < n; i++)
                sum += B[i][j] * v[i];
            result[j] = sum / D[j];
        }
        return result;
    }

    // ===== Utilitaires statiques =====

    private static double[] computeWeights(int mu, int lambda)
    {
        // w'_i = ln(lambda/2 + 0.5) - ln(i+1) pour i = 0..mu-1
        final double[] w = new double[mu];
        double sum = 0.0;
        for (int i = 0; i < mu; i++)
        {
            w[i] = Math.log(lambda / 2.0 + 0.5) - Math.log(i + 1.0);
            sum += w[i];
        }
        for (int i = 0; i < mu; i++) w[i] /= sum; // normalisation
        return w;
    }

    private static double computeMuEff(double[] weights)
    {
        double sumSq = 0.0;
        for (double w : weights) sumSq += w * w;
        return 1.0 / sumSq;
    }

    private static double norm(double[] v)
    {
        double sum = 0.0;
        for (double x : v) sum += x * x;
        return Math.sqrt(sum);
    }

    private static double[][] identityMatrix(int n)
    {
        final double[][] I = new double[n][n];
        for (int i = 0; i < n; i++) I[i][i] = 1.0;
        return I;
    }

    private static double[] onesVector(int n)
    {
        final double[] v = new double[n];
        Arrays.fill(v, 1.0);
        return v;
    }

    private static double[][] copyMatrix(double[][] M)
    {
        final double[][] copy = new double[M.length][M[0].length];
        for (int i = 0; i < M.length; i++)
            copy[i] = M[i].clone();
        return copy;
    }

    // ===== Accesseurs =====

    public double[] getMean()            { return mean.clone(); }
    public double   getSigma()           { return sigma; }
    public int      getGeneration()      { return generation; }
    public double[] getD()               { return D.clone(); }

    /**
     * Condition number de C : rapport entre la plus grande et la plus petite valeur propre.
     * Si > 1e14, C est quasi-singulière → risque de divergence numérique.
     */
    public double getConditionNumber()
    {
        double dMin = Double.MAX_VALUE, dMax = Double.MIN_VALUE;
        for (double d : D)
        {
            if (d < dMin) dMin = d;
            if (d > dMax) dMax = d;
        }
        return (dMin < 1e-20) ? Double.POSITIVE_INFINITY : (dMax * dMax) / (dMin * dMin);
    }
}