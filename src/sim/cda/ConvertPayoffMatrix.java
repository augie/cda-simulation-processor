package sim.cda;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.HashMap;
import java.util.Map;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Augie
 */
public class ConvertPayoffMatrix {

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new Exception("Expected 2 args: [in file] [out file]");
        }
        int argCount = 0;

        File inFile = new File(args[argCount++]);
        if (!inFile.exists()) {
            throw new Exception("Input file does not exist.");
        }

        File outFile = new File(args[argCount++]);
        if (outFile.exists()) {
            throw new Exception("Output file already exists.");
        }

        // <String profile, <String strategy, Double payoff>>
        Map<String, Map<String, Double>> originalGame = new HashMap<String, Map<String, Double>>();
        Map<String, Map<String, Integer>> originalGameStrategyCounts = new HashMap<String, Map<String, Integer>>();

        // Open the input file
        BufferedReader in = null;
        try {
            in = new BufferedReader(new InputStreamReader(FileUtils.openInputStream(inFile)));
            // Map at the beginning
            //  <id, strategy name>
            Map<Integer, String> strategyMap = new HashMap<Integer, String>();
            // Just hard code the map
            strategyMap.put(0, "GD");
            strategyMap.put(1, "GDX");
            strategyMap.put(2, "KAPLAN");
            strategyMap.put(3, "ZI");
            strategyMap.put(4, "ZIP");
            strategyMap.put(5, "ZIBTQ");
            strategyMap.put(6, "RB");
            // Throw away the header
            for (int i = 0; i < 18; i++) {
                in.readLine();
            }
            // Read in the profile payoffs
            String line = null;
            READLINE:
            while ((line = in.readLine()) != null) {
                line = line.trim();
                // End of file
                if (line.equals("];")) {
                    break;
                }
                // Clean up white space
                while (line.contains("  ")) {
                    line = line.replace("  ", " ");
                }
                // Split the line
                String[] split = line.split(" ");
                // First 4 are strategies
                String[] strategies = new String[4];
                // For later
                Map<String, Double> payoffMap = new HashMap<String, Double>();
                // Convert strategy # to name
                for (int i = 0; i < 4; i++) {
                    // Strategies
                    int strategyID = Integer.valueOf(split[i]);
                    if (!strategyMap.containsKey(strategyID)) {
                        continue READLINE;
                    }
                    strategies[i] = strategyMap.get(strategyID);
                    // Payoffs
                    payoffMap.put(strategies[i], Double.valueOf(split[i + 4]));
                }

                // How many of each strategy are there?
                Map<String, Integer> strategyCount = new HashMap<String, Integer>();
                for (int i = 0; i < strategies.length; i++) {
                    if (!strategyCount.containsKey(strategies[i])) {
                        strategyCount.put(strategies[i], 1);
                    } else {
                        strategyCount.put(strategies[i], strategyCount.get(strategies[i]) + 1);
                    }
                }
                
                // What is the strategy profile?
                String[] profileBuilder = new String[8];
                int count = 0;
                for (String strategy : strategies) {
                    profileBuilder[count++] = String.valueOf(strategyCount.get(strategy));
                    profileBuilder[count++] = strategy;
                }
                String profileID = Utils.join(profileBuilder, " ");
                
                // Save the strategy count
                originalGameStrategyCounts.put(profileID, strategyCount);
                
                // Save the payoffs
                originalGame.put(profileID, payoffMap);
            }
        } finally {
            IOUtils.closeQuietly(in);
        }

        // Write out the game JSON
        PrintStream out = null;
        try {
            out = new PrintStream(FileUtils.openOutputStream(outFile));
            // Header
            out.print("{\"id\":\"329509325323\",");
            out.print("\"name\":\"CDA-Schvartzman\",");
            out.print("\"simulator_fullname\":\"Schvartzman\",");
            out.print("\"parameter_hash\":{\"foo\":\"bar\"},");
            out.print("\"roles\":[{\"name\":\"ALL\",\"count\":4,\"strategies\":[\"GD\",\"GDX\",\"KAPLAN\",\"RB\",\"ZI\",\"ZIBTQ\",\"ZIP\"]}],");
            out.print("\"profiles\":[");
            // Profiles
            int id = 0;
            for (String profileID : originalGame.keySet()) {
                out.print("{\"id\":\"" + (id++) + "\",");
                out.print("\"sample_count\":1,");
                out.print("\"roles\":[");
                out.print("{\"name\":\"ALL\",");
                out.print("\"strategies\":[");
                int i = 0;
                for (String strategy : originalGame.get(profileID).keySet()) {
                    out.print("{\"name\":\"" + strategy + "\",");
                    out.print("\"count\":" + originalGameStrategyCounts.get(profileID).get(strategy) + ",");
                    out.print("\"payoff\":" + originalGame.get(profileID).get(strategy) + ",");
                    out.print("\"payoff_std\":0");
                    out.print("}");
                    if (i++ < originalGame.get(profileID).size() - 1) {
                        out.print(",");
                    }
                }
                out.print("]");
                out.print("}");
                out.print("]");
                out.print("}");
                if (id != originalGame.size()) {
                    out.print(",");
                }
            }
            // Footer
            out.print("]");
            out.print("}");
        } finally {
            try {
                out.flush();
            } catch (Exception e) {
            }
            IOUtils.closeQuietly(out);
        }
    }
}
