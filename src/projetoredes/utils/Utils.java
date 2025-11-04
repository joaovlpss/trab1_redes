package projetoredes.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import projetoredes.unicast.UCSAP;

public class Utils {
    public static List<UCSAP> loadConfiguration(String configPath) throws IOException {

        ArrayList<UCSAP> returnList = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(configPath))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue; // Ignora linhas vazias ou comentarios
                }

                String[] parts = line.split("\\s+");
                if (parts.length == 3) {
                    short id = Short.parseShort(parts[0]);
                    String host = parts[1];
                    int port = Integer.parseInt(parts[2]);

                    UCSAP ucsap = new UCSAP(id, host, port);
                    returnList.add(ucsap);
                }
            }
        } catch (NumberFormatException e) {
            throw new IOException("Erro de formato no arquivo de configuração.", e);
        }

        return returnList;
    }
    
    public static Map<Short, Map<Short, Integer>> loadTopology(String ripConfigPath){
        HashMap<Short, Map<Short, Integer>> topology = new HashMap<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(ripConfigPath))) {
            String line;

            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty() || line.startsWith("#")) {
                    continue;
                }

                String[] parts = line.split("\\s+");
                if (parts.length == 3) {
                    short id1 = Short.parseShort(parts[0]);
                    short id2 = Short.parseShort(parts[1]);
                    int cost = Integer.parseInt(parts[2]);

                    topology.putIfAbsent(id1, new HashMap<>());
                    topology.putIfAbsent(id2, new HashMap<>());

                    topology.get(id1).put(id2, cost);
                    topology.get(id2).put(id1, cost);
                } else {
                    throw new IOException("Formato inválido na linha: " + line);
                }
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Erro ao carregar a topologia: " + e.getMessage());
            return null;
        }

        return topology;
    }
}
