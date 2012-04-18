package sim.cda;

import java.io.File;
import java.util.ArrayList;
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

/**
 *
 * @author Augie
 */
public class CalculateAveragePayoffPerStratPerProf {

    public static final int REPS = 5;
    public static final int UNITS = 10;
    public static final int V_MIN = 61;
    public static final int V_MAX = 260;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new Exception("Expecting 2 args: [samples directory] [output file]");
        }

        File samplesDir = new File(args[0]);
        if (!samplesDir.exists()) {
            throw new Exception("Samples directory does not exist.");
        }

        File outputFile = new File(args[1]);
        if (outputFile.exists()) {
            throw new Exception("Output file already exists.");
        }

        // Accumulate reduced-variance profile payoffs
        Map<String, Integer> strategyProfileCounts = new HashMap<String, Integer>();
        Map<String, Double[]> strategyProfileSums = new HashMap<String, Double[]>();
        Map<String, List<String>> strategyProfileStrategies = new HashMap<String, List<String>>();
        Map<String, Map<String, Integer>> strategyProfileStrategyCounts = new HashMap<String, Map<String, Integer>>();
        for (String fileName : samplesDir.list()) {
            if (!fileName.toLowerCase().endsWith(".xml")) {
                continue;
            }
            File sampleFile = new File(samplesDir, fileName);

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
            Element scoresEl = resultsEl.getFirstChildElement("scores");
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

                // Construct the total payoff for each repetition
                double[] totalPayoffPerRepetition = new double[REPS];
                int[][] payoffPerUnitPerRepetition = new int[REPS][UNITS];
                Arrays.fill(totalPayoffPerRepetition, 0);
                for (int r = 0; r < REPS; r++) {
                    Arrays.fill(payoffPerUnitPerRepetition[r], 0);
                    // No transactions for this agent in this repetition
                    if (!transactionPrices.get(r).containsKey(id)) {
                        continue;
                    }
                    List<Integer> prices = transactionPrices.get(r).get(id);
                    for (int p = 0; p < prices.size(); p++) {
                        int price = prices.get(p);
                        if (isBuyer) {
                            payoffPerUnitPerRepetition[r][p] = values.get(p) - price;
                        } else {
                            payoffPerUnitPerRepetition[r][p] = price - values.get(p);
                        }
                        totalPayoffPerRepetition[r] += payoffPerUnitPerRepetition[r][p];
                    }
                }

                // Calculate the total average payoff for this agent
                double avgPayoffPerRepetition = 0;
                for (int r = 0; r < REPS; r++) {
                    avgPayoffPerRepetition += totalPayoffPerRepetition[r];
                }
                avgPayoffPerRepetition /= (double) REPS;

                // Reduce the variance of this sample
                double reducedVarianceScore = avgPayoffPerRepetition;

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

            // What is the strategy profile?
            ArrayList<String> strategies = new ArrayList<String>();
            strategies.addAll(strategyPayoffCounts.keySet());
            Collections.sort(strategies);
            String[] profileBuilder = new String[strategies.size() * 2];
            int count = 0;
            for (String strategy : strategies) {
                profileBuilder[count++] = String.valueOf(strategyPayoffCounts.get(strategy) / 4);
                profileBuilder[count++] = strategy;
            }
            String profileID = Utils.join(profileBuilder, " ");
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
        Map<String, Double[]> strategyProfileAvgs = new HashMap<String, Double[]>();
        for (String profileID : strategyProfileSums.keySet()) {
            Double[] sums = strategyProfileSums.get(profileID);
            Double[] avgs = new Double[sums.length];
            for (int i = 0; i < avgs.length; i++) {
                avgs[i] = sums[i] / (double) strategyProfileCounts.get(profileID);
            }
            strategyProfileAvgs.put(profileID, avgs);
        }

        // Calculate standard deviations
        Map<String, Double[]> strategyProfileStdDevSums = new HashMap<String, Double[]>();
        for (String fileName : samplesDir.list()) {
            if (!fileName.toLowerCase().endsWith(".xml")) {
                continue;
            }
            File sampleFile = new File(samplesDir, fileName);

            // Strategy payoff sum map
            Map<String, Integer> strategyPayoffCounts = new HashMap<String, Integer>();
            Map<String, Double> strategyPayoffSums = new HashMap<String, Double>();

            // Parse allocations file
            Builder parser = new Builder();
            Document allocDoc = parser.build(sampleFile);
            Element resultsEl = allocDoc.getRootElement();
            Element scoresEl = resultsEl.getFirstChildElement("scores");
            Elements agentEls = scoresEl.getChildElements("agent");
            for (int i = 0; i < agentEls.size(); i++) {
                Element agentEl = agentEls.get(i);

                // Strategy of this agent
                Element strategyEl = agentEl.getFirstChildElement("strategy");
                String strategy = strategyEl.getValue();

                // Grab the payoff for this agent
                Element scoreEl = agentEl.getFirstChildElement("score");
                double score = Double.valueOf(scoreEl.getValue());

                // Update strategy sums and counts
                if (!strategyPayoffCounts.containsKey(strategy)) {
                    strategyPayoffCounts.put(strategy, 0);
                }
                if (!strategyPayoffSums.containsKey(strategy)) {
                    strategyPayoffSums.put(strategy, 0d);
                }
                strategyPayoffCounts.put(strategy, strategyPayoffCounts.get(strategy) + 1);
                strategyPayoffSums.put(strategy, strategyPayoffSums.get(strategy) + score);
            }

            // What is the strategy profile?
            ArrayList<String> strategies = new ArrayList<String>();
            strategies.addAll(strategyPayoffCounts.keySet());
            Collections.sort(strategies);
            String[] profileBuilder = new String[strategies.size() * 2];
            int count = 0;
            for (String strategy : strategies) {
                profileBuilder[count++] = String.valueOf(strategyPayoffCounts.get(strategy) / 4);
                profileBuilder[count++] = strategy;
            }
            String profileID = Utils.join(profileBuilder, " ");

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
            Double[] totalProfileAvgs = strategyProfileAvgs.get(profileID);
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
        FileUtils.writeStringToFile(outputFile, sb.toString());
    }
}
