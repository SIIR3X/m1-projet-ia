package awele.bot.competitor.noname.search.pv;

/**
 * @author Lucas Fagioli
 * Table de la variation principale (Principal Variation)
 * Stocke la meilleure ligne de jeu trouvée à chaque profondeur
 */
public final class PVTable
{
    /**
     * Profondeur max
     */
    private static final int MAX_DEPTH = 100;
    
    /**
     * Nombre maximum de coups dans une ligne
     */
    private static final int MAX_PLY = 50;
    
    /**
     * Table PV : pvMoves[depth][ply] = coup joué au ply donné
     */
    private final int[][] pvMoves;
    
    /**
     * Longueur de la PV pour chaque profondeur : pvLength[depth] = nombre de coups
     */
    private final int[] pvLength;
    
    public PVTable()
    {
        this.pvMoves = new int[MAX_DEPTH][MAX_PLY];
        this.pvLength = new int[MAX_DEPTH];
        
        for (int d = 0; d < MAX_DEPTH; d++)
        {
            pvLength[d] = 0;
            for (int p = 0; p < MAX_PLY; p++)
            {
                pvMoves[d][p] = -1;
            }
        }
    }
    
    public void store(int depth, int ply, int move, int[] childPV, int childPVLength)
    {
        if (depth < 0 || depth >= MAX_DEPTH || ply < 0 || ply >= MAX_PLY)
            return;
        
        pvMoves[depth][ply] = move;
        
        if (childPV != null && childPVLength > 0)
        {
            int copyLength = Math.min(childPVLength, MAX_PLY - ply - 1);
            for (int i = 0; i < copyLength; i++)
            {
                pvMoves[depth][ply + 1 + i] = childPV[i];
            }
            pvLength[depth] = ply + 1 + copyLength;
        }
        else
            pvLength[depth] = ply + 1;
    }
    
    public int probe(int depth, int ply)
    {
        if (depth < 0 || depth >= MAX_DEPTH || ply < 0 || ply >= MAX_PLY)
            return -1;
        
        if (ply >= pvLength[depth])
            return -1;
        
        return pvMoves[depth][ply];
    }
    
    public int[] getPV(int depth)
    {
        if (depth < 0 || depth >= MAX_DEPTH)
            return new int[0];
        
        int length = pvLength[depth];
        int[] pv = new int[length];
        
        for (int i = 0; i < length; i++)
        {
            pv[i] = pvMoves[depth][i];
        }
        
        return pv;
    }
    
    public int getLength(int depth)
    {
        if (depth < 0 || depth >= MAX_DEPTH)
            return 0;
        
        return pvLength[depth];
    }
    
    public void clear(int depth)
    {
        if (depth < 0 || depth >= MAX_DEPTH)
            return;
        
        pvLength[depth] = 0;
        for (int p = 0; p < MAX_PLY; p++)
        {
            pvMoves[depth][p] = -1;
        }
    }
    
    public void clearAll()
    {
        for (int d = 0; d < MAX_DEPTH; d++)
        {
            clear(d);
        }
    }
}