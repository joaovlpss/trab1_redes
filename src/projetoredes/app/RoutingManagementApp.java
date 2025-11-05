package projetoredes.app;

import projetoredes.rip.RIPManager;
import projetoredes.rip.RIPConfig;
import projetoredes.rip.RoutingProtocolManagementInterface;
import projetoredes.rip.RoutingProtocolManagementServiceUserInterface;

import java.io.IOException;
import java.util.Arrays;
import java.util.Scanner;
import java.util.stream.Collectors;


public class RoutingManagementApp implements RoutingProtocolManagementServiceUserInterface, Runnable, AutoCloseable {

    private final RoutingProtocolManagementInterface manager;
    private final Scanner scanner;

    public RoutingManagementApp(String unicastConfigPath, String ripConfigPath) throws IOException {
        this.scanner = new Scanner(System.in);
        this.manager = new RIPManager(this, unicastConfigPath, ripConfigPath);
    }


    @Override
    public synchronized void distanceTableIndication(short nodeId, int[][] distanceTable) {
        System.out.println("\n[INDICAÇÃO RECEBIDA]: Tabela de Distância do Nó " + nodeId);
        
        // Formata a tabela para impressao
        if (distanceTable.length > 0) {
            System.out.println("  Nó " + nodeId + " -> " + formatVector(distanceTable[0]));
            for (int i = 1; i < distanceTable.length; i++) {
                System.out.println("  Vizinho " + i + " -> " + formatVector(distanceTable[i]));
            }
        }
        System.out.print("> ");
    }

    @Override
    public synchronized void linkCostIndication(short id1, short id2, int cost) {
        String costStr = (cost == RIPConfig.INFINITY) ? "INFINITO" : String.valueOf(cost);
        System.out.println("\n[INDICAÇÃO RECEBIDA]: Custo do enlace (" + id1 + " <-> " + id2 + ") = " + costStr);
        System.out.print("> ");
    }


    @Override
    public void run() {
        printHelp();
        boolean running = true;

        while (running) {
            System.out.print("> ");
            try {
                String line = scanner.nextLine();
                if (line == null) break;

                String[] parts = line.trim().split("\\s+");
                if (parts.length == 0 || parts[0].isEmpty()) continue;

                String cmd = parts[0].toLowerCase();

                switch (cmd) {
                    case "getcost":
                        if (parts.length == 3) {
                            short id1 = Short.parseShort(parts[1]);
                            short id2 = Short.parseShort(parts[2]);
                            if (manager.getLinkCost(id1, id2)) {
                                System.out.println("... Requisição [getLinkCost] enviada para o nó " + id1);
                            } else {
                                System.err.println("Erro: Não foi possível enviar a requisição (verifique IDs/topologia).");
                            }
                        } else {
                            System.err.println("Uso: getcost <nodeId1> <nodeId2>");
                        }
                        break;

                    case "setcost":
                        if (parts.length == 4) {
                            short id1 = Short.parseShort(parts[1]);
                            short id2 = Short.parseShort(parts[2]);
                            int cost = Integer.parseInt(parts[3]);
                            if (manager.setLinkCost(id1, id2, cost)) {
                                System.out.println("... Requisição [setLinkCost] enviada para os nós " + id1 + " e " + id2);
                            } else {
                                System.err.println("Erro: Não foi possível enviar a requisição (verifique IDs/custo/topologia).");
                            }
                        } else {
                            System.err.println("Uso: setcost <nodeId1> <nodeId2> <custo>");
                        }
                        break;

                    case "gettable":
                        if (parts.length == 2) {
                            short nodeId = Short.parseShort(parts[1]);
                            if (manager.getDistanceTable(nodeId)) {
                                System.out.println("... Requisição [getDistanceTable] enviada para o nó " + nodeId);
                            } else {
                                System.err.println("Erro: Não foi possível enviar a requisição (verifique o ID).");
                            }
                        } else {
                            System.err.println("Uso: gettable <nodeId>");
                        }
                        break;

                    case "help":
                        printHelp();
                        break;

                    case "exit":
                        running = false;
                        break;

                    default:
                        System.err.println("Comando desconhecido. Digite 'help' para ajuda.");
                }
            } catch (NumberFormatException e) {
                System.err.println("Erro: ID ou custo inválido. Devem ser números.");
            } catch (Exception e) {
                System.err.println("Erro inesperado: " + e.getMessage());
                running = false;
            }
        }
        System.out.println("Encerrando aplicação de gerenciamento.");
    }

    private void printHelp() {
        System.out.println("-------------------------------------------------");
        System.out.println("Aplicação de Roteamento");
        System.out.println("Comandos disponíveis:");
        System.out.println("  getcost <id1> <id2>      - Requisita o custo do enlace entre os nós 1 e 2.");
        System.out.println("  setcost <id1> <id2> <custo> - Define o custo do enlace (use -1 para infinito).");
        System.out.println("  gettable <id>            - Requisita a tabela de distância completa do nó.");
        System.out.println("  help                     - Mostra esta ajuda.");
        System.out.println("  exit                     - Fecha a aplicação.");
        System.out.println("-------------------------------------------------");
    }

    @Override
    public void close() throws Exception {
        scanner.close();
        if (manager instanceof AutoCloseable) {
            ((AutoCloseable) manager).close();
        }
    }


    public static void main(String[] args) {
        // Caminhos para os arquivos de configuraçao
        String ucsapsPath = "config/ucsaps.conf";
        String ripidsPath = "config/ripids.conf";

        System.out.println("Iniciando Aplicação de Gerenciamento (ID 0)");

        try (RoutingManagementApp app = new RoutingManagementApp(ucsapsPath, ripidsPath)) {
            app.run(); // Inicia o loop da interface de comando
        } catch (Exception e) {
            System.err.println("Erro fatal ao iniciar o Gerente: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // Utilitarios
    private String formatVector(int[] vector) {
        return Arrays.stream(vector)
                .mapToObj(c -> (c == RIPConfig.INFINITY) ? "INF" : String.valueOf(c))
                .collect(Collectors.joining("\t| "));
    }
}