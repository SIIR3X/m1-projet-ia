package awele.bot.competitor.noname.algorithms.training.cmaes;

import java.util.Arrays;
import java.util.Random;

import awele.bot.competitor.noname.algorithms.heuristics.evaluation.PositionEvaluator;
import awele.bot.competitor.noname.algorithms.training.common.AdaptiveOpponentSelector.OpponentProfile;
import awele.bot.competitor.noname.algorithms.training.common.BotEvaluator;
import awele.bot.competitor.noname.test.TrainingLogger;

/**
 * @author Lucas Fagioli
 * Boucle principale de CMA-ES (Covariance Matrix Adaptation Evolution Strategy).
 *
 * <p>Orchestre {@link CMAESState} (mathématique), {@link BotEvaluator} (fitness)
 * et {@link TrainingLogger} (log CSV). Ne contient aucune formule mathématique :
 * toute l'adaptation de la distribution est déléguée à {@link CMAESState}.</p>
 *
 * <h3>Déroulement d'une génération</h3>
 * <ol>
 *   <li>Échantillonner lambda individus depuis N(m, σ²·C)</li>
 *   <li>Clipper chaque individu dans [weightMin, weightMax]</li>
 *   <li>Évaluer chaque individu via {@link BotEvaluator#evaluate}</li>
 *   <li>Trier par fitness décroissante</li>
 *   <li>Mettre à jour la distribution via {@link CMAESState#update}</li>
 *   <li>Mémoriser le meilleur individu tout-temps</li>
 *   <li>Logger</li>
 * </ol>
 */
public final class CMAES2A
{
    // ===== Composants =====

    private final CMAESConfig1  config;
    private final BotEvaluator botEvaluator;
    private final Random       random;

    // ===== Meilleur individu connu =====

    private double[] bestWeights;
    private double   bestFitness;

    // ===== Suivi =====

    private int generationCount;

    public CMAES2A(CMAESConfig1 config, BotEvaluator botEvaluator)
    {
        this.config       = config;
        this.botEvaluator = botEvaluator;
        this.random       = new Random(0xC0FFEEL);
        this.bestWeights  = null;
        this.bestFitness  = Double.NEGATIVE_INFINITY;
        this.generationCount = 0;
    }

    // ===== Point d'entrée =====

    /**
     * Lance l'optimisation CMA-ES et applique les meilleurs poids à l'évaluateur.
     *
     * @param evaluator  L'évaluateur dont on veut optimiser les poids
     * @param logger     Logger CSV pour le suivi (peut être {@code null})
     * @param phaseName  Nom de la phase affiché dans les logs
     */
    public void optimize(PositionEvaluator evaluator, TrainingLogger logger, String phaseName)
    {
        final double[] initialWeights = evaluator.getWeights();
        final int      n              = initialWeights.length;

        final CMAESState state = new CMAESState(
            initialWeights, config.sigma0, config.mu, config.lambda);

        bestWeights = initialWeights.clone();
        bestFitness = Double.NEGATIVE_INFINITY;

        final long startTime = System.currentTimeMillis();
        final long deadline  = startTime + config.timeBudgetMs;

        double initialScore = Double.NEGATIVE_INFINITY;
        double finalScore   = Double.NEGATIVE_INFINITY;

        while (System.currentTimeMillis() < deadline)
        {
            // ===== 1. Échantillonnage =====
            final double[][] rawSamples = state.samplePopulation(random, config.lambda);

            // Clipping dans [weightMin, weightMax]
            for (double[] sample : rawSamples)
                clip(sample);

            // ===== 2. Évaluation =====
            final double[] fitnesses = new double[config.lambda];

            for (int k = 0; k < config.lambda; k++)
            {
                final OpponentProfile profile = botEvaluator.sampleProfile(rawSamples[k]);
                fitnesses[k] = botEvaluator.evaluate(
                    rawSamples[k], config.nbGamesPerEval, config.trainingDepth, profile);
            }

            // ===== 3. Tri par fitness décroissante =====
            final Integer[] indices = sortedIndices(fitnesses);

            // ===== 4. Mise à jour de la distribution =====
            // On passe les mu meilleurs individus (coordonnées non-clippées car on veut
            // que CMA-ES puisse explorer au-delà des bornes et revenir naturellement)
            final double[][] sortedBest = new double[config.mu][n];
            for (int k = 0; k < config.mu; k++)
                sortedBest[k] = rawSamples[indices[k]];

            state.update(sortedBest);

            // ===== 5. Meilleur de la génération =====
            final double genBestFitness = fitnesses[indices[0]];
            final double[] genBestWeights = rawSamples[indices[0]].clone();

            final double genAvgFitness = averageFitness(fitnesses);
            finalScore = genBestFitness;

            if (generationCount == 0)
                initialScore = genBestFitness;

            // ===== 6. Mise à jour du meilleur global =====
            if (genBestFitness > bestFitness)
            {
                bestFitness = genBestFitness;
                bestWeights = genBestWeights.clone();
                botEvaluator.updateBestWeights(bestWeights);

                if (logger != null)
                    logger.logBest(
                        generationCount, phaseName,
                        System.currentTimeMillis() - startTime,
                        bestFitness, bestWeights);
            }

            // ===== 7. Ré-évaluation périodique du meilleur global =====
            // Compense le bruit stochastique de l'évaluation
            if (generationCount > 0 && generationCount % config.reEvalInterval == 0)
                reEvaluateBest(phaseName, logger, startTime);

            // ===== 8. Logging =====
            if (logger != null)
            {
                logGeneration(
                    logger, phaseName,
                    System.currentTimeMillis() - startTime,
                    state, genBestFitness, genAvgFitness,
                    bestFitness, state.getMean());
            }
            
            if (logger != null) {
                double[] std = empiricalStd(rawSamples);

                logger.logCmaesDistribution(
                    generationCount, phaseName,
                    System.currentTimeMillis() - startTime,
                    state.getSigma(),
                    state.getConditionNumber(),
                    state.getMean(),
                    std
                );

                logger.logCmaesSamples(
                    generationCount, phaseName,
                    System.currentTimeMillis() - startTime,
                    rawSamples,
                    fitnesses
                );
            }

            generationCount++;
        }

        // ===== Finalisation =====
        evaluator.setWeights(bestWeights);

        if (logger != null)
            logger.logSummary(
                phaseName, generationCount,
                System.currentTimeMillis() - startTime,
                initialScore, finalScore, bestFitness);
    }

    // ===== Ré-évaluation =====

    /**
     * Ré-évalue le meilleur individu connu avec plusieurs parties supplémentaires
     * pour affiner son score et éventuellement le mettre à jour.
     */
    private void reEvaluateBest(String phaseName, TrainingLogger logger, long startTime)
    {
        final OpponentProfile profile = botEvaluator.sampleProfile(bestWeights);
        final double reEvalScore = botEvaluator.evaluate(
            bestWeights, config.nbGamesPerEval * 5, config.trainingDepth, profile);

        // On combine l'ancien score et le nouveau pour lisser le bruit
        final double smoothed = 0.5 * bestFitness + 0.5 * reEvalScore;

        if (smoothed > bestFitness)
        {
            bestFitness = smoothed;
            botEvaluator.updateBestWeights(bestWeights);

            if (logger != null)
                logger.logBest(
                    generationCount, phaseName + "_REEVAL",
                    System.currentTimeMillis() - startTime,
                    bestFitness, bestWeights);
        }
    }

    // ===== Logging =====

    /**
     * Adapte le logging CMA-ES au format de {@link TrainingLogger}.
     *
     * <p>On réutilise {@code logIteration} en mappant les grandeurs CMA-ES :</p>
     * <ul>
     *   <li>ak   → sigma (step size courant)</li>
     *   <li>ck   → condition number de C (indicateur de santé numérique)</li>
     *   <li>scorePlus  → fitness du meilleur de la génération</li>
     *   <li>scoreMinus → fitness moyenne de la génération</li>
     *   <li>windowAvg  → meilleur fitness tout-temps</li>
     *   <li>weights    → moyenne de la distribution (m)</li>
     * </ul>
     */
    private void logGeneration(
        TrainingLogger logger,
        String         phaseName,
        long           elapsedMs,
        CMAESState     state,
        double         genBestFitness,
        double         genAvgFitness,
        double         bestFitnessEver,
        double[]       mean)
    {
        logger.logIteration(
            generationCount, phaseName, elapsedMs,
            state.getSigma(),          // ak → sigma
            state.getConditionNumber(), // ck → condition number
            genBestFitness,            // scorePlus → best of gen
            genAvgFitness,             // scoreMinus → avg of gen
            bestFitnessEver,           // windowAvg → best ever
            bestFitnessEver,           // bestScore  → idem
            mean                       // weights → mean vector
        );
    }

    // ===== Utilitaires =====

    private void clip(double[] weights)
    {
        for (int i = 0; i < weights.length; i++)
        {
            if (weights[i] < config.weightMin) weights[i] = config.weightMin;
            if (weights[i] > config.weightMax) weights[i] = config.weightMax;
        }
    }

    /**
     * Retourne les indices triés par fitness décroissante (meilleur en premier).
     */
    private static Integer[] sortedIndices(double[] fitnesses)
    {
        final Integer[] idx = new Integer[fitnesses.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Double.compare(fitnesses[b], fitnesses[a]));
        return idx;
    }

    private static double averageFitness(double[] fitnesses)
    {
        double sum = 0.0;
        for (double f : fitnesses) sum += f;
        return sum / fitnesses.length;
    }

    // ===== Accesseurs =====

    public double[] getBestWeights()    { return bestWeights != null ? bestWeights.clone() : null; }
    public double   getBestFitness()    { return bestFitness; }
    public int      getGenerationCount(){ return generationCount; }
    
    private static double[] empiricalStd(double[][] samples) {
        int lambda = samples.length;
        int n = samples[0].length;

        double[] mean = new double[n];
        for (double[] x : samples)
            for (int i = 0; i < n; i++)
                mean[i] += x[i];
        for (int i = 0; i < n; i++)
            mean[i] /= lambda;

        double[] var = new double[n];
        for (double[] x : samples)
            for (int i = 0; i < n; i++) {
                double d = x[i] - mean[i];
                var[i] += d * d;
            }

        double[] std = new double[n];
        for (int i = 0; i < n; i++) {
            double denom = Math.max(1, lambda - 1);
            std[i] = Math.sqrt(var[i] / denom);
        }
        return std;
    }
}