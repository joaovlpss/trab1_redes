package projetoredes.unicast;

import projetoredes.unicast.UCSAP;
import projetoredes.unicast.UnicastServiceInterface;
import projetoredes.unicast.UnicastServiceUserInterface;

import java.util.Map;
import java.util.HashMap;

public class UnicastProtocol implements UnicastServiceInterface {

    private static final int MAX_PDU_SIZE = 512;

    private final UnicastServiceUserInterface user;

    // Mapa de IDs - Enderecos
    private final Map<Short, UCSAP> knownEntities = new HashMap<>();

    // Mapa de Enderecos - IDs
    private final Map<UCSAP, Short> knownAddresses = new HashMap<>();

    

    public UnicastProtocol(UnicastServiceUserInterface user) {
        this.user = user;
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

        return true;
    }

}