package awele.bot.competitor.noname.algorithms.training.cmaes;

/**
 * @author Lucas Fagioli
 * Configuration de l'algorithme CMA-ES (Covariance Matrix Adaptation Evolution Strategy).
 *
 * <p>Les valeurs par défaut suivent les recommandations de Hansen (2016) pour un
 * problème bruité de petite dimension (n ≤ 20). Les paramètres critiques sont
 * sigma0 et lambda ; les autres sont calculés automatiquement depuis n si on
 * utilise {@link #defaultConfig(int)}.</p>
 */
public final class CMAESConfig1
{
    // ===== Paramètres de la distribution initiale =====

    /**
     * Écart-type initial (step size). Doit être de l'ordre de 1/3 de la plage
     * attendue des poids. Trop grand → exploration trop large ; trop petit → convergence prématurée.
     */
    public final double sigma0;

    // ===== Paramètres de population =====

    /**
     * Taille de la population (nombre d'individus évalués par génération).
     * Hansen recommande lambda = 4 + floor(3*ln(n)). Augmenter lambda améliore
     * la robustesse au bruit au détriment de la vitesse de convergence.
     */
    public final int lambda;

    /**
     * Nombre de parents sélectionnés (mu = lambda/2 est la valeur classique).
     */
    public final int mu;

    // ===== Bornes des poids =====

    public final double weightMin;
    public final double weightMax;

    // ===== Paramètres d'évaluation =====

    /**
     * Nombre de parties jouées par individu pour estimer sa fitness.
     * Plus c'est élevé, moins il y a de bruit, mais l'évaluation est plus lente.
     */
    public final int nbGamesPerEval;

    /**
     * Profondeur de recherche utilisée pendant l'entraînement.
     */
    public final int trainingDepth;

    // ===== Budget temps =====

    public final long timeBudgetMs;

    // ===== Ré-évaluation du meilleur =====

    /**
     * Nombre de générations entre deux ré-évaluations du meilleur individu connu.
     * Permet de compenser le bruit de l'évaluation stochastique.
     */
    public final int reEvalInterval;

    public CMAESConfig1(
        double sigma0,
        int    lambda,
        int    mu,
        double weightMin,
        double weightMax,
        int    nbGamesPerEval,
        int    trainingDepth,
        long   timeBudgetMs,
        int    reEvalInterval)
    {
        if (mu >= lambda)
            throw new IllegalArgumentException("mu doit être strictement inférieur à lambda.");
        if (sigma0 <= 0.0)
            throw new IllegalArgumentException("sigma0 doit être strictement positif.");

        this.sigma0        = sigma0;
        this.lambda        = lambda;
        this.mu            = mu;
        this.weightMin     = weightMin;
        this.weightMax     = weightMax;
        this.nbGamesPerEval = nbGamesPerEval;
        this.trainingDepth = trainingDepth;
        this.timeBudgetMs  = timeBudgetMs;
        this.reEvalInterval = reEvalInterval;
    }

    /**
     * Configuration par défaut calculée depuis la dimension du problème.
     *
     * @param n Dimension du vecteur de paramètres (nombre de poids)
     * @return Une configuration CMA-ES prête à l'emploi
     */
    public static CMAESConfig1 defaultConfig(int n)
    {
        // Recommandations de Hansen (2016)
        final int lambda = 4 + (int) Math.floor(3.0 * Math.log(n));
        final int mu     = lambda / 2;

        // sigma0 = 1/4 de la demi-plage → couvre environ la moitié de l'espace au départ
        final double wMin   = -20.0;
        final double wMax   =  20.0;
        final double sigma0 = (wMax - wMin) / 8.0;

        return new CMAESConfig1(
            sigma0,
            lambda,
            mu,
            wMin,
            wMax,
            6,           // nbGamesPerEval
            5,           // trainingDepth
            54 * 60_000L, // timeBudgetMs : ~54 min (laisse 5 min au GA + 1 min de marge)
            5            // reEvalInterval
        );
    }
}