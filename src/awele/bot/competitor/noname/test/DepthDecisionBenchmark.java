//package awele.bot.competitor.noname.test;
//
//import java.io.FileWriter;
//import java.io.PrintWriter;
//import java.nio.file.Files;
//import java.nio.file.Paths;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.ArrayList;
//import java.util.Arrays;
//import java.util.List;
//import java.util.Locale;
//import java.util.Random;
//
//import awele.bot.competitor.noname.core.bitboard.BitBoard;
//import awele.bot.competitor.noname.search.minmax.BitMaxNode;
//import awele.bot.competitor.noname.search.minmax.BitMinMaxNode;
//import awele.bot.competitor.noname.search.transposition.TranspositionTable;
//import awele.bot.demo.minmax.MaxNode;
//import awele.bot.demo.minmax.MinMaxNode;
//import awele.core.Board;
//import awele.core.InvalidBotException;
//
///**
// * Mesure le temps "choisir un coup" pour :
// *  - bitboard : depth 1..BIT_MAX_DEPTH
// *  - classic  : depth 1..CLASSIC_MAX_DEPTH (cap pour éviter les runs très longs)
// *
// * Sorties :
// *  - logs/bench_depth/run_<timestamp>/raw.csv
// *  - logs/bench_depth/run_<timestamp>/summary.csv
// */
//public final class DepthDecisionBenchmark
//{
//    // Plage de profondeurs
//    private static final int BIT_MAX_DEPTH = 18;
//    private static final int CLASSIC_MAX_DEPTH = 8;
//
//    // Defaults
//    private static final int DEFAULT_POSITIONS = 30;
//    private static final int DEFAULT_MOVES_PER_POSITION = 16;
//    private static final int DEFAULT_REPEATS = 3;
//
//    private static final long SEED_POSITIONS = 0x1234_5678_ABCD_EF01L;
//    private static final long SEED_MOVES     = 0xCAFE_BABE_0DDC_0FFEL;
//
//    private static final int WARMUP_ITERS = 500; // réduit pour ne pas être trop long
//
//    public static void main(String[] args) throws Exception
//    {
//        final int nbPositions = (args.length >= 1) ? parsePosInt(args[0], DEFAULT_POSITIONS) : DEFAULT_POSITIONS;
//        final int movesPerPos = (args.length >= 2) ? parsePosInt(args[1], DEFAULT_MOVES_PER_POSITION) : DEFAULT_MOVES_PER_POSITION;
//        final int repeats     = (args.length >= 3) ? parsePosInt(args[2], DEFAULT_REPEATS) : DEFAULT_REPEATS;
//
//        final String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
//        final String runDir = "logs/bench_depth/run_" + timestamp + "/";
//        Files.createDirectories(Paths.get(runDir));
//
//        final String rawCsv = runDir + "raw.csv";
//        final String summaryCsv = runDir + "summary.csv";
//
//        System.out.println("=== DepthDecisionBenchmark ===");
//        System.out.println("Run dir: " + runDir);
//        System.out.printf(Locale.US, "positions=%d, movesPerPos=%d, repeats=%d%n", nbPositions, movesPerPos, repeats);
//        System.out.printf(Locale.US, "depth(bitboard)=1..%d, depth(classic)=1..%d%n%n",
//            BIT_MAX_DEPTH, CLASSIC_MAX_DEPTH);
//
//        // mêmes positions via séquences de coups
//        final List<int[]> moveSequences = generateMoveSequences(nbPositions, movesPerPos);
//
//        System.out.println("Warmup JIT...");
//        warmup(moveSequences.get(0));
//        System.out.println("Warmup done.\n");
//
//        final List<Measure> allMeasures = new ArrayList<>();
//
//        try (PrintWriter rawOut = new PrintWriter(new FileWriter(rawCsv, false)))
//        {
//            rawOut.println("algo,depth,position_id,repeat,elapsed_ns,elapsed_ms");
//
//            // --- Classic depths 1..8 seulement ---
//            for (int depth = 1; depth <= CLASSIC_MAX_DEPTH; depth++)
//            {
//                System.out.printf("── Classic depth %d%n", depth);
//                for (int posId = 0; posId < moveSequences.size(); posId++)
//                {
//                    final int[] seq = moveSequences.get(posId);
//                    for (int r = 0; r < repeats; r++)
//                    {
//                        final long t = timeDecisionClassic(seq, depth);
//                        allMeasures.add(new Measure("classic", depth, posId, r, t));
//                        rawOut.printf(Locale.US, "classic,%d,%d,%d,%d,%.3f%n", depth, posId, r, t, t / 1_000_000.0);
//                    }
//                }
//                rawOut.flush();
//                System.out.println();
//            }
//
//            // --- BitBoard depths 1..12 ---
//            for (int depth = 1; depth <= BIT_MAX_DEPTH; depth++)
//            {
//                System.out.printf("── BitBoard depth %d%n", depth);
//                for (int posId = 0; posId < moveSequences.size(); posId++)
//                {
//                    final int[] seq = moveSequences.get(posId);
//                    for (int r = 0; r < repeats; r++)
//                    {
//                        final long t = timeDecisionBit(seq, depth);
//                        allMeasures.add(new Measure("bitboard", depth, posId, r, t));
//                        rawOut.printf(Locale.US, "bitboard,%d,%d,%d,%d,%.3f%n", depth, posId, r, t, t / 1_000_000.0);
//                    }
//                }
//                rawOut.flush();
//                System.out.println();
//            }
//        }
//
//        // Summary (classic 1..8, bitboard 1..12)
//        try (PrintWriter sumOut = new PrintWriter(new FileWriter(summaryCsv, false)))
//        {
//            sumOut.println("algo,depth,n,avg_ms,median_ms,p95_ms,avg_ns");
//
//            for (int depth = 1; depth <= CLASSIC_MAX_DEPTH; depth++)
//            {
//                final List<Long> ns = extractNs(allMeasures, "classic", depth);
//                final Stats st = stats(ns);
//                sumOut.printf(Locale.US,
//                    "classic,%d,%d,%.3f,%.3f,%.3f,%.1f%n",
//                    depth, ns.size(),
//                    st.avgNs / 1_000_000.0,
//                    st.medianNs / 1_000_000.0,
//                    st.p95Ns / 1_000_000.0,
//                    st.avgNs
//                );
//            }
//
//            for (int depth = 1; depth <= BIT_MAX_DEPTH; depth++)
//            {
//                final List<Long> ns = extractNs(allMeasures, "bitboard", depth);
//                final Stats st = stats(ns);
//                sumOut.printf(Locale.US,
//                    "bitboard,%d,%d,%.3f,%.3f,%.3f,%.1f%n",
//                    depth, ns.size(),
//                    st.avgNs / 1_000_000.0,
//                    st.medianNs / 1_000_000.0,
//                    st.p95Ns / 1_000_000.0,
//                    st.avgNs
//                );
//            }
//        }
//
//        System.out.println("Wrote: " + rawCsv);
//        System.out.println("Wrote: " + summaryCsv);
//    }
//
//    // =========================================================================
//    // Mesure "temps pour choisir un coup"
//    // =========================================================================
//
//    private static long timeDecisionClassic(int[] moveSeq, int depth)
//    {
//        final Board board = replayOnClassic(moveSeq);
//
//        final long start = System.nanoTime();
//        MinMaxNode.initialize(board, depth);
//        final double[] decision = new MaxNode(board).getDecision();
//        blackhole(decision);
//        return System.nanoTime() - start;
//    }
//
//    private static long timeDecisionBit(int[] moveSeq, int depth)
//    {
//        final BitBoard board = replayOnBit(moveSeq);
//
//        // reset état global BitMinMax entre mesures
//        BitMinMaxNode.transpositionTable = new TranspositionTable();
//        BitMinMaxNode.nodeCount = 0;
//        BitMinMaxNode.resetTimer(); // pas de timeout : on mesure un temps complet
//
//        final long start = System.nanoTime();
//        BitMinMaxNode.initialize(board, depth);
//        final double[] decision = new BitMaxNode(board).getDecision();
//        blackhole(decision);
//        return System.nanoTime() - start;
//    }
//
//    // =========================================================================
//    // Génération de positions identiques via séquences de coups
//    // =========================================================================
//
//    private static List<int[]> generateMoveSequences(int nbPositions, int movesPerPos)
//    {
//        final Random rngPos = new Random(SEED_POSITIONS);
//        final Random rngMove = new Random(SEED_MOVES);
//
//        final List<int[]> seqs = new ArrayList<>(nbPositions);
//
//        for (int p = 0; p < nbPositions; p++)
//        {
//            final Board b = new Board();
//            b.setCurrentPlayer(0);
//
//            final int[] seq = new int[movesPerPos];
//            int len = 0;
//
//            for (int m = 0; m < movesPerPos; m++)
//            {
//                final int player = b.getCurrentPlayer();
//                final boolean[] valid = b.validMoves(player);
//
//                final int moveIndex = pickValidMoveIndex(valid, rngMove);
//                if (moveIndex < 0) break;
//
//                seq[len++] = moveIndex;
//
//                final double[] decision = new double[Board.NB_HOLES];
//                decision[moveIndex] = 1.0;
//
//                try { b.playMove(player, decision); }
//                catch (InvalidBotException e) { break; }
//
//                if (b.getNbSeeds() <= 6) break;
//            }
//
//            seqs.add(Arrays.copyOf(seq, Math.max(1, len)));
//            rngPos.nextInt();
//        }
//
//        return seqs;
//    }
//
//    private static Board replayOnClassic(int[] seq)
//    {
//        final Board b = new Board();
//        b.setCurrentPlayer(0);
//
//        for (int mv : seq)
//        {
//            final int player = b.getCurrentPlayer();
//            final double[] decision = new double[Board.NB_HOLES];
//            if (mv >= 0 && mv < decision.length) decision[mv] = 1.0;
//
//            try { b.playMove(player, decision); }
//            catch (InvalidBotException e) { break; }
//
//            if (b.getNbSeeds() <= 6) break;
//        }
//        return b;
//    }
//
//    private static BitBoard replayOnBit(int[] seq)
//    {
//        final BitBoard b = new BitBoard();
//
//        for (int mv : seq)
//        {
//            if (b.isGameOver()) break;
//
//            final double[] decision = new double[Board.NB_HOLES];
//            if (mv >= 0 && mv < decision.length) decision[mv] = 1.0;
//
//            b.playMove(decision);
//        }
//        return b;
//    }
//
//    private static int pickValidMoveIndex(boolean[] valid, Random rng)
//    {
//        int count = 0;
//        for (boolean v : valid) if (v) count++;
//        if (count == 0) return -1;
//
//        int pick = rng.nextInt(count);
//        for (int i = 0; i < valid.length; i++)
//        {
//            if (!valid[i]) continue;
//            if (pick-- == 0) return i;
//        }
//        return -1;
//    }
//
//    // =========================================================================
//    // Warmup
//    // =========================================================================
//
//    private static void warmup(int[] seq)
//    {
//        // chauffe sur depth 6 (assez représentatif)
//        for (int i = 0; i < WARMUP_ITERS; i++)
//        {
//            blackhole(timeDecisionClassic(seq, Math.min(6, CLASSIC_MAX_DEPTH)));
//            blackhole(timeDecisionBit(seq, Math.min(6, BIT_MAX_DEPTH)));
//        }
//    }
//
//    // =========================================================================
//    // Stats / utils
//    // =========================================================================
//
//    private static final class Measure
//    {
//        final String algo;
//        final int depth;
//        final int positionId;
//        final int repeat;
//        final long elapsedNs;
//
//        Measure(String algo, int depth, int positionId, int repeat, long elapsedNs)
//        {
//            this.algo = algo;
//            this.depth = depth;
//            this.positionId = positionId;
//            this.repeat = repeat;
//            this.elapsedNs = elapsedNs;
//        }
//    }
//
//    private static List<Long> extractNs(List<Measure> measures, String algo, int depth)
//    {
//        final List<Long> out = new ArrayList<>();
//        for (Measure m : measures)
//            if (m.depth == depth && m.algo.equals(algo))
//                out.add(m.elapsedNs);
//        return out;
//    }
//
//    private static final class Stats
//    {
//        final double avgNs;
//        final double medianNs;
//        final double p95Ns;
//
//        Stats(double avgNs, double medianNs, double p95Ns)
//        {
//            this.avgNs = avgNs;
//            this.medianNs = medianNs;
//            this.p95Ns = p95Ns;
//        }
//    }
//
//    private static Stats stats(List<Long> ns)
//    {
//        if (ns.isEmpty()) return new Stats(0, 0, 0);
//
//        long sum = 0;
//        for (long x : ns) sum += x;
//
//        final long[] a = new long[ns.size()];
//        for (int i = 0; i < ns.size(); i++) a[i] = ns.get(i);
//        Arrays.sort(a);
//
//        final double avg = (double) sum / a.length;
//        final double median = percentileSorted(a, 50);
//        final double p95 = percentileSorted(a, 95);
//
//        return new Stats(avg, median, p95);
//    }
//
//    private static double percentileSorted(long[] sorted, int p)
//    {
//        if (sorted.length == 0) return 0;
//        if (p <= 0) return sorted[0];
//        if (p >= 100) return sorted[sorted.length - 1];
//
//        final double rank = (p / 100.0) * (sorted.length - 1);
//        final int lo = (int) Math.floor(rank);
//        final int hi = (int) Math.ceil(rank);
//        if (lo == hi) return sorted[lo];
//
//        final double w = rank - lo;
//        return sorted[lo] * (1.0 - w) + sorted[hi] * w;
//    }
//
//    private static int parsePosInt(String s, int def)
//    {
//        try
//        {
//            final int v = Integer.parseInt(s.replace("_", "").replace(",", ""));
//            return (v > 0) ? v : def;
//        }
//        catch (Exception e)
//        {
//            return def;
//        }
//    }
//
//    private static volatile Object sink;
//    private static void blackhole(Object o) { sink = o; }
//
//    private DepthDecisionBenchmark() {}
//}