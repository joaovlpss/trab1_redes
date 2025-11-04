package projetoredes.rip;

public interface RoutingProtocolManagementServiceUserInterface {
    void distanceTableIndication(short nodeId, int[][] distanceTable);

    void linkCostIndication(short id1, short id2, int cost);
}
