package sim.cda;

import org.apache.commons.math3.stat.correlation.Covariance;
import org.apache.commons.math3.stat.descriptive.moment.Variance;

/**
 *
 * @author Augie
 */
public class Utils {
    
    public static final Covariance COVARIANCE = new Covariance();
    public static final Variance VARIANCE = new Variance();
    
    public static String join(String[] s, String by) {
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length; i++) {
            buf.append(s[i]);
            buf.append(by);
        }
        for (int i = 0; i < by.length(); i++) {
            buf.deleteCharAt(buf.length() - i - 1);
        }
        return buf.toString();
    }
    
}
