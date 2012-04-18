package sim.cda;

import java.io.File;
import java.util.ArrayList;
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
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.LUDecomposition;
import org.apache.commons.math3.linear.RealMatrix;

/**
 *
 * @author Augie
 */
public class CalculateVarianceReductionRatio {

    public static final int PLAYERS = 16;
    public static final int REPS = 5;
    public static final int UNITS = 10;
    public static final int V_MIN = 61;
    public static final int V_MAX = 260;

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            throw new Exception("Expecting 5 args: [samples directory] [avg unit vals file] [avg payoffs file] [coefficients file] [reduced variance payoffs file]");
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

        File redVarPayoffsFile = new File(args[argCount++]);
        if (!redVarPayoffsFile.exists()) {
            throw new Exception("Reduced variance payoffs file does not exist.");
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

        // Read the reduced-variance payoffs for each profile
        // The double values are sorted corresponding to the sorted strategy names
        Map<String, Double[]> redVarStrategyProfileAvgs = new HashMap<String, Double[]>();
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
                redVarStrategyProfileAvgs.put(profile, payoffs);
            }
        }

        // K is the total number of agent samples
        double K = 0;

        // Covariance matrix
        double[][] SigmaData = new double[UNITS][UNITS];
        // Variance array
        double[] sigmaData = new double[UNITS];

        // Read all of the allocations to build up the data set
        Map<String, Map<String, ArrayList<Double>>> profileStrategyPayoffs = new HashMap<String, Map<String, ArrayList<Double>>>();
        ArrayList<Double> allPayoffs = new ArrayList<Double>();
        for (String sampleFileLoc : inDir.list()) {
            // Open the sample file
            if (!sampleFileLoc.toLowerCase().endsWith(".xml")) {
                continue;
            }
            File sampleFile = new File(inDir, sampleFileLoc);

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
            if (!profileStrategyPayoffs.containsKey(profileID)) {
                profileStrategyPayoffs.put(profileID, new HashMap<String, ArrayList<Double>>());
            }

            Elements agentEls = scoresEl.getChildElements("agent");
            for (int i = 0; i < agentEls.size(); i++) {
                K++;
                Element agentEl = agentEls.get(i);

                // ID
                Element idEl = agentEl.getFirstChildElement("ID");
                int id = Integer.valueOf(idEl.getValue());

                // Strategy of this agent
                Element strategyEl = agentEl.getFirstChildElement("strategy");
                String strategy = strategyEl.getValue();
                if (!profileStrategyPayoffs.get(profileID).containsKey(strategy)) {
                    profileStrategyPayoffs.get(profileID).put(strategy, new ArrayList<Double>());
                }

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
                for (int r = 0; r < REPS; r++) {
                    // No transactions for this agent in this repetition
                    if (!transactionPrices.get(r).containsKey(id)) {
                        continue;
                    }
                    List<Integer> prices = transactionPrices.get(r).get(id);
                    for (int p = 0; p < prices.size(); p++) {
                        int price = prices.get(p);
                        if (isBuyer) {
                            totalPayoffPerRepetition[r] += values.get(p) - price;
                        } else {
                            totalPayoffPerRepetition[r] += price - values.get(p);
                        }
                    }
                }

                // Calculate the total average payoff for this agent
                double avgPayoffPerRepetition = 0;
                for (int r = 0; r < REPS; r++) {
                    avgPayoffPerRepetition += totalPayoffPerRepetition[r];
                }
                avgPayoffPerRepetition /= (double) REPS;
                profileStrategyPayoffs.get(profileID).get(strategy).add(avgPayoffPerRepetition);
                allPayoffs.add(avgPayoffPerRepetition);

                // Fetch the avg payoff for this strat in this profile
                double avgPayoffForThisStrategyInThisProfile = strategyProfileAvgs.get(profileID)[strategies.indexOf(strategy)];

                // Add to the sigma data
                for (int u = 0; i < UNITS; i++) {
                    double normalizedValue;
                    if (isBuyer) {
                        normalizedValue = values.get(u) - V_MIN;
                    } else {
                        normalizedValue = V_MAX - values.get(u);
                    }
                    sigmaData[u] += (avgPayoffPerRepetition - avgPayoffForThisStrategyInThisProfile) * (normalizedValue - avgNormalizedUnitValue[u]);
                }

                // Add to the Sigma data
                for (int q = 0; q < UNITS; q++) {
                    for (int r = 0; r < UNITS; r++) {
                        SigmaData[q][r] += (values.get(q) - avgNormalizedUnitValue[q]) * (values.get(r) - avgNormalizedUnitValue[r]);
                    }
                }
            }
        }

        // Average the collected data
        for (int i = 0; i < UNITS; i++) {
            for (int j = 0; j < UNITS; j++) {
                SigmaData[i][j] /= (K - 1);
            }
        }
        for (int i = 0; i < UNITS; i++) {
            sigmaData[i] /= (K - 1);
        }

        // Covariance matrix
        RealMatrix Sigma = new Array2DRowRealMatrix(SigmaData);
        RealMatrix SigmaInverse = new LUDecomposition(Sigma).getSolver().getInverse();
        // Variance array
        RealMatrix sigma = new Array2DRowRealMatrix(sigmaData);
        RealMatrix sigmaTranspose = sigma.transpose();
        // Calculate numerator of R
        RealMatrix numerator = sigmaTranspose.multiply(SigmaInverse.multiply(sigma));
        double numeratorVal = numerator.getEntry(0, 0);

//        // Calculate variance reduction ratio for every strategy in every profile
//        for (String profile : profileStrategyPayoffs.keySet()) {
//            for (String strategy : profileStrategyPayoffs.get(profile).keySet()) {
//                double[] values = new double[profileStrategyPayoffs.get(profile).get(strategy).size()];
//                for (int i = 0; i < values.length; i++) {
//                    values[i] = profileStrategyPayoffs.get(profile).get(strategy).get(i);
//                }
//                double var = Utils.VARIANCE.evaluate(values);
//                double varRedRatio = ((K - 2) / (K - UNITS - 2)) * (1 - (numeratorVal / var));
//                System.out.println(varRedRatio);
//            }
//        }


        double[] values = new double[allPayoffs.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = allPayoffs.get(i);
        }
        double var = Utils.VARIANCE.evaluate(values);
        double varRedRatio = ((K - 2) / (K - UNITS - 2)) * (1 - (numeratorVal / var));
        System.out.println(varRedRatio);
    }
}
