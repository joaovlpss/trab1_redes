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



}