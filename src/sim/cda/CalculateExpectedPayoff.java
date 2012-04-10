package sim.cda;

import java.io.File;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import org.apache.commons.io.FileUtils;
import org.apache.commons.math3.stat.regression.OLSMultipleLinearRegression;

/**
 *
 * @author Augie
 */
public class CalculateExpectedPayoff {

    public static final int V_MIN = 61;
    public static final int V_MAX = 260;

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new Exception("Expecting 2 args: [samples directory] [learned model output file]");
        }

        File samplesDir = new File(args[0]);
        if (!samplesDir.exists()) {
            throw new Exception("Samples directory does not exist.");
        }

        File outputFile = new File(args[1]);
        if (outputFile.exists()) {
            throw new Exception("Output file already exists.");
        }

        // Run a multiple linear regression on the samples
        // Predicting payoff per agent based on the values assigned to them
        OLSMultipleLinearRegression regression = new OLSMultipleLinearRegression();
        regression.setNoIntercept(false);
        // Load and read in to memory the value set and corresponding payoffs for every agent
        String[] sampleFileLocs = samplesDir.list();
        // 16 agents per sample and however many samples
        int dataPoints = 16 * sampleFileLocs.length;
        double[] u = new double[dataPoints];
        // However many data points and 10 values
        double[][] v = new double[dataPoints][10];

        // Read all of the allocations to build up the data set
        int agentCount = 0;
        for (String sampleFileLoc : sampleFileLocs) {
            if (!sampleFileLoc.toLowerCase().endsWith(".xml")) {
                continue;
            }
            File sampleFile = new File(samplesDir, sampleFileLoc);

            // Parse allocations file
            Builder parser = new Builder();
            Document allocDoc = parser.build(sampleFile);
            Element resultsEl = allocDoc.getRootElement();
            Element scoresEl = resultsEl.getFirstChildElement("scores");
            Elements agentEls = scoresEl.getChildElements("agent");
            for (int i = 0; i < agentEls.size(); i++) {
                Element agentEl = agentEls.get(i);

                // Grab the payoff for this agent
                Element scoreEl = agentEl.getFirstChildElement("score");
                u[agentCount + i] = Double.valueOf(scoreEl.getValue());

                // Grab the values for this agent
                Element valuesEl = agentEl.getFirstChildElement("values");
                Elements valueEls = valuesEl.getChildElements("value");
                double[] vi = new double[10];
                for (int j = 0; j < valueEls.size(); j++) {
                    Element valueEl = valueEls.get(j);
                    vi[j] = Double.valueOf(valueEl.getValue());
                }

                // Is the agent a buyer or a seller?
                // Buyer values are ordered highest to lowest
                boolean isBuyer = vi[0] > vi[9];

                // Normalize the value depending upon role
                if (isBuyer) {
                    for (int j = 0; j < 10; j++) {
                        vi[j] = vi[j] - V_MIN;
                    }
                } else {
                    for (int j = 0; j < 10; j++) {
                        vi[j] = V_MAX - vi[j];
                    }
                }

                v[agentCount + i] = vi;
            }

            // Increment agent count
            agentCount += 16;
        }

        // Set the data for the regression
        regression.newSampleData(u, v);

        // Regression params and diagnostics
        double[] beta = regression.estimateRegressionParameters();
        double[] residuals = regression.estimateResiduals();
        double[][] parametersVariance = regression.estimateRegressionParametersVariance();
        double regressandVariance = regression.estimateRegressandVariance();
        double rSquared = regression.calculateRSquared();
        double adjrSquared = regression.calculateAdjustedRSquared();
        double sigma = regression.estimateRegressionStandardError();

        // Write out the regression parameters
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < beta.length; i++) {
            sb.append(String.valueOf(beta[i]));
            sb.append("\n");
        }
        FileUtils.writeStringToFile(outputFile, sb.toString());
    }
}
