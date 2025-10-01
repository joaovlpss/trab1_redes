package projetoredes.app;

import projetoredes.unicast.UnicastServiceUserInterface;

import java.io.IOException;
import java.util.Scanner;

import projetoredes.unicast.UnicastProtocol;


public class SimpleUnicastApp implements UnicastServiceUserInterface, AutoCloseable {

    private final UnicastProtocol protocol;
    private final short selfId;

    public SimpleUnicastApp(short selfId, String configPath) throws IOException {
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
                    // Unico erro que pode acontecer aqui e parsing de numero.
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

    public static void main(String[] args){
        if(args.length != 1) {
            System.err.println("Uso: java projetoredes.app.SimpleUnicastApp <id>");
            // Sair com erro
            System.exit(1);
        }

        // Caminho para arquivo de config.
        // Vamos sempre rodar da raiz do projeto.
        final String CONFIG_PATH = "config/ucsaps.conf";

        try {
            short selfId = Short.parseShort(args[0]);

            // Usando try-with-resources nos garante que o close() vai ser chamado.
            try (SimpleUnicastApp app = new SimpleUnicastApp(selfId, CONFIG_PATH)){
                app.run();
            }
        } catch (NumberFormatException e){
            System.err.println("ERRO: O ID fornecido '" + args[0] + "' não é um número válido.");
        } catch (IllegalArgumentException e) {
            System.err.println("ERRO: " + e.getMessage());
        // Essa vem do protocolo
        } catch (IOException e) {
            System.err.println("ERRO de I/O ao iniciar o protocolo: " + e.getMessage());
            e.printStackTrace();
        }
    }
}