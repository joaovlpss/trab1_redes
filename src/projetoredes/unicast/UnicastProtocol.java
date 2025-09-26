package projetoredes.unicast;

import java.io.IOException;
import java.net.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import projetoredes.utils.Utils;

public class UnicastProtocol implements UnicastServiceInterface {

    private static final int MAX_PDU_SIZE = 512;
    private final DatagramSocket socket;
    private final UnicastServiceUserInterface user;
    // Mapa de IDs - Enderecos
    private final Map<Short, UCSAP> knownEntities = new HashMap<>();
    // Mapa de Enderecos - IDs
    private final Map<DatagramSocket, Short> knownAddresses = new HashMap<>();

    

    public UnicastProtocol(UnicastServiceUserInterface user, short userId, String configPath) 
    throws IOException, IllegalArgumentException {
        if (user == null) {
            throw new IllegalArgumentException("O usuario do serviço nao pode ser nulo.");
        }
        this.user = user;

        List<UCSAP> addresses = Utils.loadConfiguration(configPath);
        for (UCSAP address : addresses) {
            knownEntities.put(address.id(), address);
            knownAddresses.put(new DatagramSocket(address.port()), address.id());
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

}