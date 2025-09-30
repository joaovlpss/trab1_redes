package projetoredes.app;

import projetoredes.unicast.UnicastServiceUserInterface;

import java.util.Scanner;

import projetoredes.unicast.UnicastProtocol;


public class SimpleUnicastApp implements UnicastServiceUserInterface, AutoCloseable {

    private final UnicastProtocol protocol;
    private final short selfId;

    public SimpleUnicastApp(short selfId, String configPath) throws Exception {
        this.selfId = selfId;
        // Iniciar a thread receptora
        this.protocol = new UnicastProtocol(this, selfId, configPath);

        System.out.println("Aplicação iniciada com ID " + selfId);
    }
    
    @Override
    public void UPDataInd(short sourceId, String data) {
        // Esse e um app simples de teste, entao vamos 
        // apenas printar a mensagem para a tela.
        System.out.println("\r[Mensagem recebida de ID " + sourceId + "]: " + data);
        System.out.print("> ");
    }

    public void run() {
        Scanner scanner = new Scanner(System.in);
        System.out.println("---------------------------------------------------------");
        System.out.println("Comandos disponíveis:");
        System.out.println("  send <id_destino> <mensagem>   - Envia uma mensagem");
        System.out.println("  exit                           - Fecha a aplicação");
        System.out.println("---------------------------------------------------------");

        while(true) {
            System.out.print("> ");
            String line = scanner.nextLine();

            if(line.equalsIgnoreCase("exit")) {
                break;
            }

            String[] parts = line.split(" ", 3);

            if(parts.length == 3 && parts[0].equalsIgnoreCase("send")) {
                try {
                    short destinationId = Short.parseShort(parts[1]);
                    String message = parts[2];

                    if (destinationId == this.selfId) {
                        System.out.println("AVISO: Você não pode enviar uma mensagem para si mesmo.");
                        continue;
                    }

                    boolean success = protocol.UPDataReq(destinationId, message);
                    if (success) {
                        System.out.println("-> Mensagem enviada para o ID" + destinationId + ".");
                    } else {
                        System.out.println("-> Falha ao enviar mensagem. Verifique o ID inserido.");
                    }
                } catch (NumberFormatException e) {
                    System.err.println("ERRO: O ID de destino deve ser um número.");
                }
            } else {
                System.out.println("Comando inválido. Tente novamente.");
            }
        }
        scanner.close();
    }

    @Override
    public void close() {
        if(protocol != null) {
            protocol.close();
        }
        System.out.println("Aplicação encerrada.");
    }
}