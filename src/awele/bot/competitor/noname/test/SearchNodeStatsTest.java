//package awele.bot.competitor.noname.test;
//
//import static awele.bot.competitor.noname.core.bitboard.BitConstants.NB_HOLES;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//
//import awele.bot.competitor.noname.NoNameBot;
//import awele.bot.competitor.noname.core.bitboard.BitBoard;
//import awele.bot.competitor.noname.evaluation.PositionEvaluator;
//import awele.bot.competitor.noname.ordering.MoveEvaluator;
//import awele.bot.competitor.noname.search.minmax.NNMaxNode;
//import awele.bot.competitor.noname.search.minmax.NNMinMaxNode;
//import awele.bot.competitor.noname.search.transposition.TwoLevelTranspositionTable;
//import awele.core.InvalidBotException;
//
///**
// * Test/benchmark: joue des parties et mesure le nombre de nœuds visités par décision.
// * Permet de comparer avec/ sans history, killers, catégories, etc.
// */
//public final class SearchNodeStatsTest
//{
//    private static final int MAX_MOVES_PER_GAME = 200;
//    private static final int RANDOM_OPENING_PLIES = 6;
//
//    // Ajuste ici
//    private static final int NB_GAMES = 100;
//    private static final int DEPTH = 100;
//
//    // Self-play (mêmes eval/depth)
//    private static final boolean SELF_PLAY = true;
//
//    private final TwoLevelTranspositionTable ttA = new TwoLevelTranspositionTable(22, 18, true);
//    private final TwoLevelTranspositionTable ttB = new TwoLevelTranspositionTable(22, 18, true);
//
//    private final BitBoard startBoard = new BitBoard();
//
//    public static void main(String[] args)
//    {
//    		try {
//				NoNameBot bot = new NoNameBot();
//				bot.learn();
//			} catch (InvalidBotException e) {
//				// TODO Auto-generated catch block
//				e.printStackTrace();
//			}
//    		
//    	
//        // Toggle des heuristiques d'ordering (voir modifs MoveEvaluator plus bas)
//        MoveEvaluator.ENABLE_CATEGORY_ORDERING = true;
//        MoveEvaluator.ENABLE_KILLERS = false;
//
//        // Exemple: comparer rapidement
//        // MoveEvaluator.ENABLE_HISTORY = false;
//
//        final SearchNodeStatsTest bench = new SearchNodeStatsTest();
//
//        final PositionEvaluator evalA = NNMinMaxNode.positionEvaluator; // ou new PositionEvaluator(weights)
//        final PositionEvaluator evalB = SELF_PLAY ? evalA : evalA;
//
//        final StatsReport report = bench.run(evalA, evalB, DEPTH, DEPTH, NB_GAMES);
//
//        System.out.println(report.toPrettyString());
//        System.out.println();
//        System.out.println(report.toCsvSummary());
//    }
//
//    public StatsReport run(PositionEvaluator evalA, PositionEvaluator evalB, int depthA, int depthB, int nbGames)
//    {
//        final StatsReport report = new StatsReport();
//
//        for (int g = 0; g < nbGames; g++)
//        {
//            final boolean aIsPlayer0 = (g % 2 == 0);
//            playSingleGameWithStats(evalA, evalB, aIsPlayer0, depthA, depthB, report);
//        }
//
//        report.finalizeStats();
//        return report;
//    }
//
//    private void playSingleGameWithStats(
//            PositionEvaluator evalA,
//            PositionEvaluator evalB,
//            boolean aIsPlayer0,
//            int depthA,
//            int depthB,
//            StatsReport report)
//    {
//        startBoard.initialize();
//        BitBoard board = startBoard.clone();
//
//        ttA.clear();
//        ttB.clear();
//
//        playRandomOpening(board, RANDOM_OPENING_PLIES);
//
//        // Sauvegarde état global
//        final TwoLevelTranspositionTable savedTT = NNMinMaxNode.transpositionTable;
//        final PositionEvaluator savedEval = NNMinMaxNode.positionEvaluator;
//        final boolean savedExpired = NNMinMaxNode.timeExpired;
//        final long savedStart = NNMinMaxNode.searchStartTime;
//        final long savedMax = NNMinMaxNode.maxSearchTime;
//
//        NNMinMaxNode.timeExpired = false;
//        NNMinMaxNode.searchStartTime = 0L;
//        NNMinMaxNode.maxSearchTime = Long.MAX_VALUE;
//
//        try
//        {
//            int movesPlayed = 0;
//
//            while (!board.isGameOver() && movesPlayed < MAX_MOVES_PER_GAME)
//            {
//                final int currentPlayer = board.getCurrentPlayer();
//                final boolean aTurn =
//                        (aIsPlayer0 && currentPlayer == 0) ||
//                        (!aIsPlayer0 && currentPlayer == 1);
//
//                final PositionEvaluator currentEval = aTurn ? evalA : evalB;
//                final int currentDepth = aTurn ? depthA : depthB;
//
//                NNMinMaxNode.transpositionTable = aTurn ? ttA : ttB;
//
//                final DecisionStats ds = getBestDecisionWithNodeStats(board, currentEval, currentDepth);
//
//                // applique le coup
//                board.playMove(ds.decision);
//
//                // log
//                report.onMove(aTurn ? "A" : "B", movesPlayed, ds.nodesVisited);
//
//                movesPlayed++;
//            }
//
//            // résultat final
//            final int aPlayer = aIsPlayer0 ? 0 : 1;
//            final int bPlayer = 1 - aPlayer;
//
//            report.onGameEnd(board.getScore(aPlayer), board.getScore(bPlayer), board.getWinner());
//        }
//        finally
//        {
//            NNMinMaxNode.transpositionTable = savedTT;
//            NNMinMaxNode.positionEvaluator = savedEval;
//            NNMinMaxNode.timeExpired = savedExpired;
//            NNMinMaxNode.searchStartTime = savedStart;
//            NNMinMaxNode.maxSearchTime = savedMax;
//        }
//    }
//
//    private DecisionStats getBestDecisionWithNodeStats(BitBoard board, PositionEvaluator evaluator, int depth)
//    {
//        NNMinMaxNode.positionEvaluator = evaluator;
//
//        // Reset compteur nœuds pour cette décision
//        NNMinMaxNode.nodeCount = 0;
//
//        NNMinMaxNode.initialize(board, depth);
//
//        final NNMaxNode root = new NNMaxNode(board);
//
//        if (root.isInterrupted())
//            return new DecisionStats(fallbackDecision(board), NNMinMaxNode.nodeCount);
//
//        return new DecisionStats(root.getDecision(), NNMinMaxNode.nodeCount);
//    }
//
//    private void playRandomOpening(BitBoard board, int plies)
//    {
//        // Copie légère de ton BotEvaluator (si tu veux strictement le même random seed, réutilise ton code)
//        for (int p = 0; p < plies && !board.isGameOver(); p++)
//        {
//            final int player = board.getCurrentPlayer();
//            final boolean[] valid = board.getValidMoves(player);
//
//            int first = -1;
//            for (int i = 0; i < NB_HOLES; i++)
//                if (valid[i]) { first = i; break; }
//
//            if (first == -1) return;
//
//            final double[] decision = new double[NB_HOLES];
//            decision[first] = 1.0;
//            board.playMove(decision);
//        }
//    }
//
//    private double[] fallbackDecision(BitBoard board)
//    {
//        final int player = board.getCurrentPlayer();
//        final boolean[] valid = board.getValidMoves(player);
//        final double[] decision = new double[NB_HOLES];
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
//        final long nodesVisited;
//
//        DecisionStats(double[] decision, long nodesVisited)
//        {
//            this.decision = decision;
//            this.nodesVisited = nodesVisited;
//        }
//    }
//
//    // ===== Stats =====
//    public static final class StatsReport
//    {
//        private final List<Long> nodesAll = new ArrayList<>();
//        private final List<Long> nodesA = new ArrayList<>();
//        private final List<Long> nodesB = new ArrayList<>();
//
//        private int games = 0;
//        private int winsA = 0, winsB = 0, draws = 0;
//
//        public void onMove(String who, int plyIndex, long nodes)
//        {
//            nodesAll.add(nodes);
//            if ("A".equals(who)) nodesA.add(nodes); else nodesB.add(nodes);
//        }
//
//        public void onGameEnd(int scoreA, int scoreB, int winner)
//        {
//            games++;
//            if (winner == -1) draws++;
//            else if (winner == 0) winsA++; // attention: winner est relatif au playerId, pas "A/B"
//            else winsB++;
//        }
//
//        public void finalizeStats()
//        {
//            // tri pour percentiles
//            Collections.sort(nodesAll);
//            Collections.sort(nodesA);
//            Collections.sort(nodesB);
//        }
//
//        public String toPrettyString()
//        {
//            final StringBuilder sb = new StringBuilder();
//            sb.append("Games: ").append(games).append('\n');
//            sb.append("Moves measured: ").append(nodesAll.size()).append('\n');
//            sb.append('\n');
//
//            sb.append("All moves nodes stats:\n");
//            sb.append(formatStats(nodesAll)).append('\n');
//
//            sb.append("Player A moves nodes stats:\n");
//            sb.append(formatStats(nodesA)).append('\n');
//
//            sb.append("Player B moves nodes stats:\n");
//            sb.append(formatStats(nodesB)).append('\n');
//
//            sb.append('\n');
//            sb.append("Ordering toggles: ")
//              .append("categories=").append(MoveEvaluator.ENABLE_CATEGORY_ORDERING).append(", ")
//              .append("killers=").append(MoveEvaluator.ENABLE_KILLERS).append(", ");
//
//            return sb.toString();
//        }
//
//        public String toCsvSummary()
//        {
//            // CSV compact : count,mean,median,p90,p99,min,max
//            return "scope,count,mean,median,p90,p99,min,max\n"
//                + "all," + csvLine(nodesAll) + "\n"
//                + "A,"   + csvLine(nodesA) + "\n"
//                + "B,"   + csvLine(nodesB) + "\n";
//        }
//
//        private static String csvLine(List<Long> xs)
//        {
//            if (xs.isEmpty()) return "0,0,0,0,0,0,0";
//            final double mean = mean(xs);
//            return xs.size() + ","
//                    + String.format(java.util.Locale.US, "%.2f", mean) + ","
//                    + percentile(xs, 50) + ","
//                    + percentile(xs, 90) + ","
//                    + percentile(xs, 99) + ","
//                    + xs.get(0) + ","
//                    + xs.get(xs.size() - 1);
//        }
//
//        private static String formatStats(List<Long> xs)
//        {
//            if (xs.isEmpty()) return "  (no data)\n";
//
//            final double mean = mean(xs);
//            final long p50 = percentile(xs, 50);
//            final long p90 = percentile(xs, 90);
//            final long p95 = percentile(xs, 95);
//            final long p99 = percentile(xs, 99);
//            final long min = xs.get(0);
//            final long max = xs.get(xs.size() - 1);
//
//            return "  count=" + xs.size() + "\n"
//                 + "  mean=" + String.format(java.util.Locale.US, "%.2f", mean) + "\n"
//                 + "  p50=" + p50 + "  p90=" + p90 + "  p95=" + p95 + "  p99=" + p99 + "\n"
//                 + "  min=" + min + "  max=" + max + "\n";
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
//            if (xsSorted.isEmpty()) return 0;
//            final int n = xsSorted.size();
//            final int idx = (int) Math.ceil((p / 100.0) * n) - 1;
//            final int clamped = Math.max(0, Math.min(n - 1, idx));
//            return xsSorted.get(clamped);
//        }
//    }
//}