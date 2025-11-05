package projetoredes.app;

import projetoredes.rip.RIPConfig;
import projetoredes.rip.RIPNode;

import java.io.IOException;

public class NodeLauncher {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Erro: ID do nó não fornecido.");
            System.err.println("Uso: java projetoredes.app.NodeLauncher <nodeId>");
            System.exit(1);
        }

        short nodeId = -1;
        try {
            nodeId = Short.parseShort(args[0]);
            if (nodeId == RIPConfig.MANAGER_ID || nodeId > RIPConfig.MAX_NODES) {
                throw new NumberFormatException("ID do nó deve estar entre 1 e " + RIPConfig.MAX_NODES);
            }
        } catch (NumberFormatException e) {
            System.err.println("Erro: ID do nó inválido: " + args[0]);
            System.exit(1);
        }

        // Caminhos para os arquivos de configuraçao
        String ucsapsPath = "config/ucsaps.conf";
        String ripidsPath = "config/ripids.conf";
        
        long propTime = RIPConfig.DEFAULT_PROP_TIME_MS;
        if (args.length > 1) {
            try {
                // Permite sobreescrever o tempo de propagaçao via argumento
                propTime = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Aviso: Tempo de propagação inválido, usando padrão (" + propTime + "ms).");
            }
        }

        System.out.println("Iniciando Nó RIP com ID: " + nodeId);
        
        // Usa try-with-resources para garantir que o node.close() seja chamado
        // ao final da execuçao
        try (RIPNode node = new RIPNode(nodeId, ucsapsPath, ripidsPath, propTime)) {
            
            System.out.println("Nó " + nodeId + " está rodando.");
            System.out.println("Pressione [Enter] para encerrar este nó");
            
            // Trava a thread principal aqui.
            // Os threads do UnicastProtocol e do Timer do RIPNode continuarao rodando.
            try {
                System.in.read();
            } catch (IOException e) {
                // Ignora
            }
            
            System.out.println("Encerrando nó " + nodeId);
            
        } catch (Exception e) {
            System.err.println("Erro fatal no nó " + nodeId + ": " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("Nó " + nodeId + " desligado.");
    }
}