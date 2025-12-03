package projetoredes.rip;

import projetoredes.unicast.UnicastServiceUserInterface;
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

public class RIPNode implements UnicastServiceUserInterface, AutoCloseable {
    private final short nodeId;
    private final UnicastProtocol unicastLayer;

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

    @Override
    public void UPDataInd(short sourceId, String data) {
        String[] parts = data.split(" ", 2);
        if (parts.length == 0) return;

        String pduType = parts[0];
        String payload = (parts.length > 1) ? parts[1] : "";

        // System.out.println("Nó " + nodeId + " recebeu PDU: " + pduType + " de " + sourceId);

        try {
            switch (pduType) {
                case "RIPGET":
                    handleRIPGet(sourceId, payload);
                    break;
                case "RIPSET":
                    handleRIPSet(sourceId, payload);
                    break;
                case "RIPIND":
                    handleRIPInd(sourceId, payload);
                    break;
                case "RIPRQT":
                    handleRIPRqt(sourceId);
                    break;
                default:
                    System.err.println("Nó " + nodeId + ": PDU desconhecida '" + pduType + "' de " + sourceId);
            }
        } catch (Exception e) {
            System.err.println("Nó " + nodeId + ": Erro ao processar PDU " + pduType + ": " + e.getMessage());
        }
    }

    // Handlers para PDUs

    // RIPGET <NodeA> <NodeB>
    // Resposta: RIPNTF <NodeA> <NodeB> <Cost>
    private void handleRIPGet(short sourceId, String payload) {
        if (sourceId != RIPConfig.MANAGER_ID) {
            System.err.println("Nó " + nodeId + ": RIPGET recebido de não-gerente (ID: " + sourceId + "). Ignorando.");
            return;
        }

        String[] parts = payload.split(" ");
        short nodeA = Short.parseShort(parts[0]);
        short nodeB = Short.parseShort(parts[1]);

        if (nodeA != this.nodeId) {
            System.err.println("Nó " + nodeId + ": RIPGET para nó " + nodeA + " recebido. Ignorando.");
            return;
        }

        // Pega o custo atual do enlace
        int cost = this.neighbors.getOrDefault(nodeB, RIPConfig.INFINITY);
        sendRIPNtf(RIPConfig.MANAGER_ID, this.nodeId, nodeB, cost);
    }


    // RIPSET <NodeA> <NodeB> <NewCost>
    // Resposta: RIPNTF <NodeA> <NodeB> <NewCost>
    private void handleRIPSet(short sourceId, String payload) {
        if (sourceId != RIPConfig.MANAGER_ID) {
            System.err.println("Nó " + nodeId + ": RIPSET recebido de não-gerente (ID: " + sourceId + "). Ignorando.");
            return;
        }

        String[] parts = payload.split(" ");
        short nodeA = Short.parseShort(parts[0]);
        short nodeB = Short.parseShort(parts[1]);
        int newCost = Integer.parseInt(parts[2]);

        if (nodeA != this.nodeId) {
            System.err.println("Nó " + nodeId + ": RIPSET para nó " + nodeA + " recebido. Ignorando.");
            return;
        }

        // O enlace deve existir na topologia original.
        if (!this.fullTopology.get(this.nodeId).containsKey(nodeB)) {
            System.err.println("Nó " + nodeId + ": RIPSET para enlace inexistente (" + nodeA + "-" + nodeB + "). Ignorando.");
            return;
        }

        // Atualiza o custo do vizinho
        this.neighbors.put(nodeB, newCost);
        System.out.println("Nó " + nodeId + ": Custo do enlace para " + nodeB + " alterado para " + newCost);

        // Se o custo for infinito, invalida a linha do vizinho na tabela
        if (newCost == RIPConfig.INFINITY) {
            int neighborRow = this.neighborToRowIndex.get(nodeB);
            Arrays.fill(this.distanceTable[neighborRow], RIPConfig.INFINITY);
            System.out.println("Nó " + nodeId + ": Enlace para " + nodeB + " é infinito. Invalidando seu vetor.");
        }

        // Recalcula o vetor e propaga se houver mudança
        recalculateDistanceVector();

        // Confirma a alteraçao para o gerente
        sendRIPNtf(RIPConfig.MANAGER_ID, this.nodeId, nodeB, newCost);
    }

    // RIPIND <SourceNodeID> <Vector>
    // Atualiza a tabela e recalcula o vetor se necessário
    private void handleRIPInd(short sourceId, String payload) {
        if (!this.neighbors.containsKey(sourceId)) {
            System.err.println("Nó " + nodeId + ": RIPIND recebido de não-vizinho (ID: " + sourceId + "). Ignorando.");
            return;
        }

        String[] parts = payload.split(" ", 2);
        short sendingNodeId = Short.parseShort(parts[0]);
        String vectorString = parts[1];

        if (sendingNodeId != sourceId) {
            System.err.println("Nó " + nodeId + ": ID de origem (" + sourceId + ") não bate com ID na PDU (" + sendingNodeId + "). Ignorando.");
            return;
        }

        // Se o enlace para este vizinho é infinito, ignoramos sua IND
        if (this.neighbors.get(sourceId) == RIPConfig.INFINITY) {
            System.out.println("Nó " + nodeId + ": RIPIND de " + sourceId + " ignorado (custo do enlace é infinito).");
            return;
        }

        int[] receivedVector = parseVector(vectorString);
        if (receivedVector.length != this.numNodes) {
            System.err.println("Nó " + nodeId + ": Vetor de " + sourceId + " com tamanho incorreto. Ignorando.");
            return;
        }

        // Atualiza a linha do vizinho na tabela
        int neighborRow = this.neighborToRowIndex.get(sourceId);
        System.arraycopy(receivedVector, 0, this.distanceTable[neighborRow], 0, this.numNodes);

        // Recalcula o vetor e propaga se houver mudança
        recalculateDistanceVector();
    }


    private void handleRIPRqt(short sourceId) {
        if (sourceId != RIPConfig.MANAGER_ID) {
            System.err.println("Nó " + nodeId + ": RIPRQT recebido de não-gerente (ID: " + sourceId + "). Ignorando.");
            return;
        }
        
        sendRIPRsp(RIPConfig.MANAGER_ID);
    }

    // Logica do algoritmo
    private synchronized void recalculateDistanceVector() {
        boolean changed = false;
        int selfIndex = nodeIdToIndex.get(this.nodeId);

        // Itera por todas as COLUNAS
        for (int j = 0; j < this.numNodes; j++) {
            
            // Custo para si mesmo e sempre 0
            if (j == selfIndex) {
                if (this.distanceVector[j] != 0) {
                    this.distanceVector[j] = 0;
                    changed = true;
                }
                continue;
            }

            int minCost = RIPConfig.INFINITY;

            // Itera por todos os VIZINHOS (linhas > 0)
            for (short neighborId : this.neighbors.keySet()) {
                int costToNeighbor = this.neighbors.get(neighborId);
                if (costToNeighbor == RIPConfig.INFINITY) {
                    continue; // Enlace para este vizinho esta rompido
                }

                int neighborRow = this.neighborToRowIndex.get(neighborId);
                int costFromNeighborToDest = this.distanceTable[neighborRow][j];

                if (costFromNeighborToDest != RIPConfig.INFINITY) {
                    int totalCost = costToNeighbor + costFromNeighborToDest;
                    
                    if (minCost == RIPConfig.INFINITY || totalCost < minCost) {
                        minCost = totalCost;
                    }
                }
            }

            // Atualiza o vetor do no se o custo mudou
            if (this.distanceVector[j] != minCost) {
                this.distanceVector[j] = minCost;
                changed = true;
            }
        }

        if (changed) {
            System.out.println("Nó " + nodeId + ": Vetor recalculado: " + formatVector(this.distanceVector));
            propagateVectorToNeighbors();
        }
    }

    // Classe interna extendendo TimerTask para propagar periodicamente o vetor de distancias
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

    // Metodo que envia o vetor de distancias atual para todos os vizinhos
    private void propagateVectorToNeighbors() {
            String pdu = "RIPIND " + this.nodeId + " " + formatVector(this.distanceVector);

            for (short neighborId : this.neighbors.keySet()) {
                // Nao envia para vizinhos com custo infinito
                if (this.neighbors.get(neighborId) != RIPConfig.INFINITY) {
                    unicastLayer.UPDataReq(neighborId, pdu);
                }
            }
    }

    // Metodos de envio de PDU
    private void sendRIPNtf(short destId, short nodeA, short nodeB, int cost) {
        String pdu = String.format("RIPNTF %d %d %d", nodeA, nodeB, cost);
        unicastLayer.UPDataReq(destId, pdu);
    }

    private void sendRIPRsp(short destId) {
        StringBuilder pduBuilder = new StringBuilder();
        pduBuilder.append("RIPRSP ").append(this.nodeId).append(" ");
        
        for (int i = 0; i < this.distanceTable.length; i++) {
            pduBuilder.append(formatVector(this.distanceTable[i]));
            if (i < this.distanceTable.length - 1) {
                pduBuilder.append(" ");
            }
        }
        
        unicastLayer.UPDataReq(destId, pduBuilder.toString());
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

    @Override
    public void close() {
        propagationTimer.cancel();
        if (unicastLayer != null) {
            unicastLayer.close();
        }
        System.out.println("Nó RIP " + nodeId + " encerrado.");
    }
}
