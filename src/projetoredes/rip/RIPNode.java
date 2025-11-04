package projetoredes.rip;

import projetoredes.unicast.UnicastServiceUserInterface;
import projetoredes.unicast.UnicastServiceInterface;
import projetoredes.unicast.UnicastProtocol;
import projetoredes.utils.Utils;

import java.io.IOException;
import java.util.Map;
import java.util.Timer;

public class RIPNode implements RoutingProtocolManagementInterface, UnicastServiceUserInterface {
    private final short nodeId;

    private final UnicastServiceInterface unicastLayer;

    private Map<Short, Integer> neighbors;

    public RIPNode(short nodeId, String unicastConfigPath, String ripConfigPath) {
        try {
            this.nodeId = nodeId;
            this.unicastLayer = new UnicastProtocol(this, nodeId, unicastConfigPath);
            this.neighbors = Utils.loadTopology(ripConfigPath).get(nodeId);

        } catch (IOException e) {
            throw new RuntimeException("Erro ao inicializar o nó RIP: " + e.getMessage(), e);
        }
    }

    public void UPDataInd(short sourceId, String data) {
        // Implementar o recebimento de mensagens RIP aqui
    }

}
