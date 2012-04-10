package sim.cda;

import java.io.File;
import java.util.Arrays;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author Augie
 */
public class GatherResults {

    public static void main(String[] args) throws Exception {
        if (args.length != 3) {
            throw new Exception("Expecting 3 arguments: [top simulation directory] [timestamp] [output directory]");
        }

        // Get directory of simulations
        File simDir = new File(args[0]);
        if (!simDir.exists()) {
            throw new Exception("Simulation directory does not exist.");
        }

        // Get timestamp of the earliest sample that should be collected
        long beginTimestamp = Long.valueOf(args[1]);

        // Get the output directory
        File outDir = new File(args[2]);
        if (outDir.exists() && outDir.list().length > 0) {
            throw new Exception("Output directory is not empty.");
        }
        if (!outDir.exists() && !outDir.mkdirs()) {
            throw new Exception("Could not create output directory.");
        }

        // Top level is directories named for users
        for (String userDirLoc : simDir.list()) {
            System.out.println("Scanning " + userDirLoc + " directory");
            File userDirFile = new File(simDir, userDirLoc);
            // Second level is directories named for simulations ID's
            for (String simDirLoc : userDirFile.list()) {
                File simDirFile = new File(userDirFile, simDirLoc);
                // Third level is the simulation directory
                // Make sure past_games dir exists
                if (Arrays.binarySearch(simDirFile.list(), "past_games") < 0) {
                    continue;
                }
                File pastGamesDir = new File(simDirFile, "past_games");
                // past_games contains directories numbered from 1-#samples
                for (String sampleDirLoc : pastGamesDir.list()) {
                    // Each sample directory contains alloc.xml
                    File sampleDirFile = new File(pastGamesDir, sampleDirLoc);
                    // Check for alloc.xml
                    File allocXMLFile = new File(sampleDirFile, "alloc.xml");
                    if (!allocXMLFile.exists()) {
                        continue;
                    }
                    // Check the timestamp of the allocation file
                    if (allocXMLFile.lastModified() < beginTimestamp) {
                        continue;
                    }
                    // Copy the file to the output directory with the naming
                    //  convention: sim#_sam#.xml
                    FileUtils.copyFile(allocXMLFile, new File(outDir, simDirLoc + "_" + sampleDirLoc + ".xml"));
                    System.out.println("Found allocation file: " + allocXMLFile.getAbsolutePath());
                }
            }
        }
    }
}
