package awele.bot.competitor.noname.test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/**
 * @author Lucas Fagioli
 * Logger CSV pour les statistiques d'entraînement SPSA.
 *
 * Crée 4 fichiers CSV dans logs/run_YYYY-MM-DD_HH-mm-ss/ :
 *   - spsa_iterations.csv  : une ligne par itération SPSA
 *   - spsa_weights.csv     : évolution des poids à chaque itération
 *   - spsa_best.csv        : snapshot à chaque nouveau meilleur score
 *   - spsa_summary.csv     : résumé par phase (2 lignes au total)
 *
 * Tous les fichiers sont flushés immédiatement après chaque écriture
 * pour survivre à un arrêt brutal du processus en cours d'entraînement.
 *
 * Utilisation avec try-with-resources dans NoNameBot.learn() :
 *   try (TrainingLogger logger = new TrainingLogger()) {
 *       spsa.optimize(evaluator, botEvaluator, logger, "JOAN_SALA");
 *   }
 */
public final class TrainingLogger implements AutoCloseable
{
    // ===== Writers =====

    private final PrintWriter iterationsWriter;
    private final PrintWriter weightsWriter;
    private final PrintWriter bestWriter;
    private final PrintWriter summaryWriter;
    private final PrintWriter cmaesDistWriter;
    private final PrintWriter cmaesSamplesWriter;
    
    // ===== État =====

    private final String  runDir;
    private       boolean closed;

    // ===== En-têtes CSV =====
    
    private static final String HEADER_CMAES_DIST =
    	    "generation,phase,elapsed_ms,sigma,condition_number," +
    	    "mean_w0,mean_w1,mean_w2,mean_w3,mean_w4,mean_w5,mean_w6,mean_w7,mean_w8,mean_w9,mean_w10,mean_w11,mean_w12,mean_w13," +
    	    "std_w0,std_w1,std_w2,std_w3,std_w4,std_w5,std_w6,std_w7,std_w8,std_w9,std_w10,std_w11,std_w12,std_w13";

    	private static final String HEADER_CMAES_SAMPLES =
    	    "generation,phase,elapsed_ms,individual_index,fitness," +
    	    "w0,w1,w2,w3,w4,w5,w6,w7,w8,w9,w10,w11,w12,w13";

    private static final String HEADER_ITERATIONS =
        "iteration,phase,elapsed_ms,ak,ck,score_plus,score_minus,score_diff,window_avg,best_score";

 // Remplacez vos HEADER_WEIGHTS et HEADER_BEST par :

    private static final String HEADER_WEIGHTS =
    	    "iteration,phase," +
    	    "w_score_early,w_mobility_early,w_capture_potential_early,w_anti_capture_early,w_famine_safety_early,w_vulnerable_holes_early," +
    	    "w_score_late,w_mobility_late,w_capture_potential_late,w_anti_capture_late,w_famine_safety_late,w_vulnerable_holes_late";

    	// En-tête pour le logging du meilleur poids trouvé
    private static final String HEADER_BEST =
    	    "iteration,phase,elapsed_ms,best_score," +
    	    "w_score_early,w_mobility_early,w_capture_potential_early,w_anti_capture_early,w_famine_safety_early,w_vulnerable_holes_early," +
    	    "w_score_late,w_mobility_late,w_capture_potential_late,w_anti_capture_late,w_famine_safety_late,w_vulnerable_holes_late";
    
    private static final String HEADER_SUMMARY =
        "phase,nb_iterations,duration_ms,initial_score,final_score,best_score";

    // ===== Constructeur =====

    /**
     * Crée le dossier de logs horodaté et initialise tous les fichiers CSV.
     * @throws RuntimeException si la création des fichiers échoue
     */
    public TrainingLogger()
    {
        final String timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));

        this.runDir = "logs/run_" + timestamp + "/";
        this.closed = false;

        try
        {
            Files.createDirectories(Paths.get(this.runDir));

            this.iterationsWriter = openCsv("spsa_iterations.csv", HEADER_ITERATIONS);
            this.weightsWriter    = openCsv("spsa_weights.csv",    HEADER_WEIGHTS);
            this.bestWriter       = openCsv("spsa_best.csv",       HEADER_BEST);
            this.summaryWriter    = openCsv("spsa_summary.csv",    HEADER_SUMMARY);
            this.cmaesDistWriter    = openCsv("cmaes_distribution.csv", HEADER_CMAES_DIST);
            this.cmaesSamplesWriter = openCsv("cmaes_samples.csv",      HEADER_CMAES_SAMPLES);
        }
        catch (IOException e)
        {
            throw new RuntimeException("[TrainingLogger] Impossible de créer les fichiers : " + e.getMessage(), e);
        }
    }
    
    public void logCmaesSamples(
    	    int generation,
    	    String phase,
    	    long elapsedMs,
    	    double[][] samples,
    	    double[] fitnesses)
    	{
    	    if (closed) return;

    	    for (int k = 0; k < samples.length; k++)
    	    {
    	        StringBuilder sb = new StringBuilder();

    	        sb.append(generation).append(',')
    	          .append(phase).append(',')
    	          .append(elapsedMs).append(',')
    	          .append(k).append(',')
    	          .append(String.format(Locale.US, "%.6f", fitnesses[k]));

    	        for (double w : samples[k])
    	            sb.append(',').append(String.format(Locale.US, "%.6f", w));

    	        cmaesSamplesWriter.println(sb);
    	    }

    	    cmaesSamplesWriter.flush();
    	}
    
    public void logCmaesDistribution(
    	    int generation,
    	    String phase,
    	    long elapsedMs,
    	    double sigma,
    	    double conditionNumber,
    	    double[] mean,
    	    double[] std)
    	{
    	    if (closed) return;

    	    StringBuilder sb = new StringBuilder();

    	    sb.append(generation).append(',')
    	      .append(phase).append(',')
    	      .append(elapsedMs).append(',')
    	      .append(String.format(Locale.US, "%.6f", sigma)).append(',')
    	      .append(String.format(Locale.US, "%.6f", conditionNumber));

    	    for (double m : mean)
    	        sb.append(',').append(String.format(Locale.US, "%.6f", m));

    	    for (double s : std)
    	        sb.append(',').append(String.format(Locale.US, "%.6f", s));

    	    cmaesDistWriter.println(sb);
    	    cmaesDistWriter.flush();
    	}

    // ===== API de log =====

    /**
     * Log une itération SPSA complète.
     * Écrit dans spsa_iterations.csv ET spsa_weights.csv en un seul appel.
     * Appelé à chaque itération de la boucle SPSA.
     *
     * @param iteration  Index de l'itération courante
     * @param phase      Nom de la phase ("JOAN_SALA" ou "SELF_PLAY")
     * @param elapsedMs  Temps écoulé depuis le début de la phase
     * @param ak         Taux d'apprentissage courant
     * @param ck         Amplitude de perturbation courante
     * @param scorePlus  Score obtenu avec θ+c*Δ
     * @param scoreMinus Score obtenu avec θ-c*Δ
     * @param windowAvg  Moyenne glissante (-1.0 si fenêtre pas encore pleine)
     * @param bestScore  Meilleur score vu depuis le début
     * @param weights    Vecteur de poids courant θ
     */
    public void logIteration(
        int      iteration,
        String   phase,
        long     elapsedMs,
        double   ak,
        double   ck,
        double   scorePlus,
        double   scoreMinus,
        double   windowAvg,
        double   bestScore,
        double[] weights)
    {
        if (closed) return;

        // spsa_iterations.csv
        iterationsWriter.printf(Locale.US,
        	    "%d,%s,%d,%.6f,%.6f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
            iteration, phase, elapsedMs,
            ak, ck,
            scorePlus, scoreMinus, scorePlus - scoreMinus,
            windowAvg, bestScore);
        iterationsWriter.flush();

        // spsa_weights.csv — même numéro d'itération, fichier séparé pour clarté des graphiques
        final StringBuilder wb = new StringBuilder();
        wb.append(iteration).append(',').append(phase);
        for (final double w : weights)
            wb.append(',').append(String.format(Locale.US, "%.6f", w));
        weightsWriter.println(wb);
        weightsWriter.flush();
    }

    /**
     * Log dans spsa_best.csv uniquement quand un nouveau record est battu.
     * Permet de tracer la courbe d'amélioration sans le bruit des itérations normales.
     *
     * @param iteration  Itération où le record a été battu
     * @param phase      Nom de la phase
     * @param elapsedMs  Temps écoulé depuis le début de la phase
     * @param bestScore  Nouveau meilleur score
     * @param weights    Poids correspondant à ce meilleur score
     */
    public void logBest(
        int      iteration,
        String   phase,
        long     elapsedMs,
        double   bestScore,
        double[] weights)
    {
        if (closed) return;

        final StringBuilder sb = new StringBuilder();
        sb.append(iteration).append(',')
          .append(phase).append(',')
          .append(elapsedMs).append(',')
          .append(String.format(Locale.US, "%.6f", bestScore));
        for (final double w : weights)
            sb.append(',').append(String.format(Locale.US, "%.6f", w));

        bestWriter.println(sb);
        bestWriter.flush();
    }

    /**
     * Log dans spsa_summary.csv à la fin de chaque phase.
     * Produit une vue d'ensemble de la phase pour comparaison rapide.
     *
     * @param phase         Nom de la phase
     * @param nbIterations  Nombre d'itérations réalisées pendant la phase
     * @param durationMs    Durée réelle de la phase
     * @param initialScore  Score moyen observé au début (premières itérations)
     * @param finalScore    Score moyen observé à la fin (dernières itérations)
     * @param bestScore     Meilleur score observé pendant toute la phase
     */
    public void logSummary(
        String phase,
        int    nbIterations,
        long   durationMs,
        double initialScore,
        double finalScore,
        double bestScore)
    {
        if (closed) return;

        summaryWriter.printf("%s,%d,%d,%.6f,%.6f,%.6f%n",
            phase, nbIterations, durationMs,
            initialScore, finalScore, bestScore);
        summaryWriter.flush();
    }

    // ===== AutoCloseable =====

    @Override
    public void close()
    {
        if (closed) return;
        closed = true;

        closeQuietly(iterationsWriter);
        closeQuietly(weightsWriter);
        closeQuietly(bestWriter);
        closeQuietly(summaryWriter);
        closeQuietly(cmaesDistWriter);
        closeQuietly(cmaesSamplesWriter);
    }

    // ===== Utilitaires privés =====

    private PrintWriter openCsv(String filename, String header) throws IOException
    {
        final PrintWriter pw = new PrintWriter(new FileWriter(this.runDir + filename, false));
        pw.println(header);
        pw.flush();
        return pw;
    }

    private static void closeQuietly(PrintWriter pw)
    {
        if (pw != null)
            try { pw.close(); } catch (Exception ignored) {}
    }

    public String getRunDir() { return this.runDir; }
}