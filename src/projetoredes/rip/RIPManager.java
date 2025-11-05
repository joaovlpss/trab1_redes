package projetoredes.rip;

import projetoredes.unicast.UnicastProtocol;
import projetoredes.unicast.UnicastServiceInterface;
import projetoredes.unicast.UnicastServiceUserInterface;
import projetoredes.utils.Utils;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;


public class RIPManager implements RoutingProtocolManagementInterface, UnicastServiceUserInterface {

    private final UnicastServiceInterface unicastLayer;
    private final RoutingProtocolManagementServiceUserInterface applicationUser;

    // Topologia carregada para validar requisicoes
    private final Map<Short, Map<Short, Integer>> topology;
    private final Set<Short> allNodeIds;
    private final int numNodes;


    public RIPManager(RoutingProtocolManagementServiceUserInterface applicationUser,
                      String unicastConfigPath,
                      String ripConfigPath) throws IOException {

        if (applicationUser == null) {
            throw new IllegalArgumentException("Usuário da aplicação não pode ser nulo.");
        }
        this.applicationUser = applicationUser;

        // Carrega a topologia para validaçao
        this.topology = Utils.loadTopology(ripConfigPath);
        if (this.topology == null) {
            throw new IOException("Falha ao carregar a topologia " + ripConfigPath);
        }
        this.allNodeIds = this.topology.keySet();
        this.numNodes = this.allNodeIds.size();

        // Inicia a camada de Unicast com o ID de gerente
        try {
            this.unicastLayer = new UnicastProtocol(this, RIPConfig.MANAGER_ID, unicastConfigPath);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao inicializar UnicastProtocol para o Gerente: " + e.getMessage(), e);
        }
        
        System.out.println("Gerente RIP (ID " + RIPConfig.MANAGER_ID + ") inicializado.");
    }


    @Override
    public boolean getDistanceTable(short nodeId) {
        if (!allNodeIds.contains(nodeId)) {
            System.err.println("Gerente: getDistanceTable para nó inválido " + nodeId);
            return false;
        }

        String pdu = "RIPRQT";
        return unicastLayer.UPDataReq(nodeId, pdu);
    }

    @Override
    public boolean getLinkCost(short id1, short id2) {
        if (!allNodeIds.contains(id1) || !allNodeIds.contains(id2)) {
            System.err.println("Gerente: getLinkCost para nós inválidos " + id1 + ", " + id2);
            return false;
        }

        // Valida se o enlace existe na topologia original
        if (!topology.get(id1).containsKey(id2)) {
            System.err.println("Gerente: getLinkCost para enlace inexistente " + id1 + "-" + id2);
            return false;
        }

        String pdu = String.format("RIPGET %d %d", id1, id2);
        return unicastLayer.UPDataReq(id1, pdu);
    }

    @Override
    public boolean setLinkCost(short id1, short id2, int cost) {
        if (!allNodeIds.contains(id1) || !allNodeIds.contains(id2)) {
            System.err.println("Gerente: setLinkCost para nós inválidos " + id1 + ", " + id2);
            return false;
        }

        // Valida se o enlace existe na topologia original
        if (!topology.get(id1).containsKey(id2)) {
            System.err.println("Gerente: setLinkCost para enlace inexistente " + id1 + "-" + id2);
            return false;
        }

        // Valida o custo
        if (cost != RIPConfig.INFINITY && (cost < 1 || cost > RIPConfig.MAX_COST)) {
            System.err.println("Gerente: setLinkCost com custo inválido " + cost);
            return false;
        }

        // Enviamos para os dois nos envolvidos
        String pdu1 = String.format("RIPSET %d %d %d", id1, id2, cost);
        String pdu2 = String.format("RIPSET %d %d %d", id2, id1, cost);

        boolean ok1 = unicastLayer.UPDataReq(id1, pdu1);
        boolean ok2 = unicastLayer.UPDataReq(id2, pdu2);

        return ok1 && ok2;
    }

}