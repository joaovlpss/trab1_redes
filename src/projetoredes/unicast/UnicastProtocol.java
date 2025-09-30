package projetoredes.unicast;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import projetoredes.utils.Utils;

public class UnicastProtocol implements UnicastServiceInterface, AutoCloseable {

    private static final int MAX_PDU_SIZE = 1024;
    private final DatagramSocket socket;
    private final UnicastServiceUserInterface user;

    // Mapa de IDs - Enderecos
    private final Map<Short, UCSAP> knownEntities = new HashMap<>();

    // Mapa de Enderecos - IDs
    private final Map<SocketAddress, Short> knownAddresses = new HashMap<>();

    private final Thread receiverThread;


    public UnicastProtocol(UnicastServiceUserInterface user, short userId, String configPath) 
    throws IOException, IllegalArgumentException {
        if (user == null) {
            throw new IllegalArgumentException("O usuario do serviço nao pode ser nulo.");
        }
        this.user = user;

        List<UCSAP> addresses = Utils.loadConfiguration(configPath);
        for (UCSAP ucsap : addresses) {
            knownEntities.put(ucsap.id(), ucsap);
            try {
                InetAddress address = InetAddress.getByName(ucsap.host());
                SocketAddress socketAddr = new InetSocketAddress(address, ucsap.port()); 
                knownAddresses.put(socketAddr, ucsap.id());
            } catch (UnknownHostException e) {
                System.err.println("Aviso: Host desconhecido " + ucsap.host() + ". Ignorando esta entrada.");
            }
        }

        UCSAP selfDescription = knownEntities.get(userId);
        if (selfDescription == null) {
            throw new IllegalArgumentException("ID " + userId + " não encontrado na configuração.");
        }

        try {
            this.socket = new DatagramSocket(selfDescription.port());
        } catch (SocketException e) {
            throw new IOException("Não foi possível criar ou vincular o socket na porta " + selfDescription.port(), e);
        }

        this.receiverThread = new Thread(this::startReceiverThread);
        this.receiverThread.start();
    }

    @Override
    public boolean UPDataReq(short destinationId, String data){
        UCSAP destination = knownEntities.get(destinationId);

        if (destination == null) {
            System.err.println("ERRO: Destino com ID " + destinationId + " desconhecido.");
            return false;
        }

        String pduString = "UPDREQPDU " + data.length() + " " + data;
        byte[] buffer = pduString.getBytes();

        if (buffer.length > MAX_PDU_SIZE) {
            System.err.println("ERRO: Mensagem muito longa para ser enviada.");
            return false;
        }

        try {
            InetAddress address = InetAddress.getByName(destination.host());
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, address, destination.port());
            socket.send(packet);
            return true;
        } catch (IOException e) {
            System.err.println("Erro de I/O ao enviar pacote: " + e.getMessage());
            return false;
        }
    }

    private void startReceiverThread() {
        byte[] buffer = new byte[MAX_PDU_SIZE];
        while (!socket.isClosed()){
            try{
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                // Bloqueia ate receber pacote
                socket.receive(packet);

                // Identificar o remetente pelo mapa de enderecos
                SocketAddress senderAddress = packet.getSocketAddress();
                Short sourceId = knownAddresses.get(senderAddress);

                if(sourceId == null) {
                    System.err.println("Aviso: Pacote recebido de endereço desconhecido " + senderAddress + ". Ignorando.");
                    continue;
                }

                // Processar a PDU recebida
                String receivedData = new String(packet.getData(), 0, packet.getLength());
                processPDU(sourceId, receivedData);
            } catch (SocketException e) {
                System.err.println("Socket fechado inesperadamente: " + e.getMessage());
                break;
            } catch (IOException e) {
                System.err.println("Erro de I/O ao receber pacote: " + e.getMessage());
            }
        }
    }

    private void processPDU(short sourceId, String pdu) {
        String[] parts = pdu.split(" ", 3);

        if (parts.length == 3 && parts[0].equals("UPDREQPDU")) {
            try {
                int declaredLength = Integer.parseInt(parts[1]);
                String data = parts[2];

                if (data.length() != declaredLength) {
                    System.err.println("Aviso: Comprimento declarado (" + declaredLength + ") não corresponde ao comprimento real (" + data.length() + "). Ignorando PDU.");
                    return;
                }

                user.UPDataInd(sourceId, data);
            } catch (NumberFormatException e) {
                System.err.println("Aviso: Comprimento inválido na PDU recebida. Ignorando.");
            }
        } else {
            System.err.println("ERRO: PDU mal formatada recebida de ID " + sourceId + ". Ignorando.");
        }
    }

    @Override
    public void close() {
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        try {
            if (receiverThread != null && receiverThread.isAlive()) {
                receiverThread.join();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println("Thread de recepção interrompida durante o fechamento.");
        }
    }
}