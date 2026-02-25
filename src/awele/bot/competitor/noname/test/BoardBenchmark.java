//package awele.bot.competitor.noname.test;
//
//import java.io.FileWriter;
//import java.io.PrintWriter;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.Locale;
//import java.util.Random;
//
//import awele.bot.competitor.noname.core.bitboard.BitBoard;
//import awele.core.Board;
//import awele.core.InvalidBotException;
//
///**
// * Benchmark minimal (sans paliers, sans multithreading).
// *
// * Mesure uniquement :
// *   - temps moyen de clone() (ns/op)
// *   - temps moyen de playMove() (ns/op) sur un clone (clone NON inclus dans le temps playMove)
// *
// * Sortie :
// *   logs/boards/run_<timestamp>/summary.csv
// *
// * Usage :
// *   java BoardBenchmark
// *     -> nb_ops par défaut
// *
// *   java BoardBenchmark <nb_ops>
// *     -> ex: java BoardBenchmark 10000000
// */
//public final class BoardBenchmark
//{
//    private static final int DEFAULT_NB_OPS = 100_000_000;
//
//    // Warmup JIT (petit, pour éviter les effets de compilation)
//    private static final int WARMUP_ITERS = 50_000;
//
//    // Seed fixe pour reproductibilité
//    private static final long RNG_SEED = 0xCAFE_BABEL;
//
//    private static final String SUMMARY_HEADER =
//        "board_type,nb_ops,clone_avg_ns,playmove_avg_ns";
//
//    public static void main(String[] args) throws Exception
//    {
//        final int nbOps = parseNbOps(args);
//
//        final String timestamp = LocalDateTime.now()
//            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
//        final String runDir = "logs/boards/run_" + timestamp + "/";
//        Files.createDirectories(Paths.get(runDir));
//
//        final String outFile = runDir + "summary.csv";
//
//        System.out.println("=== BoardBenchmark (simple) ===");
//        System.out.println("Run dir : " + runDir);
//        System.out.printf("nb_ops  : %,d%n%n", nbOps);
//
//        System.out.println("Warmup JIT...");
//        warmup();
//        System.out.println("Warmup terminé.\n");
//
//        final BenchResult classic = benchClassic(nbOps);
//        final BenchResult bitboard = benchBitboard(nbOps);
//
//        printResult("classique", classic);
//        printResult("bitboard", bitboard);
//
//        try (PrintWriter pw = new PrintWriter(new FileWriter(outFile, false)))
//        {
//            pw.println(SUMMARY_HEADER);
//            writeSummaryLine(pw, "classique", nbOps, classic);
//            writeSummaryLine(pw, "bitboard", nbOps, bitboard);
//            pw.flush();
//        }
//
//        System.out.println("\nWrote: " + outFile);
//    }
//
//    // =========================================================================
//    // Bench classic
//    // =========================================================================
//
//    private static BenchResult benchClassic(int nbOps)
//    {
//        final Random rng = new Random(RNG_SEED);
//        final Board reference = buildMidGameClassic(new Random(RNG_SEED));
//        final double[] decision = randomDecision(reference.validMoves(reference.getCurrentPlayer()), rng);
//
//        // --- clone ---
//        final long cloneStart = System.nanoTime();
//        for (int i = 0; i < nbOps; i++)
//        {
//            final Object c = reference.clone();
//            blackhole(c);
//        }
//        final long cloneTotal = System.nanoTime() - cloneStart;
//
//        // --- playMove (clone non compté) ---
//        long playTotal = 0L;
//        for (int i = 0; i < nbOps; i++)
//        {
//            final Board copy = (Board) reference.clone(); // non mesuré
//
//            final long pmStart = System.nanoTime();
//            try { copy.playMove(copy.getCurrentPlayer(), decision); }
//            catch (InvalidBotException ignored) {}
//            playTotal += (System.nanoTime() - pmStart);
//
//            blackhole(copy);
//        }
//
//        return new BenchResult(nbOps, cloneTotal, playTotal);
//    }
//
//    // =========================================================================
//    // Bench bitboard
//    // =========================================================================
//
//    private static BenchResult benchBitboard(int nbOps)
//    {
//        final Random rng = new Random(RNG_SEED);
//        final BitBoard reference = buildMidGameBitBoard(new Random(RNG_SEED));
//        final int player = reference.getCurrentPlayer();
//        final double[] decision = randomDecision(reference.getValidMoves(player), rng);
//
//        // --- clone ---
//        final long cloneStart = System.nanoTime();
//        for (int i = 0; i < nbOps; i++)
//        {
//            final BitBoard c = reference.clone();
//            blackhole(c);
//        }
//        final long cloneTotal = System.nanoTime() - cloneStart;
//
//        // --- playMove (clone non compté) ---
//        long playTotal = 0L;
//        for (int i = 0; i < nbOps; i++)
//        {
//            final BitBoard copy = reference.clone(); // non mesuré
//
//            final long pmStart = System.nanoTime();
//            copy.playMove(decision);
//            playTotal += (System.nanoTime() - pmStart);
//
//            blackhole(copy);
//        }
//
//        return new BenchResult(nbOps, cloneTotal, playTotal);
//    }
//
//    // =========================================================================
//    // Result
//    // =========================================================================
//
//    private static final class BenchResult
//    {
//        final int nbOps;
//        final long cloneTotalNs;
//        final long playTotalNs;
//
//        final double cloneAvgNs;
//        final double playAvgNs;
//
//        BenchResult(int nbOps, long cloneTotalNs, long playTotalNs)
//        {
//            this.nbOps = nbOps;
//            this.cloneTotalNs = cloneTotalNs;
//            this.playTotalNs = playTotalNs;
//
//            this.cloneAvgNs = (double) cloneTotalNs / nbOps;
//            this.playAvgNs = (double) playTotalNs / nbOps;
//        }
//    }
//
//    // =========================================================================
//    // Mid-game states
//    // =========================================================================
//
//    private static Board buildMidGameClassic(Random rng)
//    {
//        final Board board = new Board();
//        board.setCurrentPlayer(0);
//        final int moves = 8 + rng.nextInt(13); // 8..20
//
//        for (int m = 0; m < moves; m++)
//        {
//            final int player = board.getCurrentPlayer();
//            final boolean[] valid = board.validMoves(player);
//            final double[] decision = randomDecision(valid, rng);
//
//            try { board.playMove(player, decision); }
//            catch (InvalidBotException ignored) { break; }
//
//            if (board.getNbSeeds() <= 6) break;
//        }
//        return board;
//    }
//
//    private static BitBoard buildMidGameBitBoard(Random rng)
//    {
//        final BitBoard board = new BitBoard();
//        final int moves = 8 + rng.nextInt(13); // 8..20
//
//        for (int m = 0; m < moves; m++)
//        {
//            if (board.isGameOver()) break;
//            final int player = board.getCurrentPlayer();
//            final boolean[] valid = board.getValidMoves(player);
//            final double[] decision = randomDecision(valid, rng);
//            board.playMove(decision);
//        }
//        return board;
//    }
//
//    // =========================================================================
//    // RNG decision
//    // =========================================================================
//
//    private static double[] randomDecision(boolean[] valid, Random rng)
//    {
//        final double[] decision = new double[Board.NB_HOLES];
//
//        int count = 0;
//        for (boolean v : valid) if (v) count++;
//        if (count == 0) return decision;
//
//        int pick = rng.nextInt(count);
//        for (int i = 0; i < valid.length; i++)
//        {
//            if (!valid[i]) continue;
//            if (pick-- == 0)
//            {
//                decision[i] = 1.0;
//                return decision;
//            }
//        }
//        return decision;
//    }
//
//    // =========================================================================
//    // Warmup
//    // =========================================================================
//
//    private static void warmup()
//    {
//        final Random rng = new Random(42);
//
//        final Board cb = buildMidGameClassic(new Random(42));
//        final BitBoard bb = buildMidGameBitBoard(new Random(42));
//
//        final double[] d1 = randomDecision(cb.validMoves(cb.getCurrentPlayer()), rng);
//        final double[] d2 = randomDecision(bb.getValidMoves(bb.getCurrentPlayer()), rng);
//
//        for (int i = 0; i < WARMUP_ITERS; i++)
//        {
//            blackhole(cb.clone());
//            blackhole(bb.clone());
//
//            try
//            {
//                final Board copy = (Board) cb.clone();
//                copy.playMove(copy.getCurrentPlayer(), d1);
//                blackhole(copy);
//            }
//            catch (InvalidBotException ignored) {}
//
//            final BitBoard copy2 = bb.clone();
//            copy2.playMove(d2);
//            blackhole(copy2);
//        }
//    }
//
//    // =========================================================================
//    // Output
//    // =========================================================================
//
//    private static void printResult(String name, BenchResult r)
//    {
//        System.out.printf(
//            "%-10s | clone: %8.2f ns/op | playMove: %8.2f ns/op%n",
//            name, r.cloneAvgNs, r.playAvgNs
//        );
//    }
//
//    private static void writeSummaryLine(PrintWriter pw, String boardType, int nbOps, BenchResult r)
//    {
//        pw.printf(Locale.US, "%s,%d,%.4f,%.4f%n", boardType, nbOps, r.cloneAvgNs, r.playAvgNs);
//    }
//
//    private static int parseNbOps(String[] args)
//    {
//        if (args == null || args.length == 0)
//            return DEFAULT_NB_OPS;
//
//        try
//        {
//            final String s = args[0].replace("_", "").replace(",", "");
//            final long n = Long.parseLong(s);
//            if (n <= 0 || n > Integer.MAX_VALUE)
//                return DEFAULT_NB_OPS;
//            return (int) n;
//        }
//        catch (Exception ignored)
//        {
//            return DEFAULT_NB_OPS;
//        }
//    }
//
//    // =========================================================================
//    // Blackhole (anti JIT DCE)
//    // =========================================================================
//
//    private static volatile Object sink;
//    private static void blackhole(Object o) { sink = o; }
//
//    private BoardBenchmark() {}
//}