//package awele.bot.competitor.noname.test;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Random;
//
//import awele.bot.competitor.noname.algorithms.minmax.BitMaxNode;
//import awele.bot.competitor.noname.algorithms.minmax.BitMinMaxNode;
//import awele.bot.competitor.noname.algorithms.transposition.EntryType;
//import awele.bot.competitor.noname.algorithms.transposition.TranspositionEntry;
//import awele.bot.competitor.noname.algorithms.transposition.TranspositionTable;
//import awele.bot.competitor.noname.core.bitboard.BitBoard;
//
///**
// * @author Lucas Fagioli
// *
// * Benchmark v3 — mesure l'impact du TT move ordering.
// *
// * ARCHITECTURE : on ne sous-classe pas TranspositionTable (qui est final).
// * On injecte à la place une interface TTAdapter dans BitMinMaxNode via
// * un champ statique temporaire, ce qui est impossible sans modifier BitMinMaxNode...
// *
// * Solution retenue : on injecte le mode via un flag statique du benchmark,
// * et on wrape TranspositionTable dans une classe interne NON-finale
// * TTAdapter qui implémente le même contrat via composition.
// *
// * BitMinMaxNode.transpositionTable est remplacé pendant le run par une instance
// * de TTAdapter qui délègue à une vraie TranspositionTable selon le mode.
// *
// * Les 3 modes :
// *   NO_TT        — probe() retourne toujours null, store() = no-op
// *   TT_CACHE     — probe() retourne l'entrée avec bestMove masqué à -1
// *   TT_FULL      — probe() retourne l'entrée intacte + comptage des hits/moveUsed
// *
// * La substitution est possible parce que BitMinMaxNode.transpositionTable
// * est de type TranspositionTable (pas final en tant que variable), et TTAdapter
// * étend une classe intermédiaire NON-finale qu'on crée ici.
// *
// * CORRECTION : on introduit TranspositionTableBase (non-finale) dont héritent
// * les wrappers, et on s'assure que BitMinMaxNode reçoit bien un objet compatible.
// * Comme BitMinMaxNode.transpositionTable est typé TranspositionTable (finale),
// * la seule solution sans modifier le code de production est d'utiliser
// * la réflexion pour remplacer le champ, ou de demander à l'utilisateur
// * de retirer le `final` de TranspositionTable.
// *
// * ==> INSTRUCTION : retirer le mot-clé `final` de la déclaration de TranspositionTable.
// *     Changer :  public final class TranspositionTable
// *     En :       public class TranspositionTable
// *     C'est la seule modification nécessaire dans le code de production.
// */
//public class TTMoveOrderingBenchmark
//{
//    // =========================================================================
//    // Configuration
//    // =========================================================================
//
//    private static final int  NB_POSITIONS     = 200;
//    private static final int  TARGET_DEPTH     = 10;
//    private static final int  RANDOM_PLIES_MIN = 6;
//    private static final int  RANDOM_PLIES_MAX = 24;
//    private static final long SEED             = 0xDEADBEEFL;
//
//    // =========================================================================
//    // Compteurs — incrémentés par les wrappers pendant un run
//    // =========================================================================
//
//    static long ttHits     = 0;
//    static long ttMoveUsed = 0;
//
//    static void resetCounters()
//    {
//        ttHits     = 0;
//        ttMoveUsed = 0;
//    }
//
//    // =========================================================================
//    // Wrappers (nécessitent que TranspositionTable ne soit PAS final)
//    // =========================================================================
//
//    /**
//     * Pas de TT : alpha-beta pur.
//     * probe() retourne toujours null → pas de cutoff, pas d'ordering TT.
//     * store() est un no-op → rien n'est mémorisé entre les nœuds.
//     */
//    static final class NullTT extends TranspositionTable
//    {
//        @Override public TranspositionEntry probe(long key)                                                         { return null; }
//        @Override public void store(long key, double v, int d, EntryType t, int m)                                  {}
//        @Override public void clear()        {}
//        @Override public void incrementAge() {}
//    }
//
//    /**
//     * TT normale avec bestMove masqué à -1.
//     * Les cutoffs EXACT/LOWER/UPPER fonctionnent normalement.
//     * Le MoveEvaluator reçoit ttBestMove = -1 → pas de bonus TT dans le tri.
//     *
//     * Cela isole l'apport du cache pur (réductions de l'arbre via bornes)
//     * sans l'aide de l'ordering par le meilleur coup.
//     */
//    static final class CacheOnlyTT extends TranspositionTable
//    {
//        @Override
//        public TranspositionEntry probe(long key)
//        {
//            final TranspositionEntry e = super.probe(key);
//            if (e == null) return null;
//            ttHits++;
//            // On retourne l'entrée avec bestMove = -1 pour neutraliser l'ordering
//            return new TranspositionEntry(e.key, e.evaluation, e.depth, e.type, -1, e.generation);
//        }
//    }
//
//    /**
//     * TT complète instrumentée.
//     * Comportement identique à la TT de production.
//     * Comptage des hits et des fois où bestMove >= 0.
//     */
//    static final class InstrumentedFullTT extends TranspositionTable
//    {
//        @Override
//        public TranspositionEntry probe(long key)
//        {
//            final TranspositionEntry e = super.probe(key);
//            if (e == null) return null;
//            ttHits++;
//            if (e.bestMove >= 0) ttMoveUsed++;
//            return e;
//        }
//    }
//
//    // =========================================================================
//    // Résultat d'un run
//    // =========================================================================
//
//    static final class RunResult
//    {
//        final long nodes;
//        final long timeNs;
//        final long hits;
//        final long moveUsed;
//
//        RunResult(long nodes, long timeNs, long hits, long moveUsed)
//        {
//            this.nodes    = nodes;
//            this.timeNs   = timeNs;
//            this.hits     = hits;
//            this.moveUsed = moveUsed;
//        }
//    }
//
//    // =========================================================================
//    // Recherche avec iterative deepening — sauvegarde/restaure l'état statique
//    // =========================================================================
//
//    static RunResult runIterativeDeepening(BitBoard board, TranspositionTable tt, int targetDepth)
//    {
//        final TranspositionTable savedTT    = BitMinMaxNode.transpositionTable;
//        final boolean            savedExp   = BitMinMaxNode.timeExpired;
//        final long               savedStart = BitMinMaxNode.searchStartTime;
//        final long               savedMax   = BitMinMaxNode.maxSearchTime;
//
//        BitMinMaxNode.transpositionTable = tt;
//        BitMinMaxNode.timeExpired        = false;
//        BitMinMaxNode.searchStartTime    = System.nanoTime();
//        BitMinMaxNode.maxSearchTime      = Long.MAX_VALUE;
//        BitMinMaxNode.nodeCount          = 0;
//        resetCounters();
//
//        final long t0 = System.nanoTime();
//
//        try
//        {
//            for (int depth = 1; depth <= targetDepth; depth++)
//            {
//                BitMinMaxNode.initialize(board, depth);
//                final BitMaxNode root = new BitMaxNode(board);
//                if (root.isInterrupted()) break;
//            }
//        }
//        finally
//        {
//            BitMinMaxNode.transpositionTable = savedTT;
//            BitMinMaxNode.timeExpired        = savedExp;
//            BitMinMaxNode.searchStartTime    = savedStart;
//            BitMinMaxNode.maxSearchTime      = savedMax;
//        }
//
//        return new RunResult(BitMinMaxNode.nodeCount, System.nanoTime() - t0, ttHits, ttMoveUsed);
//    }
//
//    // =========================================================================
//    // Génération de positions aléatoires
//    // =========================================================================
//
//    static List<BitBoard> generatePositions(int count, long seed)
//    {
//        final Random         rng = new Random(seed);
//        final List<BitBoard> r   = new ArrayList<>(count);
//
//        while (r.size() < count)
//        {
//            final BitBoard board = new BitBoard();
//            final int plies = RANDOM_PLIES_MIN + rng.nextInt(RANDOM_PLIES_MAX - RANDOM_PLIES_MIN + 1);
//            boolean valid = true;
//
//            for (int p = 0; p < plies; p++)
//            {
//                if (board.isGameOver()) { valid = false; break; }
//
//                final int      player = board.getCurrentPlayer();
//                final boolean[] moves = board.getValidMoves(player);
//
//                int cnt = 0;
//                for (boolean v : moves) if (v) cnt++;
//                if (cnt == 0) { valid = false; break; }
//
//                int pick = rng.nextInt(cnt);
//                for (int i = 0; i < moves.length; i++)
//                {
//                    if (!moves[i]) continue;
//                    if (pick-- == 0) { board.playMove(player, i); break; }
//                }
//            }
//
//            if (valid && !board.isGameOver())
//                r.add(board.clone());
//        }
//
//        return r;
//    }
//
//    // =========================================================================
//    // Rapport
//    // =========================================================================
//
//    static double pct(double base, double test)
//    {
//        return base > 0 ? (base - test) / base * 100.0 : 0.0;
//    }
//
//    static void printReport(
//        int    nbPos,
//        double avgNodesNoTT, double avgNodesCache, double avgNodesFull,
//        double avgTimeNoTT,  double avgTimeCache,  double avgTimeFull,
//        double avgHitsCache, double avgHitsFull,   double avgMoveUsed)
//    {
//        System.out.println("=".repeat(72));
//        System.out.printf("  TT MOVE ORDERING BENCHMARK  —  %d positions  depth 1..%d%n", nbPos, TARGET_DEPTH);
//        System.out.println("=".repeat(72));
//
//        System.out.printf("%n[ Nœuds explorés (cumul iterative deepening 1..%d) ]%n", TARGET_DEPTH);
//        System.out.printf("  NO_TT    : %12.0f%n", avgNodesNoTT);
//        System.out.printf("  TT_CACHE : %12.0f   (%+6.1f%% vs NO_TT)%n",
//            avgNodesCache, -pct(avgNodesNoTT, avgNodesCache));
//        System.out.printf("  TT_FULL  : %12.0f   (%+6.1f%% vs NO_TT | %+6.1f%% vs CACHE)%n",
//            avgNodesFull, -pct(avgNodesNoTT, avgNodesFull), -pct(avgNodesCache, avgNodesFull));
//
//        System.out.printf("%n[ Temps moyen par décision ]%n");
//        System.out.printf("  NO_TT    : %8.2f ms%n", avgTimeNoTT   / 1e6);
//        System.out.printf("  TT_CACHE : %8.2f ms%n", avgTimeCache  / 1e6);
//        System.out.printf("  TT_FULL  : %8.2f ms%n", avgTimeFull   / 1e6);
//
//        // Les hits de CACHE et FULL ne sont pas directement comparables car
//        // TT_FULL explore moins de nœuds → moins d'appels à probe().
//        // On normalise donc par le nombre de nœuds pour comparer le taux de hit.
//        System.out.printf("%n[ Table de transposition ]%n");
//        System.out.printf("  Hits moyens (CACHE) : %8.0f   (sur ~%.0f nœuds)%n", avgHitsCache, avgNodesCache);
//        System.out.printf("  Hits moyens (FULL)  : %8.0f   (sur ~%.0f nœuds)%n", avgHitsFull,  avgNodesFull);
//        System.out.printf("  Taux de hit (CACHE) : %6.1f%% par nœud%n",
//            avgNodesCache > 0 ? avgHitsCache / avgNodesCache * 100 : 0);
//        System.out.printf("  Taux de hit (FULL)  : %6.1f%% par nœud%n",
//            avgNodesFull  > 0 ? avgHitsFull  / avgNodesFull  * 100 : 0);
//        System.out.printf("  TT move présent (FULL) : %.1f%% des hits%n",
//            avgHitsFull > 0 ? avgMoveUsed / avgHitsFull * 100 : 0);
//
//        System.out.printf("%n[ Analyse ]%n");
//
//        final double gainCache = pct(avgNodesNoTT,   avgNodesCache);
//        final double gainFull  = pct(avgNodesNoTT,   avgNodesFull);
//        final double gainOrder = pct(avgNodesCache,  avgNodesFull);
//
//        // Gain du cache seul
//        if (gainCache < 2.0)
//            System.out.println("  Cache TT seul  : ⚠  < 2% — les bornes seules n'élagage presque rien");
//        else
//            System.out.printf( "  Cache TT seul  : ✓  -%.1f%% de nœuds (cutoffs EXACT/BOUND)%n", gainCache);
//
//        // Gain du TT move ordering
//        if (gainOrder < 2.0)
//            System.out.println("  TT move order  : ⚠  < 2% — marginal, le coup TT n'aide pas le tri");
//        else if (gainOrder < 15.0)
//            System.out.printf( "  TT move order  : ~  -%.1f%% supplémentaires grâce au tri TT move%n", gainOrder);
//        else
//            System.out.printf( "  TT move order  : ✓  -%.1f%% supplémentaires — le TT move est décisif%n", gainOrder);
//
//        // Interprétation du couplage cache + ordering
//        if (gainCache < 2.0 && gainOrder > 10.0)
//        {
//            System.out.println();
//            System.out.println("  ► Le cache seul est inefficace sans le bon ordering :");
//            System.out.println("    un cutoff LOWER_BOUND ne sert que si le coup qui l'a généré");
//            System.out.println("    est exploré en premier. Sans TT move, la borne arrive trop tard.");
//            System.out.println("    TT move ordering et cache TT sont donc couplés, pas indépendants.");
//        }
//
//        System.out.println("=".repeat(72));
//    }
//
//    // =========================================================================
//    // Main
//    // =========================================================================
//
//    public static void main(String[] args)
//    {
//        System.out.printf("Génération de %d positions (plies %d..%d)...%n",
//            NB_POSITIONS, RANDOM_PLIES_MIN, RANDOM_PLIES_MAX);
//        final List<BitBoard> positions = generatePositions(NB_POSITIONS, SEED);
//        System.out.printf("%d positions générées.%n%n", positions.size());
//
//        System.out.println("Warmup JIT (5 positions × 3 modes)...");
//        for (int w = 0; w < 5; w++)
//        {
//            final BitBoard pos = positions.get(w);
//            runIterativeDeepening(pos, new NullTT(),             TARGET_DEPTH);
//            runIterativeDeepening(pos, new CacheOnlyTT(),        TARGET_DEPTH);
//            runIterativeDeepening(pos, new InstrumentedFullTT(), TARGET_DEPTH);
//        }
//        System.out.println("Warmup terminé.\n");
//
//        final long[] nodesNoTT  = new long[NB_POSITIONS];
//        final long[] nodesCache = new long[NB_POSITIONS];
//        final long[] nodesFull  = new long[NB_POSITIONS];
//        final long[] timeNoTT   = new long[NB_POSITIONS];
//        final long[] timeCache  = new long[NB_POSITIONS];
//        final long[] timeFull   = new long[NB_POSITIONS];
//        final long[] hitsCache  = new long[NB_POSITIONS];
//        final long[] hitsFull   = new long[NB_POSITIONS];
//        final long[] moveUsed   = new long[NB_POSITIONS];
//
//        for (int i = 0; i < NB_POSITIONS; i++)
//        {
//            final BitBoard pos = positions.get(i);
//
//            final RunResult rNoTT  = runIterativeDeepening(pos, new NullTT(),             TARGET_DEPTH);
//            final RunResult rCache = runIterativeDeepening(pos, new CacheOnlyTT(),        TARGET_DEPTH);
//            final RunResult rFull  = runIterativeDeepening(pos, new InstrumentedFullTT(), TARGET_DEPTH);
//
//            nodesNoTT[i]  = rNoTT.nodes;
//            nodesCache[i] = rCache.nodes;
//            nodesFull[i]  = rFull.nodes;
//            timeNoTT[i]   = rNoTT.timeNs;
//            timeCache[i]  = rCache.timeNs;
//            timeFull[i]   = rFull.timeNs;
//            hitsCache[i]  = rCache.hits;
//            hitsFull[i]   = rFull.hits;
//            moveUsed[i]   = rFull.moveUsed;
//
//            if ((i + 1) % 50 == 0)
//                System.out.printf("  %d/%d positions traitées...%n", i + 1, NB_POSITIONS);
//        }
//
//        double sNodesNoTT = 0, sNodesCache = 0, sNodesFull = 0;
//        double sTimeNoTT  = 0, sTimeCache  = 0, sTimeFull  = 0;
//        double sHitsCache = 0, sHitsFull   = 0, sMoveUsed  = 0;
//
//        for (int i = 0; i < NB_POSITIONS; i++)
//        {
//            sNodesNoTT  += nodesNoTT[i];
//            sNodesCache += nodesCache[i];
//            sNodesFull  += nodesFull[i];
//            sTimeNoTT   += timeNoTT[i];
//            sTimeCache  += timeCache[i];
//            sTimeFull   += timeFull[i];
//            sHitsCache  += hitsCache[i];
//            sHitsFull   += hitsFull[i];
//            sMoveUsed   += moveUsed[i];
//        }
//
//        System.out.println();
//        printReport(
//            NB_POSITIONS,
//            sNodesNoTT  / NB_POSITIONS, sNodesCache / NB_POSITIONS, sNodesFull  / NB_POSITIONS,
//            sTimeNoTT   / NB_POSITIONS, sTimeCache  / NB_POSITIONS, sTimeFull   / NB_POSITIONS,
//            sHitsCache  / NB_POSITIONS, sHitsFull   / NB_POSITIONS, sMoveUsed   / NB_POSITIONS
//        );
//    }
//}