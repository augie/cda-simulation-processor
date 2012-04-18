package sim.cda;

import java.io.File;
import java.util.Arrays;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Elements;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author Augie
 */
public class CalculateAverageNormalizedUnitValue {

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

        // Sum the values in every unit slot
        double[] vSum = new double[UNITS];
        Arrays.fill(vSum, 0);

        // Read all of the allocations to build up the data set
        int count = 0;
        for (String sampleFileLoc : samplesDir.list()) {
            // Open the sample file
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

                // ID
                Element idEl = agentEl.getFirstChildElement("ID");
                int id = Integer.valueOf(idEl.getValue());

                // Grab the values for this agent
                Element valuesEl = agentEl.getFirstChildElement("values");
                Elements valueEls = valuesEl.getChildElements("value");
                double[] vi = new double[UNITS];
                for (int j = 0; j < valueEls.size(); j++) {
                    Element valueEl = valueEls.get(j);
                    vi[j] = Double.valueOf(valueEl.getValue());
                }

                // Is the agent a buyer or a seller?
                // Buyer values are ordered highest to lowest
                boolean isBuyer = vi[0] > vi[9];

                // Add to sum for each unit
                for (int j = 0; j < UNITS; j++) {
                    double normalizedValue;
                    if (isBuyer) {
                        normalizedValue = vi[j] - V_MIN;
                    } else {
                        normalizedValue = V_MAX - vi[j];
                    }
                    vSum[j] += normalizedValue;
                }

                count++;
            }
        }

        // Average
        double[] vAvg = new double[UNITS];
        for (int j = 0; j < UNITS; j++) {
            vAvg[j] = vSum[j] / count;
        }

        // Write out the regression parameters
        StringBuilder sb = new StringBuilder();
        for (int u = 0; u < UNITS; u++) {
            sb.append(vAvg[u]);
            sb.append("\n");
        }
        FileUtils.writeStringToFile(outputFile, sb.toString());
    }
}
