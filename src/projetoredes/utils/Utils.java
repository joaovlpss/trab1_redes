package projetoredes.utils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
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
}
