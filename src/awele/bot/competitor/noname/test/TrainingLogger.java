package awele.bot.competitor.noname.test;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

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

    // ===== État =====

    private final String  runDir;
    private       boolean closed;

    // ===== En-têtes CSV =====

    private static final String HEADER_ITERATIONS =
        "iteration,phase,elapsed_ms,ak,ck,score_plus,score_minus,score_diff,window_avg,best_score";

    private static final String HEADER_WEIGHTS =
    	    "iteration,phase," +
    	    "w_score_early,w_krou_early,w_dangerous_early,w_empty_early,w_sequence_early," +
    	    "w_score_late,w_krou_late,w_dangerous_late,w_empty_late,w_sequence_late";

    private static final String HEADER_BEST =
    	    "iteration,phase,elapsed_ms,best_score," +
    	    "w_score_early,w_krou_early,w_dangerous_early,w_empty_early,w_sequence_early," +
    	    "w_score_late,w_krou_late,w_dangerous_late,w_empty_late,w_sequence_late";

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
        }
        catch (IOException e)
        {
            throw new RuntimeException("[TrainingLogger] Impossible de créer les fichiers : " + e.getMessage(), e);
        }
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
        iterationsWriter.printf("%d,%s,%d,%.6f,%.6f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
            iteration, phase, elapsedMs,
            ak, ck,
            scorePlus, scoreMinus, scorePlus - scoreMinus,
            windowAvg, bestScore);
        iterationsWriter.flush();

        // spsa_weights.csv — même numéro d'itération, fichier séparé pour clarté des graphiques
        final StringBuilder wb = new StringBuilder();
        wb.append(iteration).append(',').append(phase);
        for (final double w : weights)
            wb.append(',').append(String.format("%.6f", w));
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
          .append(String.format("%.6f", bestScore));
        for (final double w : weights)
            sb.append(',').append(String.format("%.6f", w));

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