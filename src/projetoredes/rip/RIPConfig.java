package projetoredes.rip;

public record RIPConfig() {
    public static final short MANAGER_ID = 0;
    public static final int INFINITY = -1;
    public static final int MAX_NODES = 15;
    public static final int MAX_COST = 15;
    public static final long DEFAULT_PROP_TIME_MS = 10_000;
}
