package sim.cda;

import java.io.File;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author Augie
 */
public class CalculateRedVarAvgPayoffPerStratPerProf {

    public static final boolean DEBUG = true;
    public static final int PLAYERS = 16;
    public static final int REPS = 5;
    public static final int UNITS = 10;
    public static final int V_MIN = 61;
    public static final int V_MAX = 260;

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            throw new Exception("Expecting 6 args: [samples directory] [avg unit vals file] [avg payoffs file] [coefficients file] [output json file] [output plain file]");
        }
        int argCount = 0;

        File inDir = new File(args[argCount++]);
        if (!inDir.exists()) {
            throw new Exception("Input directory does not exist.");
        }

        File unitValsFile = new File(args[argCount++]);
        if (!unitValsFile.exists()) {
            throw new Exception("Unit values file does not exist.");
        }

        File avgPayoffsFile = new File(args[argCount++]);
        if (!avgPayoffsFile.exists()) {
            throw new Exception("Avg payoffs file does not exist.");
        }

        File coeffFile = new File(args[argCount++]);
        if (!coeffFile.exists()) {
            throw new Exception("Variance reduction coefficients file does not exist.");
        }

        File outFile = new File(args[argCount++]);
        if (outFile.exists()) {
            throw new Exception("Output file already exists.");
        }

        File outPlainFile = new File(args[argCount++]);
        if (outPlainFile.exists()) {
            throw new Exception("Plain text output file already exists.");
        }

        // Read the unit values file
        double[] avgNormalizedUnitValue = new double[UNITS];
        {
            String unitValsString = FileUtils.readFileToString(unitValsFile);
            int count = 0;
            for (String unitVal : unitValsString.split("\n")) {
                avgNormalizedUnitValue[count++] = Double.valueOf(unitVal.trim());
            }
        }

        // Read the avgPayoffsFile
        // The double values are sorted corresponding to the sorted strategy names
        Map<String, Double[]> strategyProfileAvgs = new HashMap<String, Double[]>();
        {
            String avgPayoffsString = FileUtils.readFileToString(avgPayoffsFile);
            String[] split = avgPayoffsString.split("\n");
            int profileCount = Integer.valueOf(split[0]);
            int index = 1;
            for (int i = 0; i < profileCount; i++) {
                String profile = split[index];
                index += 2;
                int strategyCount = Integer.valueOf(split[index]);
                index++;
                Double[] payoffs = new Double[strategyCount];
                for (int j = 0; j < strategyCount; j++) {
                    payoffs[j] = Double.valueOf(split[index + 2]);
                    index += 4;
                }
                strategyProfileAvgs.put(profile, payoffs);
            }
        }

        // Read in the control variate coefficients file
        double[] conVarCoeffs = new double[UNITS];
        {
            String[] coeffs = FileUtils.readFileToString(coeffFile).split("\n");
            for (int i = 0; i < UNITS; i++) {
                conVarCoeffs[i] = Double.valueOf(coeffs[i].trim());
            }
        }

        // Accumulate reduced-variance profile payoffs
        Map<String, Integer> strategyProfileCounts = new HashMap<String, Integer>();
        Map<String, Double[]> strategyProfileSums = new HashMap<String, Double[]>();
        Map<String, List<String>> strategyProfileStrategies = new HashMap<String, List<String>>();
        Map<String, Map<String, Integer>> strategyProfileStrategyCounts = new HashMap<String, Map<String, Integer>>();
        for (String fileName : inDir.list()) {
            if (!fileName.toLowerCase().endsWith(".xml")) {
                continue;
            }
            File sampleFile = new File(inDir, fileName);

            // Strategy payoff sum map
            Map<String, Integer> strategyPayoffCounts = new HashMap<String, Integer>();
            Map<String, Double> strategyPayoffSums = new HashMap<String, Double>();

            // <repetition #, <agent ID, <price>>>
            Map<Integer, Map<Integer, List<Integer>>> transactionPrices = new HashMap<Integer, Map<Integer, List<Integer>>>();
            for (int r = 0; r < REPS; r++) {
                transactionPrices.put(r, new HashMap<Integer, List<Integer>>());
            }

            // Parse allocations file
            Builder parser = new Builder();
            Document allocDoc = parser.build(sampleFile);
            Element resultsEl = allocDoc.getRootElement();
            Element transactionsEl = resultsEl.getFirstChildElement("transactions");
            Elements repetitionEls = transactionsEl.getChildElements("repetition");
            for (int i = 0; i < repetitionEls.size(); i++) {
                Element repetitionEl = repetitionEls.get(i);

                // ID
                Element idEl = repetitionEl.getFirstChildElement("id");
                int id = Integer.valueOf(idEl.getValue()) - 1;

                // Read the transactions
                Elements transactionEls = repetitionEl.getChildElements("transaction");
                for (int j = 0; j < transactionEls.size(); j++) {
                    Element transactionEl = transactionEls.get(j);

                    // Buyer ID
                    Element buyerIDEl = transactionEl.getFirstChildElement("buyerID");
                    int buyerID = Integer.valueOf(buyerIDEl.getValue());

                    // Seller ID
                    Element sellerIDEl = transactionEl.getFirstChildElement("sellerID");
                    int sellerID = Integer.valueOf(sellerIDEl.getValue());

                    // Price
                    Element priceEl = transactionEl.getFirstChildElement("price");
                    int price = Integer.valueOf(priceEl.getValue());

                    // Add to transactions data structure
                    if (!transactionPrices.get(id).containsKey(buyerID)) {
                        transactionPrices.get(id).put(buyerID, new LinkedList<Integer>());
                    }
                    if (!transactionPrices.get(id).containsKey(sellerID)) {
                        transactionPrices.get(id).put(sellerID, new LinkedList<Integer>());
                    }
                    if (transactionPrices.get(id).get(buyerID).size() == UNITS
                            || transactionPrices.get(id).get(sellerID).size() == UNITS) {
                        continue;
                    }
                    transactionPrices.get(id).get(buyerID).add(price);
                    transactionPrices.get(id).get(sellerID).add(price);
                }
            }

            // What is the profile ID for this sample?
            List<String> strategies = new LinkedList<String>();
            Map<String, Integer> strategyCount = new HashMap<String, Integer>();
            Element scoresEl = resultsEl.getFirstChildElement("scores");
            String profileID = null;
            {
                Elements agentEls = scoresEl.getChildElements("agent");
                for (int i = 0; i < agentEls.size(); i++) {
                    Element agentEl = agentEls.get(i);

                    // Strategy of this agent
                    Element strategyEl = agentEl.getFirstChildElement("strategy");
                    String strategy = strategyEl.getValue();
                    if (!strategies.contains(strategy)) {
                        strategies.add(strategy);
                        strategyCount.put(strategy, 1);
                    } else {
                        strategyCount.put(strategy, strategyCount.get(strategy) + 1);
                    }
                }
                Collections.sort(strategies);
                String[] profileBuilder = new String[strategies.size() * 2];
                int count = 0;
                for (String strategy : strategies) {
                    profileBuilder[count++] = String.valueOf(strategyCount.get(strategy) / 4);
                    profileBuilder[count++] = strategy;
                }
                profileID = Utils.join(profileBuilder, " ");
            }

            Elements agentEls = scoresEl.getChildElements("agent");
            for (int i = 0; i < agentEls.size(); i++) {
                Element agentEl = agentEls.get(i);

                // ID
                Element idEl = agentEl.getFirstChildElement("ID");
                int id = Integer.valueOf(idEl.getValue());

                // Strategy of this agent
                Element strategyEl = agentEl.getFirstChildElement("strategy");
                String strategy = strategyEl.getValue();

                // Values
                LinkedList<Integer> values = new LinkedList<Integer>();
                Element valuesEl = agentEl.getFirstChildElement("values");
                Elements valueEls = valuesEl.getChildElements("value");
                for (int j = 0; j < valueEls.size(); j++) {
                    Element valueEl = valueEls.get(j);
                    values.add(Integer.valueOf(valueEl.getValue()));
                }

                // Is this agent a buyer?
                boolean isBuyer = (values.getFirst().intValue() > values.getLast().intValue());

                // Fetch the avg payoff for this strat in this profile
                double avgPayoffForThisStrategyInThisProfile = strategyProfileAvgs.get(profileID)[strategies.indexOf(strategy)];

                // Reduce the variance payoff for this sample
                double reducedVarianceScore = avgPayoffForThisStrategyInThisProfile;

                // For each of the units
                for (int u = 0; u < UNITS; u++) {
                    double normalizedValue;
                    if (isBuyer) {
                        normalizedValue = values.get(u) - V_MIN;
                    } else {
                        normalizedValue = V_MAX - values.get(u);
                    }
                    reducedVarianceScore -= conVarCoeffs[u] * (normalizedValue - avgNormalizedUnitValue[u]);
                }

                // Update strategy sums and counts
                if (!strategyPayoffCounts.containsKey(strategy)) {
                    strategyPayoffCounts.put(strategy, 0);
                }
                if (!strategyPayoffSums.containsKey(strategy)) {
                    strategyPayoffSums.put(strategy, 0d);
                }
                strategyPayoffCounts.put(strategy, strategyPayoffCounts.get(strategy) + 1);
                strategyPayoffSums.put(strategy, strategyPayoffSums.get(strategy) + reducedVarianceScore);
            }
            strategyProfileStrategies.put(profileID, strategies);
            Map<String, Integer> profileStrategyCounts = new HashMap<String, Integer>();
            for (String strategy : strategies) {
                profileStrategyCounts.put(strategy, strategyPayoffCounts.get(strategy) / 4);
            }
            strategyProfileStrategyCounts.put(profileID, profileStrategyCounts);

            // Average the payoffs per strategy in this profile
            Map<String, Double> strategyPayoffAvgs = new HashMap<String, Double>();
            for (String strategy : strategyPayoffCounts.keySet()) {
                strategyPayoffAvgs.put(strategy, strategyPayoffSums.get(strategy) / (double) strategyPayoffCounts.get(strategy));
            }

            // Increment the strategy profile counts
            if (!strategyProfileCounts.containsKey(profileID)) {
                strategyProfileCounts.put(profileID, 0);
            }
            if (!strategyProfileSums.containsKey(profileID)) {
                Double[] sums = new Double[strategyPayoffCounts.size()];
                Arrays.fill(sums, 0d);
                strategyProfileSums.put(profileID, sums);
            }
            strategyProfileCounts.put(profileID, strategyProfileCounts.get(profileID) + 1);
            Double[] sums = strategyProfileSums.get(profileID);
            for (String strategy : strategyPayoffAvgs.keySet()) {
                sums[strategies.indexOf(strategy)] += strategyPayoffAvgs.get(strategy);
            }
            strategyProfileSums.put(profileID, sums);
        }

        // Calculate average strategy payoffs per profile
        Map<String, Double[]> redVarStrategyProfileAvgs = new HashMap<String, Double[]>();
        for (String profileID : strategyProfileSums.keySet()) {
            Double[] sums = strategyProfileSums.get(profileID);
            Double[] avgs = new Double[sums.length];
            for (int i = 0; i < avgs.length; i++) {
                avgs[i] = sums[i] / (double) strategyProfileCounts.get(profileID);
            }
            redVarStrategyProfileAvgs.put(profileID, avgs);
        }

        // Calculate standard deviations
        Map<String, Double[]> strategyProfileStdDevSums = new HashMap<String, Double[]>();
        for (String fileName : inDir.list()) {
            if (!fileName.toLowerCase().endsWith(".xml")) {
                continue;
            }
            File sampleFile = new File(inDir, fileName);

            // Strategy payoff sum map
            Map<String, Integer> strategyPayoffCounts = new HashMap<String, Integer>();
            Map<String, Double> strategyPayoffSums = new HashMap<String, Double>();

            // Parse allocations file
            Builder parser = new Builder();
            Document allocDoc = parser.build(sampleFile);
            Element resultsEl = allocDoc.getRootElement();

            // What is the profile ID for this sample?
            List<String> strategies = new LinkedList<String>();
            Map<String, Integer> strategyCount = new HashMap<String, Integer>();
            Element scoresEl = resultsEl.getFirstChildElement("scores");
            String profileID = null;
            {
                Elements agentEls = scoresEl.getChildElements("agent");
                for (int i = 0; i < agentEls.size(); i++) {
                    Element agentEl = agentEls.get(i);

                    // Strategy of this agent
                    Element strategyEl = agentEl.getFirstChildElement("strategy");
                    String strategy = strategyEl.getValue();
                    if (!strategies.contains(strategy)) {
                        strategies.add(strategy);
                        strategyCount.put(strategy, 1);
                    } else {
                        strategyCount.put(strategy, strategyCount.get(strategy) + 1);
                    }
                }
                Collections.sort(strategies);
                String[] profileBuilder = new String[strategies.size() * 2];
                int count = 0;
                for (String strategy : strategies) {
                    profileBuilder[count++] = String.valueOf(strategyCount.get(strategy) / 4);
                    profileBuilder[count++] = strategy;
                }
                profileID = Utils.join(profileBuilder, " ");
            }

            Elements agentEls = scoresEl.getChildElements("agent");
            for (int i = 0; i < agentEls.size(); i++) {
                Element agentEl = agentEls.get(i);

                // Strategy of this agent
                Element strategyEl = agentEl.getFirstChildElement("strategy");
                String strategy = strategyEl.getValue();

                // Values
                LinkedList<Integer> values = new LinkedList<Integer>();
                Element valuesEl = agentEl.getFirstChildElement("values");
                Elements valueEls = valuesEl.getChildElements("value");
                for (int j = 0; j < valueEls.size(); j++) {
                    Element valueEl = valueEls.get(j);
                    values.add(Integer.valueOf(valueEl.getValue()));
                }

                // Is this agent a buyer?
                boolean isBuyer = (values.getFirst().intValue() > values.getLast().intValue());

                // Fetch the avg payoff for this strat in this profile
                double avgPayoffForThisStrategyInThisProfile = strategyProfileAvgs.get(profileID)[strategies.indexOf(strategy)];

                // Reduce the variance payoff for this sample
                double reducedVarianceScore = avgPayoffForThisStrategyInThisProfile;

                // For each of the units
                for (int u = 0; u < UNITS; u++) {
                    double normalizedValue;
                    if (isBuyer) {
                        normalizedValue = values.get(u) - V_MIN;
                    } else {
                        normalizedValue = V_MAX - values.get(u);
                    }
                    reducedVarianceScore -= conVarCoeffs[u] * (normalizedValue - avgNormalizedUnitValue[u]);
                }

                // Update strategy sums and counts
                if (!strategyPayoffCounts.containsKey(strategy)) {
                    strategyPayoffCounts.put(strategy, 0);
                }
                if (!strategyPayoffSums.containsKey(strategy)) {
                    strategyPayoffSums.put(strategy, 0d);
                }
                strategyPayoffCounts.put(strategy, strategyPayoffCounts.get(strategy) + 1);
                strategyPayoffSums.put(strategy, strategyPayoffSums.get(strategy) + reducedVarianceScore);
            }

            // Average the payoffs per strategy in this profile
            Map<String, Double> strategyPayoffAvgs = new HashMap<String, Double>();
            for (String strategy : strategyPayoffCounts.keySet()) {
                strategyPayoffAvgs.put(strategy, strategyPayoffSums.get(strategy) / (double) strategyPayoffCounts.get(strategy));
            }

            // About to add to the sum for calculating std devs
            if (!strategyProfileStdDevSums.containsKey(profileID)) {
                Double[] newArray = new Double[strategies.size()];
                Arrays.fill(newArray, 0d);
                strategyProfileStdDevSums.put(profileID, newArray);
            }
            // Utilize average payoff per strategy for this profile and the total average payoff per strategy for all profiles
            Double[] stdDevSums = strategyProfileStdDevSums.get(profileID);
            Double[] totalProfileAvgs = redVarStrategyProfileAvgs.get(profileID);
            for (int i = 0; i < strategies.size(); i++) {
                stdDevSums[i] += Math.pow(strategyPayoffAvgs.get(strategies.get(i)) - totalProfileAvgs[i], 2);
            }
            strategyProfileStdDevSums.put(profileID, stdDevSums);
        }

        // Calculate the standard deviations for each strategy in each profile
        Map<String, Double[]> strategyProfileStdDevs = new HashMap<String, Double[]>();
        for (String profileID : strategyProfileStdDevSums.keySet()) {
            Double[] sums = strategyProfileStdDevSums.get(profileID);
            Double[] stdDevs = new Double[sums.length];
            for (int i = 0; i < sums.length; i++) {
                stdDevs[i] = Math.sqrt(sums[i] / (double) strategyProfileCounts.get(profileID));
            }
            strategyProfileStdDevs.put(profileID, stdDevs);
        }

        // Print the resulting averages and std devs
        for (String profileID : strategyProfileAvgs.keySet()) {
            String avgPayoffsString = "";
            Double[] avgPayoffs = strategyProfileAvgs.get(profileID);
            Double[] stdDevs = strategyProfileStdDevs.get(profileID);
            for (int i = 0; i < avgPayoffs.length; i++) {
                avgPayoffsString = avgPayoffsString + avgPayoffs[i] + " (" + stdDevs[i] + ") ";
            }
            System.out.println(profileID + ": " + avgPayoffsString);
        }

        // Write out the game JSON
        PrintStream out = null;
        try {
            out = new PrintStream(FileUtils.openOutputStream(outFile));
            // Header
            out.print("{\"id\":\"329509325323\",");
            out.print("\"name\":\"CDA\",");
            out.print("\"simulator_fullname\":\"CDA-2.11\",");
            out.print("\"parameter_hash\":{\"foo\":\"bar\"},");
            out.print("\"roles\":[{\"name\":\"ALL\",\"count\":4,\"strategies\":[\"AA\",\"GD\",\"GDX\",\"KAPLAN\",\"RB\",\"ZI\",\"ZIBTQ\",\"ZIP\"]}],");
            out.print("\"profiles\":[");
            // Profiles
            int id = 0;
            for (String profileID : strategyProfileCounts.keySet()) {
                out.print("{\"id\":\"" + (id++) + "\",");
                out.print("\"sample_count\":" + strategyProfileCounts.get(profileID) + ",");
                out.print("\"roles\":[");
                out.print("{\"name\":\"ALL\",");
                out.print("\"strategies\":[");
                for (int i = 0; i < strategyProfileStrategies.get(profileID).size(); i++) {
                    String strategy = strategyProfileStrategies.get(profileID).get(i);
                    out.print("{\"name\":\"" + strategy + "\",");
                    out.print("\"count\":" + strategyProfileStrategyCounts.get(profileID).get(strategy) + ",");
                    out.print("\"payoff\":" + redVarStrategyProfileAvgs.get(profileID)[i] + ",");
                    out.print("\"payoff_std\":" + strategyProfileStdDevs.get(profileID)[i]);
                    out.print("}");
                    if (i < strategyProfileStrategies.get(profileID).size() - 1) {
                        out.print(",");
                    }
                }
                out.print("]");
                out.print("}");
                out.print("]");
                out.print("}");
                if (id != strategyProfileCounts.size()) {
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

        // Build up the output file
        StringBuilder sb = new StringBuilder();
        // # Profiles
        sb.append(strategyProfileCounts.size());
        sb.append("\n");
        for (String profileID : strategyProfileCounts.keySet()) {
            // Profile ID
            sb.append(profileID);
            sb.append("\n");
            // Sample count
            sb.append(strategyProfileCounts.get(profileID));
            sb.append("\n");
            // Number of strategies
            sb.append(strategyProfileStrategies.get(profileID).size());
            sb.append("\n");
            for (int i = 0; i < strategyProfileStrategies.get(profileID).size(); i++) {
                String strategy = strategyProfileStrategies.get(profileID).get(i);
                // Name
                sb.append(strategy);
                sb.append("\n");
                // Count
                sb.append(strategyProfileStrategyCounts.get(profileID).get(strategy));
                sb.append("\n");
                // Payoff
                sb.append(strategyProfileAvgs.get(profileID)[i]);
                sb.append("\n");
                // Std Dev
                sb.append(strategyProfileStdDevs.get(profileID)[i]);
                sb.append("\n");
            }
        }
        // Write out the results file
        FileUtils.writeStringToFile(outPlainFile, sb.toString());
    }

    public static void println(String msg) {
        if (DEBUG) {
            System.out.println(msg);
        }
    }
}
