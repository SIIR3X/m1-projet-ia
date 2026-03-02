package awele.bot.competitor.hybridprov2;

import awele.bot.CompetitorBot;
import awele.core.Board;
import awele.core.InvalidBotException;
import awele.data.AweleData;
import awele.data.AweleObservation;
import java.util.ArrayList;
import java.util.List;

/**
 * HybridProV2 - Version optimale pour battre tous les adversaires.
 * 
 * Innovations :
 *  1. Co-évolution : 2 populations s'affrontent mutuellement
 *  2. Hall of Fame : garde les meilleurs individus de toutes les générations
 *  3. 6 poids optimaux (compromis complexité/performance)
 *  4. Évaluation multi-adversaires (diversité)
 *  5. Initialisation depuis régression logistique
 *  6. Mutation adaptative intelligente
 */
public class HybridProV2 extends CompetitorBot {

    // -------------------------------------------------------------------------
    // Constantes
    // -------------------------------------------------------------------------

    private static final double INF = Double.MAX_VALUE / 2.0;
    private static final double WIN_SCORE = 100000.0;
    private static final long TIME_LIMIT_MS = 80;
    private static final long MAX_TRAINING_TIME_MS = 57 * 60 * 1000; // 57 minutes

    // Algorithme génétique
    private static final int POPULATION_SIZE = 30;
    private static final int NUM_GENERATIONS = 100;
    private static final int TOURNAMENT_SIZE = 4;
    private static final int GAMES_PER_EVAL = 3;
    private static final int NUM_WEIGHTS = 6;

    // -------------------------------------------------------------------------
    // Génome
    // -------------------------------------------------------------------------

    private static class Genome {
        double[] weights;
        double fitness;

        Genome() {
            this.weights = new double[NUM_WEIGHTS];
            this.fitness = 0.0;
        }

        Genome(double[] weights) {
            this.weights = weights.clone();
            this.fitness = 0.0;
        }

        protected Genome clone() {
            Genome copy = new Genome(this.weights);
            copy.fitness = this.fitness;  // Préserver la fitness !
            return copy;
        }
    }

    // -------------------------------------------------------------------------
    // Variables
    // -------------------------------------------------------------------------

    private double[] bestWeights;
    private java.util.Map<Long, TranspositionEntry> transpositionTable;
    private List<Genome> hallOfFame; // Meilleurs de toutes les générations

    private static class TranspositionEntry {
        double value;
        int depth;
        byte flag;

        TranspositionEntry(double value, int depth, byte flag) {
            this.value = value;
            this.depth = depth;
            this.flag = flag;
        }
    }

    // -------------------------------------------------------------------------
    // Constructeur
    // -------------------------------------------------------------------------

    public HybridProV2() {
        try {
            addAuthor("Lezer - HybridProV2");
        } catch (InvalidBotException e) {
            e.printStackTrace();
        }
        setBotName("HybridProV2");

        this.bestWeights = new double[NUM_WEIGHTS];
        initializeDefaultWeights(this.bestWeights);
        
        this.transpositionTable = new java.util.HashMap<>(100000);
        this.hallOfFame = new ArrayList<>();
    }

    private void initializeDefaultWeights(double[] w) {
        w[0] = 4.0;  // scoreDiff
        w[1] = 0.3;  // seedsDiff
        w[2] = 0.6;  // captureBonus
        w[3] = 0.2;  // mobility
        w[4] = 0.15; // controlCenter
        w[5] = 0.25; // endgameWeight
    }

    // -------------------------------------------------------------------------
    // Cycle de vie
    // -------------------------------------------------------------------------

    @Override
    public void initialize() {
        if (this.transpositionTable != null) {
            this.transpositionTable.clear();
        }
    }

    @Override
    public void finish() {
        // Rien
    }

    // -------------------------------------------------------------------------
    // Apprentissage : Régression + Co-évolution
    // -------------------------------------------------------------------------

    @Override
    public void learn() {
        long startTime = System.currentTimeMillis();

        System.out.println("=== HYBRIDPROV2: Phase 1 - Logistic Regression ===");
        
        // Phase 1 : Régression logistique
        double[] logisticWeights = learnFromLogisticRegression();
        System.out.println("Logistic weights: " + formatWeights(logisticWeights));

        System.out.println("\n=== HYBRIDPROV2: Phase 2 - Co-Evolution ===");

        // Phase 2 : Initialiser les populations
        Genome[] populationA = new Genome[POPULATION_SIZE];
        Genome[] populationB = new Genome[POPULATION_SIZE];
        
        java.util.Random rand = new java.util.Random();
        
        // Population A : basée sur régression avec variations
        for (int i = 0; i < POPULATION_SIZE; i++) {
            populationA[i] = new Genome(logisticWeights);
            if (i >= 2) { // Garder 2 copies exactes
                for (int j = 0; j < NUM_WEIGHTS; j++) {
                    populationA[i].weights[j] += (rand.nextDouble() - 0.5) * 0.8;
                }
            }
        }
        
        // Population B : variations plus agressives
        for (int i = 0; i < POPULATION_SIZE; i++) {
            populationB[i] = new Genome(logisticWeights);
            for (int j = 0; j < NUM_WEIGHTS; j++) {
                populationB[i].weights[j] += (rand.nextDouble() - 0.5) * 1.5;
            }
        }

        // Phase 3 : Co-évolution
        int gen = 0;
        while (gen < NUM_GENERATIONS) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > MAX_TRAINING_TIME_MS) {
                System.out.println("Timeout reached at generation " + gen);
                break;
            }

            // Évaluation : chaque population joue contre l'autre
            evaluatePopulationCoEvolution(populationA, populationB);
            evaluatePopulationCoEvolution(populationB, populationA);

            // Tri
            java.util.Arrays.sort(populationA, (a, b) -> Double.compare(b.fitness, a.fitness));
            java.util.Arrays.sort(populationB, (a, b) -> Double.compare(b.fitness, a.fitness));

            // Hall of Fame : ajouter les meilleurs
            if (gen % 10 == 0) {
                addToHallOfFame(populationA[0]);
                addToHallOfFame(populationB[0]);
            }

            // Affichage
            if (gen % 10 == 0 || gen == NUM_GENERATIONS - 1) {
                double avgA = (populationA[0].fitness + populationA[1].fitness + populationA[2].fitness) / 3.0;
                double avgB = (populationB[0].fitness + populationB[1].fitness + populationB[2].fitness) / 3.0;
                
                System.out.println("Gen " + gen + "/" + NUM_GENERATIONS + 
                                   " - PopA best: " + String.format("%.1f", populationA[0].fitness) +
                                   " (avg: " + String.format("%.1f", avgA) + ")" +
                                   " - PopB best: " + String.format("%.1f", populationB[0].fitness) +
                                   " (avg: " + String.format("%.1f", avgB) + ")" +
                                   " - Time: " + (elapsed / 1000) + "s");
            }

            // Nouvelle génération avec migration croisée
            Genome[] newPopA = new Genome[POPULATION_SIZE];
            Genome[] newPopB = new Genome[POPULATION_SIZE];
            
            // Élitisme : 3 meilleurs + 1 de l'autre population
            for (int i = 0; i < 3; i++) {
                newPopA[i] = populationA[i].clone();
                newPopB[i] = populationB[i].clone();
            }
            newPopA[3] = populationB[0].clone(); // Migration
            newPopB[3] = populationA[0].clone(); // Migration

            // Génération du reste
            for (int i = 4; i < POPULATION_SIZE; i++) {
                newPopA[i] = evolveGenome(populationA, gen);
                newPopB[i] = evolveGenome(populationB, gen);
            }

            populationA = newPopA;
            populationB = newPopB;
            gen++;
        }

        // Évaluation finale contre Hall of Fame
        evaluatePopulationCoEvolution(populationA, populationB);
        java.util.Arrays.sort(populationA, (a, b) -> Double.compare(b.fitness, a.fitness));
        
        evaluatePopulationCoEvolution(populationB, populationA);
        java.util.Arrays.sort(populationB, (a, b) -> Double.compare(b.fitness, a.fitness));

        // Sélection du meilleur absolu
        Genome best = (populationA[0].fitness > populationB[0].fitness) 
                    ? populationA[0] 
                    : populationB[0];
        
        // Tester contre Hall of Fame
        for (Genome champion : hallOfFame) {
            double winRate = testAgainst(best, champion);
            if (winRate < 0.5) {
                System.out.println("Hall of Fame champion selected! Fitness: " + 
                                   String.format("%.2f", champion.fitness) + 
                                   " vs current best: " + String.format("%.2f", best.fitness));
                best = champion; // Un champion du Hall of Fame est meilleur
                break;
            }
        }

        this.bestWeights = best.weights.clone();

        long totalTime = System.currentTimeMillis() - startTime;
        System.out.println("\n=== Training complete ===");
        System.out.println("Time: " + (totalTime / 1000) + "s");
        System.out.println("Best fitness: " + String.format("%.2f", best.fitness));
        System.out.println("Best weights: " + formatWeights(this.bestWeights));
        System.out.println("Hall of Fame size: " + hallOfFame.size());
    }

    private void addToHallOfFame(Genome genome) {
        // Ajouter seulement si vraiment bon et différent
        if (genome.fitness < 500.0) return;
        
        for (Genome champ : hallOfFame) {
            if (isSimilar(genome.weights, champ.weights)) return;
        }
        
        hallOfFame.add(genome.clone());
        
        // Limiter la taille
        if (hallOfFame.size() > 10) {
            hallOfFame.remove(0);
        }
    }

    private boolean isSimilar(double[] w1, double[] w2) {
        double dist = 0;
        for (int i = 0; i < w1.length; i++) {
            double diff = w1[i] - w2[i];
            dist += diff * diff;
        }
        return Math.sqrt(dist) < 0.5;
    }

    private double testAgainst(Genome genome1, Genome genome2) {
        EvaluationBot bot1 = new EvaluationBot(genome1.weights, 4);
        EvaluationBot bot2 = new EvaluationBot(genome2.weights, 4);
        return GameSimulator.evaluateWinRate(bot1, bot2, 4);
    }

    /**
     * Régression logistique → 6 poids.
     */
    private double[] learnFromLogisticRegression() {
        AweleData data = AweleData.getInstance();
        int n = data.size();
        
        double[][] features = new double[n][4];
        double[] labels = new double[n];

        for (int i = 0; i < n; i++) {
            AweleObservation obs = data.get(i);
            int[] ph = obs.getPlayerHoles();
            int[] oh = obs.getOppenentHoles();
            
            features[i][0] = sumArray(ph);
            features[i][1] = sumArray(oh);
            features[i][2] = countTwoThree(oh);
            features[i][3] = countTwoThree(ph);
            
            labels[i] = obs.isWon() ? 1.0 : 0.0;
        }

        // Normalisation
        double[] means = new double[4];
        double[] stds = new double[4];
        for (int j = 0; j < 4; j++) {
            double sum = 0;
            for (int i = 0; i < n; i++) sum += features[i][j];
            means[j] = sum / n;
            
            double var = 0;
            for (int i = 0; i < n; i++) {
                double diff = features[i][j] - means[j];
                var += diff * diff;
            }
            stds[j] = Math.sqrt(var / n);
            if (stds[j] < 1e-10) stds[j] = 1.0;
        }

        double[][] normFeatures = new double[n][4];
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < 4; j++) {
                normFeatures[i][j] = (features[i][j] - means[j]) / stds[j];
            }
        }

        // Gradient descent
        double[] w = new double[4];
        double b = 0.0;
        double lr = 0.1;

        for (int epoch = 0; epoch < 1000; epoch++) {
            double[] dw = new double[4];
            double db = 0.0;

            for (int i = 0; i < n; i++) {
                double z = b;
                for (int j = 0; j < 4; j++) z += w[j] * normFeatures[i][j];
                double pred = sigmoid(z);
                double err = pred - labels[i];

                for (int j = 0; j < 4; j++) dw[j] += err * normFeatures[i][j];
                db += err;
            }

            for (int j = 0; j < 4; j++) w[j] -= lr * dw[j] / n;
            b -= lr * db / n;
        }

        // Mapper à 6 poids
        double[] weights = new double[NUM_WEIGHTS];
        weights[0] = 4.0;              // scoreDiff (dominant)
        weights[1] = w[0] * 0.15;      // seedsDiff
        weights[2] = w[2] * 1.5;       // captureBonus
        weights[3] = 0.25;             // mobility
        weights[4] = 0.2;              // controlCenter
        weights[5] = 0.3;              // endgame

        return weights;
    }

    /**
     * Évaluation par co-évolution : population joue contre l'autre.
     */
    private void evaluatePopulationCoEvolution(Genome[] population, Genome[] opponents) {
        for (int i = 0; i < population.length; i++) {
            double totalWins = 0;
            int totalGames = 0;
            
            // Jouer contre les 5 meilleurs adversaires
            for (int j = 0; j < Math.min(5, opponents.length); j++) {
                EvaluationBot bot = new EvaluationBot(population[i].weights, 4);
                EvaluationBot opp = new EvaluationBot(opponents[j].weights, 4);
                
                double winRate = GameSimulator.evaluateWinRate(bot, opp, GAMES_PER_EVAL);
                totalWins += winRate * GAMES_PER_EVAL;
                totalGames += GAMES_PER_EVAL;
            }
            
            population[i].fitness = (totalWins / totalGames) * 1000.0;
        }
    }

    private Genome evolveGenome(Genome[] population, int currentGen) {
        Genome parent1 = tournamentSelection(population);
        Genome parent2 = tournamentSelection(population);
        Genome child = crossover(parent1, parent2);
        mutateAdaptive(child, currentGen);
        return child;
    }

    private Genome tournamentSelection(Genome[] population) {
        java.util.Random rand = new java.util.Random();
        Genome best = null;

        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            int idx = rand.nextInt(population.length);
            if (best == null || population[idx].fitness > best.fitness) {
                best = population[idx];
            }
        }

        return best;
    }

    private Genome crossover(Genome parent1, Genome parent2) {
        Genome child = new Genome();
        java.util.Random rand = new java.util.Random();

        for (int i = 0; i < NUM_WEIGHTS; i++) {
            child.weights[i] = rand.nextBoolean() ? parent1.weights[i] : parent2.weights[i];
        }

        return child;
    }

    private void mutateAdaptive(Genome genome, int currentGen) {
        java.util.Random rand = new java.util.Random();
        
        // Mutation décroissante
        double mutationRate = 0.3 * (1.0 - currentGen / (double) NUM_GENERATIONS);
        double mutationStrength = 0.6 * (1.0 - currentGen / (double) NUM_GENERATIONS);

        for (int i = 0; i < NUM_WEIGHTS; i++) {
            if (rand.nextDouble() < mutationRate) {
                // Mutation plus forte sur scoreDiff et capture (poids critiques)
                double strength = (i == 0 || i == 2) ? mutationStrength * 1.5 : mutationStrength;
                genome.weights[i] += (rand.nextDouble() - 0.5) * 2.0 * strength;
                genome.weights[i] = Math.max(-8.0, Math.min(8.0, genome.weights[i]));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Décision : NegaMax optimisé avec 6 poids
    // -------------------------------------------------------------------------

    @Override
    public double[] getDecision(Board board) {
        double[] scores = new double[Board.NB_HOLES];
        int player = board.getCurrentPlayer();
        boolean[] valid = board.validMoves(player);

        long startTime = System.currentTimeMillis();

        int totalSeeds = board.getNbSeeds();
        int maxDepth = 15;
        if (totalSeeds > 24) maxDepth = 12;

        double[] bestScores = new double[Board.NB_HOLES];
        for (int i = 0; i < Board.NB_HOLES; i++) bestScores[i] = -INF;

        for (int depth = 1; depth <= maxDepth; depth++) {
            boolean timeExceeded = false;

            for (int move = 0; move < Board.NB_HOLES; move++) {
                if (!valid[move]) {
                    scores[move] = -INF;
                    continue;
                }

                if (System.currentTimeMillis() - startTime > TIME_LIMIT_MS) {
                    timeExceeded = true;
                    break;
                }

                try {
                    Board nextBoard = board.playMoveSimulationBoard(player, buildDecision(move));
                    scores[move] = -negamax(nextBoard, depth - 1, -INF, INF);
                } catch (InvalidBotException e) {
                    scores[move] = -INF;
                }
            }

            if (timeExceeded) break;

            for (int i = 0; i < Board.NB_HOLES; i++) bestScores[i] = scores[i];
        }

        return bestScores;
    }

    private double negamax(Board board, int depth, double alpha, double beta) {
        int currentPlayer = board.getCurrentPlayer();
        double alphaOrig = alpha;

        // Table de transposition
        long hash = computeBoardHash(board);
        TranspositionEntry entry = this.transpositionTable.get(hash);
        if (entry != null && entry.depth >= depth) {
            if (entry.flag == 0) return entry.value;
            else if (entry.flag == 1) alpha = Math.max(alpha, entry.value);
            else if (entry.flag == 2) beta = Math.min(beta, entry.value);
            if (alpha >= beta) return entry.value;
        }

        if (board.getNbSeeds() <= 6) return terminalScore(board, currentPlayer);

        boolean[] valid = board.validMoves(currentPlayer);
        boolean hasMove = false;
        for (boolean v : valid) if (v) { hasMove = true; break; }
        if (!hasMove) return terminalScore(board, currentPlayer);

        if (depth == 0) return evaluateWith6Weights(board, currentPlayer);

        // Move ordering
        int[] moveOrder = orderMoves(board, currentPlayer, valid);

        double best = -INF;

        for (int i = 0; i < moveOrder.length; i++) {
            int move = moveOrder[i];
            if (move < 0) break;

            try {
                Board nextBoard = board.playMoveSimulationBoard(currentPlayer, buildDecision(move));

                double val = (nextBoard.getCurrentPlayer() == currentPlayer)
                    ? negamax(nextBoard, depth - 1, alpha, beta)
                    : -negamax(nextBoard, depth - 1, -beta, -alpha);

                if (val > best) best = val;
                if (val > alpha) alpha = val;
                if (alpha >= beta) break;
            } catch (InvalidBotException e) {}
        }

        byte flag = (best <= alphaOrig) ? (byte)2 : (best >= beta) ? (byte)1 : (byte)0;
        this.transpositionTable.put(hash, new TranspositionEntry(best, depth, flag));

        return best;
    }

    private int[] orderMoves(Board board, int currentPlayer, boolean[] valid) {
        class MoveScore {
            int move;
            double score;
            MoveScore(int m, double s) { move = m; score = s; }
        }

        List<MoveScore> moves = new ArrayList<>();

        for (int move = 0; move < Board.NB_HOLES; move++) {
            if (!valid[move]) continue;

            double priority = 0.0;
            try {
                int captureScore = board.playMoveSimulationScore(currentPlayer, buildDecision(move));
                if (captureScore > 0) priority += captureScore * 100.0;

                int[] myHoles = getHolesFor(board, currentPlayer);
                priority += myHoles[move] * 2.0;

                Board afterMove = board.playMoveSimulationBoard(currentPlayer, buildDecision(move));
                int[] oppHoles = getHolesFor(afterMove, Board.otherPlayer(currentPlayer));
                for (int s : oppHoles) {
                    if (s == 2 || s == 3) priority += 5.0;
                }
            } catch (InvalidBotException e) {
                continue;
            }

            moves.add(new MoveScore(move, priority));
        }

        moves.sort((a, b) -> Double.compare(b.score, a.score));

        int[] result = new int[Board.NB_HOLES];
        for (int i = 0; i < moves.size(); i++) result[i] = moves.get(i).move;
        for (int i = moves.size(); i < Board.NB_HOLES; i++) result[i] = -1;

        return result;
    }

    // -------------------------------------------------------------------------
    // Évaluation avec 6 poids optimisés
    // -------------------------------------------------------------------------

    private double evaluateWith6Weights(Board board, int currentPlayer) {
        int opponent = Board.otherPlayer(currentPlayer);

        // 0. Score différence
        double scoreDiff = (board.getScore(currentPlayer) - board.getScore(opponent)) * this.bestWeights[0];

        int[] myHoles = getHolesFor(board, currentPlayer);
        int[] oppHoles = getHolesFor(board, opponent);

        // 1. Seeds différence
        double seedsDiff = (sumArray(myHoles) - sumArray(oppHoles)) * this.bestWeights[1];

        // 2. Capture bonus
        double captureBonus = 0.0;
        for (int s : oppHoles) {
            if (s == 2 || s == 3) captureBonus += s * this.bestWeights[2];
        }

        // 3. Mobility
        int myMobility = countTrue(board.validMoves(currentPlayer));
        int oppMobility = countTrue(board.validMoves(opponent));
        double mobilityScore = (myMobility - oppMobility) * this.bestWeights[3];

        // 4. Control center
        double controlCenter = 0.0;
        if (myHoles.length >= 4) {
            controlCenter = (myHoles[2] + myHoles[3]) * this.bestWeights[4];
        }

        // 5. Endgame
        double endgame = (board.getNbSeeds() < 20) ? (scoreDiff * this.bestWeights[5]) : 0.0;

        return scoreDiff + seedsDiff + captureBonus + mobilityScore + controlCenter + endgame;
    }

    private double terminalScore(Board board, int currentPlayer) {
        int opponent = Board.otherPlayer(currentPlayer);
        int myScore = board.getScore(currentPlayer);
        int oppScore = board.getScore(opponent);

        if (myScore > oppScore) return WIN_SCORE + (myScore - oppScore);
        if (myScore < oppScore) return -WIN_SCORE - (oppScore - myScore);
        return 0.0;
    }

    private long computeBoardHash(Board board) {
        long hash = 0L;
        int[] player0 = board.getPlayerHoles();
        int[] player1 = board.getOpponentHoles();

        long[] primes = {2, 3, 5, 7, 11, 13, 17, 19, 23, 29, 31, 37};

        for (int i = 0; i < Board.NB_HOLES; i++) {
            hash ^= player0[i] * primes[i] * 1000000007L;
            hash ^= player1[i] * primes[i + 6] * 1000000009L;
        }

        hash ^= board.getCurrentPlayer() * 1000000021L;
        hash ^= board.getScore(0) * 1000000033L;
        hash ^= board.getScore(1) * 1000000087L;

        return hash;
    }

    // -------------------------------------------------------------------------
    // Utilitaires
    // -------------------------------------------------------------------------

    private static double[] buildDecision(int move) {
        double[] decision = new double[Board.NB_HOLES];
        decision[move] = 1.0;
        return decision;
    }

    private static int[] getHolesFor(Board board, int player) {
        return (player == board.getCurrentPlayer())
            ? board.getPlayerHoles()
            : board.getOpponentHoles();
    }

    private static int sumArray(int[] arr) {
        int s = 0;
        for (int v : arr) s += v;
        return s;
    }

    private static int countTrue(boolean[] arr) {
        int c = 0;
        for (boolean b : arr) if (b) c++;
        return c;
    }

    private static int countTwoThree(int[] arr) {
        int c = 0;
        for (int v : arr) if (v == 2 || v == 3) c++;
        return c;
    }

    private static double sigmoid(double x) {
        if (x > 500.0) return 1.0;
        if (x < -500.0) return 0.0;
        return 1.0 / (1.0 + Math.exp(-x));
    }

    private static String formatWeights(double[] w) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < w.length; i++) {
            sb.append(String.format("%.2f", w[i]));
            if (i < w.length - 1) sb.append(", ");
        }
        sb.append("]");
        return sb.toString();
    }
}