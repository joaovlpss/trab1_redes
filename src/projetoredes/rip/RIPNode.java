package projetoredes.rip;

import projetoredes.unicast.UnicastServiceUserInterface;
import projetoredes.unicast.UnicastServiceInterface;
import projetoredes.unicast.UnicastProtocol;
import projetoredes.utils.Utils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

public class RIPNode implements UnicastServiceUserInterface {
    private final short nodeId;

    private final UnicastServiceInterface unicastLayer;

    // Estruturas de dados do roteamento
    private final Map<Short, Map<Short, Integer>> fullTopology;
    private final Map<Short, Integer> neighbors; // Vizinhos diretos e seus custos
    private final int numNodes;
    private final int[][] distanceTable; // Distance table
    private final int[] distanceVector; // Atalho para distanceTable[0]

    // Mapas para traduzir ID do no para indice do array
    // precisamos disso pq os IDs dos nos nao sao necessariamente sequenciais
    private final Map<Short, Integer> nodeIdToIndex = new HashMap<>();
    private final Map<Integer, Short> indexToNodeId = new HashMap<>();

    // Mapa para traduzir ID do vizinho para a LINHA na distanceTable
    private final Map<Short, Integer> neighborToRowIndex = new HashMap<>();

    private final Timer propagationTimer;


    public RIPNode(short nodeId, String unicastConfigPath, String ripConfigPath, long propagationTimeMs) throws IOException {
        if (nodeId == RIPConfig.MANAGER_ID || nodeId > RIPConfig.MAX_NODES) {
            throw new IllegalArgumentException("ID de nó inválido: " + nodeId);
        }

        this.nodeId = nodeId;

        // Carregar topologia
        this.fullTopology = Utils.loadTopology(ripConfigPath);
        if (this.fullTopology == null || !this.fullTopology.containsKey(nodeId)) {
            throw new IOException("Nó " + nodeId + " não encontrado na topologia ou falha ao carregar.");
        }
        
        // Clona o mapa de vizinhos para poder alterar custos dinamicamente
        this.neighbors = new HashMap<>(this.fullTopology.get(nodeId));
        this.numNodes = this.fullTopology.size();

        // Garantir uma ordem consistente (importante para os vetores) antes de mapear IDs para indices
        List<Short> sortedNodeIds = new ArrayList<>(this.fullTopology.keySet());
        Collections.sort(sortedNodeIds);
        for (int i = 0; i < sortedNodeIds.size(); i++) {
            short id = sortedNodeIds.get(i);
            this.nodeIdToIndex.put(id, i);
            this.indexToNodeId.put(i, id);
        }

        // Tabela de distancia
        // Linha 0: vetor do proprio no
        // Linhas 1..N: vetores dos N vizinhos
        this.distanceTable = new int[neighbors.size() + 1][numNodes];
        for (int[] row : distanceTable) {
            Arrays.fill(row, RIPConfig.INFINITY);
        }

        // Mapeia IDs de vizinhos para linhas da tabela
        int rowIndex = 1;
        for (short neighborId : neighbors.keySet()) {
            this.neighborToRowIndex.put(neighborId, rowIndex++);
        }

        // Inicializar vetor de distancia
        this.distanceVector = this.distanceTable[0];
        int selfIndex = this.nodeIdToIndex.get(this.nodeId);
        this.distanceVector[selfIndex] = 0;

        for (Map.Entry<Short, Integer> neighborEntry : neighbors.entrySet()) {
            int neighborIndex = this.nodeIdToIndex.get(neighborEntry.getKey());
            this.distanceVector[neighborIndex] = neighborEntry.getValue();
        }

        // Iniciar camada de Unicast
        try {
            this.unicastLayer = new UnicastProtocol(this, nodeId, unicastConfigPath);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao inicializar UnicastProtocol: " + e.getMessage(), e);
        }

        // Iniciar timer de propagaçao
        this.propagationTimer = new Timer("RIPNode-Timer-" + nodeId);
        this.propagationTimer.schedule(new PropagationTask(), propagationTimeMs, propagationTimeMs);

        System.out.println("Nó RIP " + nodeId + " inicializado. Vetor inicial: " + formatVector(distanceVector));
        propagateVectorToNeighbors(); // Propaga o vetor inicial
    }

    public void UPDataInd(short sourceId, String data) {
        // Implementar o recebimento de mensagens RIP aqui
    }


    // Task para propagar periodicamente o vetor de distancias
    
    private class PropagationTask extends TimerTask {
        @Override
        public void run() {
            try {
                // System.out.println("Nó " + nodeId + ": Timer de propagação disparado.");
                propagateVectorToNeighbors();
            } catch (Exception e) {
                System.err.println("Nó " + nodeId + ": Erro na propagação periódica: " + e.getMessage());
            }
        }
    }

    private void propagateVectorToNeighbors() {
            String pdu = "RIPIND " + this.nodeId + " " + formatVector(this.distanceVector);

            for (short neighborId : this.neighbors.keySet()) {
                // Nao envia para vizinhos com custo infinito
                if (this.neighbors.get(neighborId) != RIPConfig.INFINITY) {
                    unicastLayer.UPDataReq(neighborId, pdu);
                }
            }
    }

    // Utilitarios para formatar os vetores de distancia como strings e vice-versa
    private String formatVector(int[] vector) {
        return Arrays.stream(vector)
                     .mapToObj(String::valueOf)
                     .collect(Collectors.joining(":"));
    }

    private int[] parseVector(String vectorString) {
        return Arrays.stream(vectorString.split(":"))
                     .mapToInt(Integer::parseInt)
                     .toArray();
    }

}
