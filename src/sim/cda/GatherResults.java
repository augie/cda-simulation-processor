package sim.cda;

import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author Augie
 */
public class GatherResults {

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new Exception("Expecting 3 arguments: [top simulation directory] [simulation #] [output directory]");
        }
        
        // Users from which data has already been gathered
        Set<String> alreadyGathereredFromUsers = new HashSet<String>();
        alreadyGathereredFromUsers.addAll(Arrays.asList(new String[]{"vempatik", "wellman", "augie", "btwied", "kamanlok", "dyoon", "qduong", "bcassell"}));

        // Get directory of simulations
        File simDir = new File(args[0]);
        if (!simDir.exists()) {
            throw new Exception("Simulation directory does not exist.");
        }

        // Get ID of the earliest sample that should be collected
        int earliestSimulationID = Integer.valueOf(args[1]);

        // Get the output directory
        File outDir = new File(args[2]);
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new Exception("Could not create output directory.");
        }
        // Make a quick look-up set of simulation IDs
        Set<Integer> alreadyGathered = new HashSet<Integer>();
        for (String sampleName : outDir.list()) {
            if (!sampleName.endsWith(".xml")) {
                continue;
            }
            alreadyGathered.add(Integer.valueOf(sampleName.substring(0, sampleName.indexOf("_"))));
        }

        // Top level is directories named for users
        for (String userDirLoc : simDir.list()) {
            if (alreadyGathereredFromUsers.contains(userDirLoc)) {
                continue;
            }
            System.out.println("Scanning " + userDirLoc + " directory");
            File userDirFile = new File(simDir, userDirLoc);
            // Second level is directories named for simulations ID's
            for (String simDirLoc : userDirFile.list()) {
                try {
                    int simID = Integer.valueOf(simDirLoc).intValue();
                    if (simID < earliestSimulationID
                            || alreadyGathered.contains(simID)) {
                        continue;
                    }
                    File simDirFile = new File(userDirFile, simDirLoc);
                    // Third level is the simulation directory
                    File pastGamesDir = new File(simDirFile, "past_games");
                    // Make sure past_games dir exists
                    if (!pastGamesDir.exists()) {
                        continue;
                    }
                    // past_games contains directories numbered from 1-#samples
                    for (String sampleDirLoc : pastGamesDir.list()) {
                        // Each sample directory contains alloc.xml
                        File sampleDirFile = new File(pastGamesDir, sampleDirLoc);
                        // Check for alloc.xml
                        File allocXMLFile = new File(sampleDirFile, "alloc.xml");
                        if (!allocXMLFile.exists()) {
                            continue;
                        }
                        // Copy the file to the output directory with the naming
                        //  convention: sim#_sam#.xml
                        FileUtils.copyFile(allocXMLFile, new File(outDir, simDirLoc + "_" + sampleDirLoc + ".xml"));
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
