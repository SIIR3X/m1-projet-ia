//package awele.bot.competitor.noname.test;
//
//import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
//import awele.bot.competitor.noname.core.bitboard.BitBoard;
//import awele.bot.competitor.noname.evaluation.PositionEvaluator;
//import awele.bot.competitor.noname.ordering.MoveEvaluator;
//import awele.bot.competitor.noname.search.minmax.NNMaxNode;
//import awele.bot.competitor.noname.search.minmax.NNMinMaxNode;
//import awele.bot.competitor.noname.search.transposition.TwoLevelTranspositionTable;
//
///**
// * Benchmark "match réaliste" :
// * - maxDepth=999 (ou très grand)
// * - arrêt par budget temps
// * - A vs B (config ordering différente) avec alternance du premier joueur
// */
//public final class MatchSearchStatsTest
//{
//    private static final int GAMES = 100;
//    private static final int TIME_BUDGET_MS = 100;
//    private static final int MAX_DEPTH = 999;
//
//    // ouverture optionnelle (si tu veux réduire la variance, garde-le identique à chaque run)
//    private static final int RANDOM_OPENING_PLIES = 0;
//
//    public static void main(String[] args)
//    {
//        // Exemple : A = ordering complet, B = baseline
//        final OrderingConfig configA = new OrderingConfig(true, true, true, true);   // categories/killers/history + catLearning
//        final OrderingConfig configB = new OrderingConfig(false, false, false, false);
//
//        // Important : catégories non entraînées => catLearning = false
//        // Si tu veux tester ordering appris: entraîne avant puis freeze.
//
//        final PositionEvaluator evalA = NNMinMaxNode.positionEvaluator;
//        final PositionEvaluator evalB = NNMinMaxNode.positionEvaluator;
//
//        final MatchSearchStatsTest bench = new MatchSearchStatsTest();
//        MatchReport report = bench.playMatch(evalA, evalB, configA, configB, GAMES);
//
////        System.out.println(report.toPrettyString());
//        System.out.println();
//        System.out.println(report.toCsvSummary());
//    }
//
//    private final TwoLevelTranspositionTable ttA = new TwoLevelTranspositionTable(22, 18, true);
//    private final TwoLevelTranspositionTable ttB = new TwoLevelTranspositionTable(22, 18, true);
//
//    public MatchReport playMatch(
//            PositionEvaluator evalA,
//            PositionEvaluator evalB,
//            OrderingConfig configA,
//            OrderingConfig configB,
//            int games)
//    {
//        MatchReport report = new MatchReport(configA, configB);
//
//        for (int g = 0; g < games; g++)
//        {
//            boolean aStarts = (g % 2 == 0);
//            playSingleGame(evalA, evalB, configA, configB, aStarts, report);
//        }
//
//        report.finalizeStats();
//        return report;
//    }
//
//    private void playSingleGame(
//            PositionEvaluator evalA,
//            PositionEvaluator evalB,
//            OrderingConfig configA,
//            OrderingConfig configB,
//            boolean aStarts,
//            MatchReport report)
//    {
//        BitBoard board = new BitBoard();
//        board.initialize();
//
//        ttA.clear();
//        ttB.clear();
//
//        if (RANDOM_OPENING_PLIES > 0)
//            playRandomOpening(board, RANDOM_OPENING_PLIES);
//
//        // chaque game : on reset killers (sinon pollue d'une position à l'autre)
//        // si tu veux killers "sur toute la partie", commente le clear.
//        // KillerMoves.clear(); // si tu as ajouté cette méthode et si tu l’utilises
//
//        int ply = 0;
//
//        while (!board.isGameOver())
//        {
//            final int currentPlayer = board.getCurrentPlayer();
//            final boolean aTurn =
//                    (aStarts && currentPlayer == 0) ||
//                    (!aStarts && currentPlayer == 1);
//
//            final PositionEvaluator eval = aTurn ? evalA : evalB;
//            final OrderingConfig cfg = aTurn ? configA : configB;
//            final TwoLevelTranspositionTable tt = aTurn ? ttA : ttB;
//
//            DecisionStats ds = decideWithBudget(board, eval, tt, cfg, TIME_BUDGET_MS, MAX_DEPTH);
//
//            board.playMove(ds.decision);
//
//            report.onMove(aTurn ? "A" : "B", ply, ds.nodes, ds.interrupted);
//
//            ply++;
//            if (ply > 300) break; // garde-fou
//        }
//
//        // résultat
//        final int aPlayerId = aStarts ? 0 : 1;
//        final int bPlayerId = 1 - aPlayerId;
//
//        report.onGameEnd(board.getScore(aPlayerId), board.getScore(bPlayerId), board.getWinner(), aPlayerId, bPlayerId);
//    }
//
//    private DecisionStats decideWithBudget(
//            BitBoard board,
//            PositionEvaluator eval,
//            TwoLevelTranspositionTable tt,
//            OrderingConfig cfg,
//            int timeMs,
//            int maxDepth)
//    {
//        // Toggle ordering pour CE coup
//        MoveEvaluator.ENABLE_CATEGORY_ORDERING = cfg.categories;
//        MoveEvaluator.ENABLE_KILLERS = cfg.killers;
//
//        // Learning catégorie ON/OFF (si tu l’utilises)
//        // CategoryMoveOrdering.LEARNING_ENABLED = cfg.categoryLearning;
//
//        NNMinMaxNode.transpositionTable = tt;
//        NNMinMaxNode.positionEvaluator = eval;
//
//        // budget temps
//        NNMinMaxNode.timeExpired = false;
//        NNMinMaxNode.searchStartTime = System.nanoTime();
//        NNMinMaxNode.maxSearchTime = timeMs * 1_000_000L;
//
//        NNMinMaxNode.nodeCount = 0;
//
//        NNMinMaxNode.initialize(board, maxDepth);
//
//        NNMaxNode root = new NNMaxNode(board);
//
//        final boolean interrupted = root.isInterrupted() || NNMinMaxNode.timeExpired;
//        final double[] decision = interrupted ? fallbackDecision(board) : root.getDecision();
//
//        return new DecisionStats(decision, NNMinMaxNode.nodeCount, interrupted);
//    }
//
//    private static void playRandomOpening(BitBoard board, int plies)
//    {
//        for (int p = 0; p < plies && !board.isGameOver(); p++)
//        {
//            int player = board.getCurrentPlayer();
//            boolean[] valid = board.getValidMoves(player);
//
//            int chosen = -1;
//            for (int i = 0; i < NB_HOLES; i++)
//                if (valid[i]) { chosen = i; break; }
//
//            if (chosen < 0) return;
//
//            double[] dec = new double[NB_HOLES];
//            dec[chosen] = 1.0;
//            board.playMove(dec);
//        }
//    }
//
//    private static double[] fallbackDecision(BitBoard board)
//    {
//        int player = board.getCurrentPlayer();
//        boolean[] valid = board.getValidMoves(player);
//        double[] decision = new double[NB_HOLES];
//
//        for (int i = 0; i < NB_HOLES; i++)
//            if (valid[i]) { decision[i] = 1.0; return decision; }
//
//        return decision;
//    }
//
//    // ===== DTO =====
//    private static final class DecisionStats
//    {
//        final double[] decision;
//        final long nodes;
//        final boolean interrupted;
//
//        DecisionStats(double[] decision, long nodes, boolean interrupted)
//        {
//            this.decision = decision;
//            this.nodes = nodes;
//            this.interrupted = interrupted;
//        }
//    }
//
//    public static final class OrderingConfig
//    {
//        public final boolean categories;
//        public final boolean killers;
//        public final boolean history;
//        public final boolean categoryLearning;
//
//        public OrderingConfig(boolean categories, boolean killers, boolean history, boolean categoryLearning)
//        {
//            this.categories = categories;
//            this.killers = killers;
//            this.history = history;
//            this.categoryLearning = categoryLearning;
//        }
//    }
//
//    // ===== Stats =====
//    public static final class MatchReport
//    {
//        private final OrderingConfig aCfg;
//        private final OrderingConfig bCfg;
//
//        private int games = 0;
//        private int winsA = 0;
//        private int winsB = 0;
//        private int draws = 0;
//
//        private int scoreSumA = 0;
//        private int scoreSumB = 0;
//
//        private int interruptedA = 0;
//        private int interruptedB = 0;
//        private int totalMovesA = 0;
//        private int totalMovesB = 0;
//
//        private final List<Long> nodesA = new ArrayList<>();
//        private final List<Long> nodesB = new ArrayList<>();
//
//        public MatchReport(OrderingConfig aCfg, OrderingConfig bCfg)
//        {
//            this.aCfg = aCfg;
//            this.bCfg = bCfg;
//        }
//
//        public void onMove(String who, int ply, long nodes, boolean interrupted)
//        {
//            if ("A".equals(who))
//            {
//                nodesA.add(nodes);
//                totalMovesA++;
//                if (interrupted) interruptedA++;
//            }
//            else
//            {
//                nodesB.add(nodes);
//                totalMovesB++;
//                if (interrupted) interruptedB++;
//            }
//        }
//
//        public String toCsvLine(String configName)
//        {
//            // side,count,mean,p50,p90,p99,min,max,interruptedRate
//            final CsvNodes a = CsvNodes.of(nodesA, interruptedA, totalMovesA);
//            final CsvNodes b = CsvNodes.of(nodesB, interruptedB, totalMovesB);
//
//            final double avgScoreA = games == 0 ? 0.0 : (double) scoreSumA / games;
//            final double avgScoreB = games == 0 ? 0.0 : (double) scoreSumB / games;
//
//            return configName + ","
//                    + aCfg.categories + "," + aCfg.killers + "," + aCfg.history + ","
//                    + games + ","
//                    + winsA + "," + winsB + "," + draws + ","
//                    + String.format(java.util.Locale.US, "%.2f", avgScoreA) + ","
//                    + String.format(java.util.Locale.US, "%.2f", avgScoreB) + ","
//                    + String.format(java.util.Locale.US, "%.2f", a.mean) + "," + a.p90 + "," + a.p99 + ","
//                    + String.format(java.util.Locale.US, "%.2f", a.interruptRate) + ","
//                    + String.format(java.util.Locale.US, "%.2f", b.mean) + "," + b.p90 + "," + b.p99 + ","
//                    + String.format(java.util.Locale.US, "%.2f", b.interruptRate);
//        }
//
//        private static final class CsvNodes
//        {
//            final double mean;
//            final long p90;
//            final long p99;
//            final double interruptRate;
//
//            private CsvNodes(double mean, long p90, long p99, double interruptRate)
//            {
//                this.mean = mean;
//                this.p90 = p90;
//                this.p99 = p99;
//                this.interruptRate = interruptRate;
//            }
//
//            static CsvNodes of(java.util.List<Long> xs, int interrupted, int totalMoves)
//            {
//                if (xs.isEmpty())
//                    return new CsvNodes(0.0, 0, 0, 0.0);
//
//                // xs est déjà triée dans finalizeStats()
//                double mean = mean(xs);
//                long p90 = percentile(xs, 90);
//                long p99 = percentile(xs, 99);
//                double rate = totalMoves == 0 ? 0.0 : 100.0 * ((double) interrupted / (double) totalMoves);
//
//                return new CsvNodes(mean, p90, p99, rate);
//            }
//        }
//        
//        public void onGameEnd(int scoreA, int scoreB, int winner, int aPlayerId, int bPlayerId)
//        {
//            games++;
//            scoreSumA += scoreA;
//            scoreSumB += scoreB;
//
//            if (winner == -1) draws++;
//            else if (winner == aPlayerId) winsA++;
//            else if (winner == bPlayerId) winsB++;
//        }
//
//        public void finalizeStats()
//        {
//            Collections.sort(nodesA);
//            Collections.sort(nodesB);
//        }
//
//        public String toPrettyString()
//        {
//            StringBuilder sb = new StringBuilder();
//            sb.append("Games: ").append(games).append('\n');
//            sb.append("Config A: categories=").append(aCfg.categories)
//              .append(", killers=").append(aCfg.killers)
//              .append(", history=").append(aCfg.history)
//              .append(", catLearning=").append(aCfg.categoryLearning).append('\n');
//            sb.append("Config B: categories=").append(bCfg.categories)
//              .append(", killers=").append(bCfg.killers)
//              .append(", history=").append(bCfg.history)
//              .append(", catLearning=").append(bCfg.categoryLearning).append("\n\n");
//
//            sb.append("Results: winsA=").append(winsA)
//              .append(" winsB=").append(winsB)
//              .append(" draws=").append(draws).append('\n');
//            sb.append("Score sums: A=").append(scoreSumA).append(" B=").append(scoreSumB).append("\n\n");
//
//            sb.append("A moves: ").append(totalMovesA)
//              .append(" interrupted=").append(interruptedA)
//              .append(" (").append(percent(interruptedA, totalMovesA)).append("%)\n");
//            sb.append(formatNodes("A nodes", nodesA)).append('\n');
//
//            sb.append("B moves: ").append(totalMovesB)
//              .append(" interrupted=").append(interruptedB)
//              .append(" (").append(percent(interruptedB, totalMovesB)).append("%)\n");
//            sb.append(formatNodes("B nodes", nodesB)).append('\n');
//
//            return sb.toString();
//        }
//
//        public String toCsvSummary()
//        {
//            return "side,count,mean,p50,p90,p99,min,max,interruptedRate\n"
//                    + csvLine("A", nodesA, interruptedA, totalMovesA) + "\n"
//                    + csvLine("B", nodesB, interruptedB, totalMovesB) + "\n";
//        }
//
//        private static String csvLine(String side, List<Long> xs, int interrupted, int totalMoves)
//        {
//            if (xs.isEmpty()) return side + ",0,0,0,0,0,0,0,0";
//
//            double mean = mean(xs);
//            long p50 = percentile(xs, 50);
//            long p90 = percentile(xs, 90);
//            long p99 = percentile(xs, 99);
//            long min = xs.get(0);
//            long max = xs.get(xs.size() - 1);
//
//            return side + ","
//                    + xs.size() + ","
//                    + String.format(java.util.Locale.US, "%.2f", mean) + ","
//                    + p50 + "," + p90 + "," + p99 + ","
//                    + min + "," + max + ","
//                    + String.format(java.util.Locale.US, "%.2f", percent(interrupted, totalMoves));
//        }
//
//        private static String formatNodes(String label, List<Long> xs)
//        {
//            if (xs.isEmpty()) return label + ": (no data)";
//
//            double mean = mean(xs);
//            long p50 = percentile(xs, 50);
//            long p90 = percentile(xs, 90);
//            long p99 = percentile(xs, 99);
//            long min = xs.get(0);
//            long max = xs.get(xs.size() - 1);
//
//            return label + ": count=" + xs.size()
//                    + " mean=" + String.format(java.util.Locale.US, "%.2f", mean)
//                    + " p50=" + p50 + " p90=" + p90 + " p99=" + p99
//                    + " min=" + min + " max=" + max;
//        }
//
//        private static double mean(List<Long> xs)
//        {
//            long sum = 0;
//            for (long v : xs) sum += v;
//            return (double) sum / (double) xs.size();
//        }
//
//        private static long percentile(List<Long> xsSorted, int p)
//        {
//            int n = xsSorted.size();
//            int idx = (int) Math.ceil((p / 100.0) * n) - 1;
//            idx = Math.max(0, Math.min(n - 1, idx));
//            return xsSorted.get(idx);
//        }
//
//        private static double percent(int a, int b)
//        {
//            if (b == 0) return 0.0;
//            return 100.0 * ((double) a / (double) b);
//        }
//    }
//}