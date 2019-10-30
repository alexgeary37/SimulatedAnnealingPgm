import javax.management.Attribute;
import java.io.*;
import java.lang.reflect.Array;
import java.util.Random;
import java.util.ArrayList;
import java.lang.Math;
import java.lang.System;

public class weightOptimizer {
    private static ArrayList<Fbx> files; // lists the data for each file in csv
    private static int fileCount, attributeCount; // number of files and number of attributes per file
    private static double decrement;
    private static boolean allowNegatives;

    public static void main(String[] args) {
        fileCount = 0;

        if (args.length == 3) {

            // create fbx files from csv file
            if (generateFiles() == false) {
                System.out.println("Error: csv file must be of the format:\n\nFile name,attr1 name,attr2 name,...,Actual memory\n'file1 name',attr1 value,attr2 value,...,Actual memory file1 used\n'file2 name'...");
                return; // error in csv file
            }

            decrement = Double.parseDouble(args[1]);
            if (decrement >= 1) throw new IllegalArgumentException("\ndecrement amount should be in range 0.5 < 1");

            int allowNegativeWeights = Integer.parseInt(args[2]);
            
            if (allowNegativeWeights == 0) {
                allowNegatives = false;
            } else if (allowNegativeWeights == 1) {
                allowNegatives = true;
            } else {
                throw new IllegalArgumentException(
                        "\n<negative weights> specify 1 for allowing negative weights, 0 otherwise");
            }

            // create array which holds rounds of SimAnnealing results
            ArrayList<Round> rounds = new ArrayList<>();

            // perform SimAnnealing the specified number of times and add to rounds
            for (int i = 0; i < Integer.parseInt(args[0]); i++) {
                rounds.add(performSimAnnealing());
                System.out.println("Round: " + (i+1) + " Inaccuracy: " + rounds.get(i).getInaccuracy());
            }

            // loop through all rounds to find round which has the
            // best accuracy / lowest inaccuracy
            Round bestRound = rounds.get(0);
            double bestInaccuracy = bestRound.getInaccuracy();
            for (int i = 1; i < rounds.size(); i++) {
                Round round = rounds.get(i);
                double inaccuracy = round.getInaccuracy();

                if (inaccuracy < bestInaccuracy) {
                    bestInaccuracy = inaccuracy;
                    bestRound = round;
                }
            }

            // print weights of attributes from best round
            bestRound.printRound();

        } else {
            System.out.println(
                    "Usage: java weightOptimizer <number of solutions> <decrement amount> <negative weights>\n<decrement amount> should be set closer to 1 for higher accuracy. Recommended setting is 0.999 - 0.999999\n<negative weights> should be set to 1 to allow negative weights, 0 otherwise");
        }
    }

    // generate fbx files with values from csv stats file
    private static boolean generateFiles() {

        // IF stats file does not exist, print out error message and end program
        File stats = new File("stats.csv");
        if (!stats.exists()) {
            System.err.println("Error: stats.csv does not exist");
            return false;
        }

        try {

            // read stats.csv file to create and initialize files
            InputStream inputStream = new FileInputStream(stats);
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            files = new ArrayList<>();

            String line = bufferedReader.readLine(); // line for reading csv file

            if (line != null) {
                String[] attributeNames = line.split(",");
                attributeCount = attributeNames.length - 2; // number of numeric attributes in file

                // read each line of csv file and initialize fbx files
                while ((line = bufferedReader.readLine()) != null) {

                    if (line.compareTo("") == 0) break;

                    String[] parts = line.split(",");

                    // create and initialize fbx file with line values
                    Fbx file = new Fbx(attributeNames[0], parts[0]);
                    for (int i = 1; i < attributeNames.length; i++) {
                        file.addAttribute(attributeNames[i], Integer.parseInt(parts[i]));
                    }

                    files.add(file);
                    fileCount++;
                }
            }

        } catch (Exception e) {
            System.out.println("Error: csv file must be of the format:\n\nFile name,attr1 name,attr2 name,...,Actual memory\n'file1 name',attr1 value,attr2 value,...,Actual memory file1 used\n'file2 name'...");
            e.printStackTrace();
            return false;
        }

        return true;
    }

    // perform SimAnnealing on attributes
    private static Round performSimAnnealing() {

        String[] names = files.get(0).getAttributeNames();
        Round round = new Round(names, allowNegatives); // the current round containing all weight and proportion settings    

        round.setAverageActualMemory(files); // calculate the average actual memory used by all the files

        double temperature = 0.8; // similar to that used in sim-annealing process

        while (temperature > 1e-8) {

            // copy of weights to experiment making changes with
            ArrayList<Double> modifiedWeights = round.getAttributeWeights();

            double roundInaccuracy = calcInaccuracy(round.getConstant(), round.getAttributeWeights());

            double upperBound, lowerBound;
            
            // while there are changes left, make a change and see if
            // it makes for lower inaccuracy
            for (int i = 0; i < attributeCount; i++) {
                Random rand = new Random();

                // range from which the modified weight below will be generated within
                upperBound = round.getAttributeWeight(i) + temperature;
                lowerBound = round.getAttributeWeight(i) - temperature;
                double weight = lowerBound + (upperBound - lowerBound) * rand.nextDouble(); // modified weight

                // if we don't allow negative weights, make sure minimum weight is 0
                if (!allowNegatives && weight < 0) {
                    weight = 0;
                }

                modifiedWeights.set(i, weight); // apply change of weight to ith attribute
                
                // calculate inaccuracy with the modified weights
                double tempInaccuracy = calcInaccuracy(round.getConstant(), modifiedWeights);
                
                // if inaccuracy is less than previously, update weights and round inaccuracy
                if (tempInaccuracy < roundInaccuracy) {
                    round.setAttributeWeight(i, modifiedWeights.get(i));
                    roundInaccuracy = tempInaccuracy;
                }
                
                lowerBound = round.getConstant() - temperature;
                upperBound = round.getConstant() + temperature;
            
                // modify the constant
                double constNum = lowerBound + (upperBound - lowerBound) * rand.nextDouble();

                // prevent constant from being lower than 1
                if (constNum < 1) {
                    constNum = 1;
                }
            
                // calculate innacuracy with modified constant
                tempInaccuracy = calcInaccuracy(constNum, round.getAttributeWeights());

                // check if modified constant was better for inaccuracy and make appropriate updates
                if (tempInaccuracy < roundInaccuracy) {
                    round.setConstant(constNum);
                    roundInaccuracy = tempInaccuracy;
                }
                
                temperature *= decrement; // decrease the temperature of this round
            }
        }

        // get final round inaccuracy
        double inaccuracy = calcInaccuracy(round.getConstant(), round.getAttributeWeights()); //The best one in this round

        // return weights, inaccuracy and constant in an arraylist
        round.setInaccuracy(inaccuracy);

        return round;
    }

    // returns the inaccuracy given by the current weight settings
    private static double calcInaccuracy(double constant, ArrayList<Double> weights) {
        double[] fileMemPredictions = new double[fileCount];

        // calculate all individual file memory usage predictions
        for (int i = 0; i < fileCount; i++) {
            int[] values = files.get(i).getAttributeValues();

            fileMemPredictions[i] = constant;
            for (int j = 0; j < values.length; j++) {
                fileMemPredictions[i] += (values[j] * (double) weights.get(j));
            }
        }

        // calculate and return the inaccuracy of the current weight settings
        double inaccuracy = 0;
        for (int i = 0; i < fileCount; i++) {
            inaccuracy += Math.abs(files.get(i).getActualMemory() - fileMemPredictions[i]);
        }

        return inaccuracy;
    }
}


class Round {

    private double inaccuracy; // inaccuracy of the round - lower the value the better
    private double aveActualMemory; // the average actual memory used by the fbx files loaded in from csv;
    private double constant; // constant value of memory
    private ArrayList<RoundAttribute> attributes; // all the attributes in this round


    // private inner class to store information about attributes
    private class RoundAttribute {
        private String name;
        private double weight;

        private RoundAttribute(String attributeName, double attributeWeight) {
            name = attributeName;
            weight = attributeWeight;
        }
    }


    public Round(String[] attributeNames, boolean allowNegatives) {
        aveActualMemory = 0;
        attributes = new ArrayList<>();

        for (int i = 0; i < attributeNames.length; i++) {
            double weight = (allowNegatives == true) ? 0.1 : 1;
            RoundAttribute roundAttribute = new RoundAttribute(attributeNames[i], weight);
            attributes.add(roundAttribute);
        }

        constant = 200.0;
    }


    // prints the constant, attributes and their weights and the inaccuracy of the round
    public void printRound() {
        System.out.println("\n-------------------------------------\nInaccuracy: " +inaccuracy);
        System.out.println("weightings.json format:\n");
        System.out.println("\"constant\":" + constant + ",");

        // print weight for each attribute
        for (int i = 0; i < attributes.size() - 1; i++) {
            System.out.println("\""+ attributes.get(i).name + "\":" + attributes.get(i).weight+",");
        }

        System.out.println("\""+attributes.get(attributes.size() - 1).name + "\":"+attributes.get(attributes.size() -1 ).weight);
        System.out.println("-------------------------------------");
    }
    
    
    // getter and setter for the constant

    public void setConstant(double constValue) {
        constant = constValue;
    }

    public double getConstant() {
        return constant;
    }

    // calculate average actual memory used by all files
    public void setAverageActualMemory(ArrayList<Fbx> files) {
        for (Fbx file : files) {
            aveActualMemory += file.getActualMemory();
        }

        aveActualMemory /= files.size();
    }

    // returns the average actual memory used by all the files
    public double getAveActualMemory() {
        return aveActualMemory;
    }

    // returns all the attribute weights
    public ArrayList<Double> getAttributeWeights() {
        ArrayList<Double> weights = new ArrayList<>();

        for (RoundAttribute attr : attributes) {
            weights.add(attr.weight);
        }

        return weights;
    }

    // adds and attribute
    public void addAttribute(String name, double weight) {
        attributes.add(new RoundAttribute(name, weight));
    }


    // getters and setters for attribute weights and round inaccuracy

    public void setAttributeWeight(int index, double weight) {
        attributes.get(index).weight = weight;
    }

    public double getAttributeWeight(int index) {
        return attributes.get(index).weight;
    }

    public void setInaccuracy(double roundInaccuracy) {
        inaccuracy = roundInaccuracy;
    }

    public double getInaccuracy() {
        return inaccuracy;
    }

}


// class holds information about fbx files loaded in from a csv file
class Fbx {
    private ArrayList<FBXAttribute> attributes; // list of attributes the file contains
    private double actualMemory; // actual memory the file uses


    public Fbx(String name, Object value) {
        attributes = new ArrayList<>();
        attributes.add(new FBXAttribute(name, value));
        actualMemory = 0;
    }


    // inner class to bind attribute names with their values
    private class FBXAttribute {
        private String name;
        private Object value;

        private FBXAttribute(String n, Object v) {
            name = n;
            value = v;
            if (name.compareTo("Actual memory") == 0) {
                actualMemory = (int) v;
            }
        }
    }


    // adds a new attribute to the fbx file
    public void addAttribute(String name, Object value) {
        attributes.add(new FBXAttribute(name, value));
    }

    // returns the counts of each attribute in this file
    public int[] getAttributeValues() {
        int[] values = new int[attributes.size() - 2];
        int i = 0;

        for (FBXAttribute attr : attributes) {
            if (attr.name.compareTo("File name") != 0 && attr.name.compareTo("Actual memory") != 0) {
                values[i] = (int) attr.value;
                i++;
            }
        }

        return values;
    }

    // returns all the names of the attributes in this file
    public String[] getAttributeNames() {
        String[] names = new String[attributes.size() - 2];
        int i = 0;

        for (FBXAttribute attr : attributes) {
            if (attr.name.compareTo("File name") != 0 && attr.name.compareTo("Actual memory") != 0) {
                names[i] = attr.name;
                i++;
            }
        }

        return names;
    }

    // returns the actual memory this fbx file uses
    public double getActualMemory() {
        return actualMemory;
    }
}