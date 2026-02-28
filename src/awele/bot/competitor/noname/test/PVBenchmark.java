//package awele.bot.competitor.noname.test;
//
//import awele.bot.competitor.noname.core.bitboard.BitBoard;
//import awele.bot.competitor.noname.ordering.PositionHistory;
//import awele.bot.competitor.noname.search.minmax.NNMaxNode;
//import awele.bot.competitor.noname.search.minmax.NNMinMaxNode;
//
///**
// * @author Lucas Fagioli
// * Test de benchmark pour mesurer l'efficacité de la PV Table
// */
//public class PVBenchmark
//{
//    /**
//     * Nombre de positions de test
//     */
//    private static final int NB_TEST_POSITIONS = 20;
//    
//    /**
//     * Profondeur de recherche pour les tests
//     */
//    private static final int TEST_DEPTH = 1000;
//    
//    /**
//     * Temps maximum par position (en ms)
//     */
//    private static final long MAX_TIME_MS = 500;
//    
//    public static void main(String[] args)
//    {
//        System.out.println("===== PV TABLE BENCHMARK =====");
//        System.out.println("Positions de test : " + NB_TEST_POSITIONS);
//        System.out.println("Profondeur : " + TEST_DEPTH);
//        System.out.println("Temps max par position : " + MAX_TIME_MS + "ms");
//        System.out.println();
//        
//        // Générer des positions de test
//        BitBoard[] testPositions = generateTestPositions(NB_TEST_POSITIONS);
//        
//        // Test SANS PV
//        System.out.println("===== TEST SANS PV =====");
//        BenchmarkResult withoutPV = runBenchmark(testPositions, false);
//        withoutPV.print();
//        System.out.println();
//        
//        // Test AVEC PV
//        System.out.println("===== TEST AVEC PV =====");
//        BenchmarkResult withPV = runBenchmark(testPositions, true);
//        withPV.print();
//        System.out.println();
//        
//        // Comparaison
//        System.out.println("===== COMPARAISON =====");
//        printComparison(withoutPV, withPV);
//    }
//    
//    /**
//     * Génère des positions de test variées
//     */
//    private static BitBoard[] generateTestPositions(int count)
//    {
//        BitBoard[] positions = new BitBoard[count];
//        BitBoard board = new BitBoard();
//        
//        for (int i = 0; i < count; i++)
//        {
//            board.initialize();
//            
//            // Jouer un nombre aléatoire de coups (entre 5 et 20)
//            int nbMoves = 5 + (int)(Math.random() * 15);
//            
//            for (int move = 0; move < nbMoves && !board.isGameOver(); move++)
//            {
//                int player = board.getCurrentPlayer();
//                boolean[] validMoves = board.getValidMoves(player);
//                
//                // Choisir un coup valide aléatoire
//                int validCount = 0;
//                for (boolean v : validMoves) if (v) validCount++;
//                
//                if (validCount == 0) break;
//                
//                int pick = (int)(Math.random() * validCount);
//                int selectedMove = -1;
//                
//                for (int j = 0; j < validMoves.length; j++)
//                {
//                    if (!validMoves[j]) continue;
//                    if (pick-- == 0)
//                    {
//                        selectedMove = j;
//                        break;
//                    }
//                }
//                
//                if (selectedMove >= 0)
//                {
//                    double[] decision = new double[6];
//                    decision[selectedMove] = 1.0;
//                    board.playMove(decision);
//                }
//            }
//            
//            positions[i] = board.clone();
//        }
//        
//        return positions;
//    }
//    
//    /**
//     * Exécute le benchmark sur les positions de test
//     */
//    private static BenchmarkResult runBenchmark(BitBoard[] positions, boolean usePV)
//    {
//        long totalNodes = 0;
//        long totalTime = 0;
//        int totalDepthReached = 0;
//        int positionsCompleted = 0;
//        
//        for (int i = 0; i < positions.length; i++)
//        {
//            BitBoard position = positions[i].clone();
//            
//            if (position.isGameOver())
//                continue;
//            
//            // Réinitialiser les structures
//            NNMinMaxNode.transpositionTable.clear();
//            if (usePV)
//            {
//                NNMinMaxNode.pvTable.clearAll();
//                NNMinMaxNode.currentPly = 0;
//            }
//            PositionHistory.clear();
//            NNMinMaxNode.nodeCount = 0;
//            
//            // Configurer le timer
//            NNMinMaxNode.startTimer(MAX_TIME_MS);
//            
//            // Iterative Deepening
//            int depthReached = 0;
//            long startTime = System.nanoTime();
//            
//            for (int depth = 1; depth <= TEST_DEPTH; depth++)
//            {
//                if (NNMinMaxNode.isTimeExpired())
//                    break;
//                
//                NNMinMaxNode.initialize(position, depth);
//                NNMaxNode rootNode = new NNMaxNode(position);
//                
//                if (rootNode.isInterrupted())
//                    break;
//                
//                depthReached = depth;
//            }
//            
//            long endTime = System.nanoTime();
//            long elapsedMs = (endTime - startTime) / 1_000_000L;
//            
//            totalNodes += NNMinMaxNode.nodeCount;
//            totalTime += elapsedMs;
//            totalDepthReached += depthReached;
//            positionsCompleted++;
//            
//            // Afficher progression
//            if ((i + 1) % 5 == 0)
//            {
//                System.out.println("  Position " + (i + 1) + "/" + positions.length + 
//                    " - Depth: " + depthReached + 
//                    " - Nodes: " + NNMinMaxNode.nodeCount + 
//                    " - Time: " + elapsedMs + "ms");
//            }
//            
//            NNMinMaxNode.resetTimer();
//        }
//        
//        return new BenchmarkResult(
//            positionsCompleted,
//            totalNodes,
//            totalTime,
//            totalDepthReached
//        );
//    }
//    
//    /**
//     * Affiche la comparaison entre les deux résultats
//     */
//    private static void printComparison(BenchmarkResult without, BenchmarkResult with)
//    {
//        double avgDepthWithout = (double)without.totalDepth / without.positions;
//        double avgDepthWith = (double)with.totalDepth / with.positions;
//        double depthGain = ((avgDepthWith - avgDepthWithout) / avgDepthWithout) * 100;
//        
//        long avgNodesWithout = without.totalNodes / without.positions;
//        long avgNodesWith = with.totalNodes / with.positions;
//        double nodesGain = ((double)(avgNodesWith - avgNodesWithout) / avgNodesWithout) * 100;
//        
//        long avgTimeWithout = without.totalTime / without.positions;
//        long avgTimeWith = with.totalTime / with.positions;
//        double timeGain = ((double)(avgTimeWith - avgTimeWithout) / avgTimeWithout) * 100;
//        
//        long npsWithout = (without.totalNodes * 1000) / Math.max(1, without.totalTime);
//        long npsWith = (with.totalNodes * 1000) / Math.max(1, with.totalTime);
//        double npsGain = ((double)(npsWith - npsWithout) / npsWithout) * 100;
//        
//        System.out.println("Profondeur moyenne :");
//        System.out.println("  Sans PV : " + String.format("%.2f", avgDepthWithout));
//        System.out.println("  Avec PV : " + String.format("%.2f", avgDepthWith));
//        System.out.println("  Gain    : " + String.format("%+.2f%%", depthGain));
//        System.out.println();
//        
//        System.out.println("Nœuds moyens par position :");
//        System.out.println("  Sans PV : " + avgNodesWithout);
//        System.out.println("  Avec PV : " + avgNodesWith);
//        System.out.println("  Gain    : " + String.format("%+.2f%%", nodesGain));
//        System.out.println();
//        
//        System.out.println("Temps moyen par position :");
//        System.out.println("  Sans PV : " + avgTimeWithout + "ms");
//        System.out.println("  Avec PV : " + avgTimeWith + "ms");
//        System.out.println("  Gain    : " + String.format("%+.2f%%", timeGain));
//        System.out.println();
//        
//        System.out.println("Nœuds par seconde :");
//        System.out.println("  Sans PV : " + npsWithout + " n/s");
//        System.out.println("  Avec PV : " + npsWith + " n/s");
//        System.out.println("  Gain    : " + String.format("%+.2f%%", npsGain));
//        System.out.println();
//        
//        // Verdict
//        System.out.println("===== VERDICT =====");
//        if (depthGain > 5)
//            System.out.println("✓ PV améliore significativement la profondeur (+%.2f%%)".formatted(depthGain));
//        else if (depthGain > 0)
//            System.out.println("~ PV améliore légèrement la profondeur (+%.2f%%)".formatted(depthGain));
//        else
//            System.out.println("✗ PV n'améliore pas la profondeur (%.2f%%)".formatted(depthGain));
//        
//        if (npsGain > 0)
//            System.out.println("✓ PV améliore la vitesse de recherche (+%.2f%%)".formatted(npsGain));
//        else
//            System.out.println("✗ PV ralentit légèrement la recherche (%.2f%%)".formatted(npsGain));
//    }
//    
//    /**
//     * Résultat d'un benchmark
//     */
//    private static class BenchmarkResult
//    {
//        final int positions;
//        final long totalNodes;
//        final long totalTime;
//        final int totalDepth;
//        
//        BenchmarkResult(int positions, long totalNodes, long totalTime, int totalDepth)
//        {
//            this.positions = positions;
//            this.totalNodes = totalNodes;
//            this.totalTime = totalTime;
//            this.totalDepth = totalDepth;
//        }
//        
//        void print()
//        {
//            System.out.println("Positions testées : " + positions);
//            System.out.println("Nœuds totaux      : " + totalNodes);
//            System.out.println("Temps total       : " + totalTime + "ms");
//            System.out.println("Profondeur totale : " + totalDepth);
//            System.out.println("Profondeur moy.   : " + String.format("%.2f", (double)totalDepth / positions));
//            System.out.println("Nœuds moyens      : " + (totalNodes / positions));
//            System.out.println("Temps moyen       : " + (totalTime / positions) + "ms");
//            System.out.println("Nœuds/sec         : " + ((totalNodes * 1000) / Math.max(1, totalTime)) + " n/s");
//        }
//    }
//}