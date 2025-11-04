package projetoredes.rip;

public interface RoutingProtocolManagementInterface {
    boolean getDistanceTable(short nodeId);

    boolean getLinkCost(short id1, short id2);

    boolean setLinkCost(short id1, short id2, int cost);
}
