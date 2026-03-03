package JobShop;


import org.apache.commons.cli.*;
import org.ddolib.common.dominance.DominanceChecker;
import org.ddolib.common.solver.Solution;


import org.ddolib.modeling.AcsModel;
import org.ddolib.modeling.Model;
import org.ddolib.modeling.Problem;
import org.ddolib.modeling.Solvers;
import org.ddolib.util.io.SolutionPrinter;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static java.lang.Math.max;

public class JSMain {

    public enum SearchType {
        Astar,
        ACS,
        LNS,
        SOA,
        AWA
    }

    private static final Map<String, SearchType> searchMap = new HashMap<>() {
        {
            put("Astar", SearchType.Astar);
            put("ACS", SearchType.ACS);
            put("LNS", SearchType.LNS);
            put("SOA", SearchType.SOA);
            put("AWA", SearchType.AWA);
        }
    };


    public static Integer getObjectiveValue(String csvPath, String fullPath) {
        // --- extract only the filename (orb07.txt, ft10.txt, la01.txt, ...)
        // --- extract filename (orb07.txt)
        String fileName = fullPath.substring(fullPath.lastIndexOf('/') + 1);

        // --- remove extension (.txt → orb07)
        String instanceName = fileName.replace(".txt", "");

        try (BufferedReader br = new BufferedReader(new FileReader(csvPath))) {
            String line = br.readLine(); // skip header

            while ((line = br.readLine()) != null) {
                String[] parts = line.split(";");

                String modelName = parts[0].trim();

                // match if CSV model name contains the instance (orb07.txt etc.)
                if (modelName.contains(instanceName)) {
                    return Integer.parseInt(parts[3].trim());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return null; // not found
    }

    public static void main(String args[]) {

        String quotedValidSearch = searchMap.keySet().stream().sorted().map(x -> "\"" + x + "\"")
                .collect(Collectors.joining(",\n"));

        Option modelOpt = Option.builder("s").longOpt("solver").argName("SOLVER").required().hasArg()
                .desc("used search.\nValid searches value are : " + quotedValidSearch).build();

        Option inst = Option.builder("i").longOpt("instance").argName("INSTANCE").required().hasArg()
                .desc("path file").build();

        Option time = Option.builder("t").longOpt("time").argName("TIME").required().hasArg()
                .desc("time limit (sec)").build();
        Option open = Option.builder("o").longOpt("open").argName("OPENSHOP").required().hasArg()
                .desc("open shop").build();

        Option cpIs = Option.builder("cp").longOpt("cp").argName("cp").required().hasArg()
                .desc("cp").build();
        Options options = new Options();

        options.addOption(modelOpt);
        options.addOption(inst);
        options.addOption(time);
        options.addOption(open);
        options.addOption(cpIs);
        CommandLineParser parser = new DefaultParser();
        CommandLine cmd = null;
        String fileName = null;
        String searchName = null;
        Boolean openShop = null;
        Boolean cpval = null;
        int timeLimit = 0;
        try {
            cmd = parser.parse(options, args);
            fileName = cmd.getOptionValue("i");
            searchName = cmd.getOptionValue("s");
            timeLimit = Integer.parseInt(cmd.getOptionValue("t"));
            openShop = Boolean.valueOf(cmd.getOptionValue("o"));
            cpval = Boolean.valueOf(cmd.getOptionValue("cp"));
            if (!searchMap.containsKey(searchName))
                throw new IllegalArgumentException("Unknown solver: " + searchName);

        } catch (ParseException exp) {

            System.err.println(exp.getMessage());
            new HelpFormatter().printHelp("JobShop Problem", options);
            System.exit(1);
        }
        try {
            if (cpval) {
                JSInstance instance = new JSInstance(fileName, openShop);
                JSProblem problem = new JSProblem(instance);
                if (!openShop) {
                    for (int i = 0; i < instance.getnJobs(); i++) {
                        for (int j = 0; j < instance.getnMachines(); j++) {
                            for( int k = j+1; k < instance.getnMachines(); k++) {
                                problem.addPrecedence(i * instance.getnMachines() + j, i * instance.getnMachines() + k);
                            }
                        }
                    }
                }

                if (Objects.equals(searchName, "Astar")) {
                    final Model<JSState> model = new AcsModel<>() {

                        @Override
                        public Problem<JSState> problem() {
                            return problem;
                        }

                        @Override
                        public JSFastLowerBound lowerBound() {
                            return new JSFastLowerBound(problem);
                        }

                        @Override
                        public DominanceChecker<JSState> dominance() {
                            return new AstarDominanceChecker<>(new JSDominance(problem), problem.nbVars());
                        }
                    };
                    int finalTimeLimit = timeLimit;
                    Solution bestSolution = Solvers.minimizeAstar(model, s -> s.runTimeMs() > finalTimeLimit, (sol, s) -> {
                    });
                    System.out.println(fileName + ";" + "Astar;" + instance.getnJobs() + ";" + instance.getnMachines() + ";" + finalTimeLimit + ";" + bestSolution.statistics());

                } else if (Objects.equals(searchName, "ACS")) {
                    final AcsModel<JSState> model = new AcsModel<>() {

                        @Override
                        public Problem<JSState> problem() {
                            return problem;
                        }

                        @Override
                        public JSFastLowerBound lowerBound() {
                            return new JSFastLowerBound(problem);
                        }

                        @Override
                        public DominanceChecker<JSState> dominance() {
                            return new AstarDominanceChecker<>(new JSDominance(problem), problem.nbVars());
                        }
                    };
                    int finalTimeLimit = timeLimit;
                    Solution bestSolution = Solvers.minimizeAcs(model, s -> s.runTimeMs() > finalTimeLimit, (sol, s) -> {
                    });
                    System.out.println(fileName + ";" + "ACS;" + instance.getnJobs() + ";" + instance.getnMachines() + ";" + finalTimeLimit + ";" + bestSolution.statistics());

                } else if (Objects.equals(searchName, "SOA")) {
                    final SOTA jsdpsoa = new SOTA(instance, timeLimit, Integer.MAX_VALUE);
                    System.out.print(fileName + ";" + searchName + ";"+ instance.getnJobs() + ";" + instance.getnMachines() + ";" + timeLimit + ";" );
                    System.out.println(jsdpsoa.solve());
                }else if (Objects.equals(searchName, "LNS")){
                    int obj = getObjectiveValue("Optal-4W-2FDS.csv",fileName);
                    Random random = new Random(42);
                    for(int i=0;i<10;i++) {
                        int seed = random.nextInt();
                        System.out.println(fileName + ";" + searchName + ";" + instance.getnJobs() + ";" + instance.getnMachines() + ";" + timeLimit + ";" + LNSSolver.solve(instance, timeLimit, seed, obj)+ ";" +seed);
                    }
                }
            }else{
                JSInstance instance = new JSInstance(fileName, openShop);
                JSProblem2 problem = new JSProblem2(instance);
                if (!openShop) {
                    for (int i = 0; i < instance.getnJobs(); i++) {
                        for (int j = 0; j < instance.getnMachines() - 1; j++) {
                            problem.addPrecedence(i * instance.getnMachines() + j, i * instance.getnMachines() + j + 1);
                        }
                    }
                }

                if (Objects.equals(searchName, "Astar")) {
                    final Model<JSState> model = new AcsModel<>() {

                        @Override
                        public Problem<JSState> problem() {
                            return problem;
                        }

                        @Override
                        public JSFastLowerBound2 lowerBound() {
                            return new JSFastLowerBound2(problem);
                        }

                        @Override
                        public DominanceChecker<JSState> dominance() {
                            return new AstarDominanceChecker<>(new JSDominance2(problem), problem.nbVars());
                        }
                    };
                    int finalTimeLimit = timeLimit;
                    Solution bestSolution = Solvers.minimizeAstar(model, s -> s.runTimeMs() > finalTimeLimit, (sol, s) -> {
                    });
                    System.out.println(fileName + ";" + "Astar;" + instance.getnJobs() + ";" + instance.getnMachines() + ";" + finalTimeLimit + ";" + bestSolution.statistics());

                } else if (Objects.equals(searchName, "ACS")) {
                    final AcsModel<JSState> model = new AcsModel<>() {

                        @Override
                        public Problem<JSState> problem() {
                            return problem;
                        }

                        @Override
                        public JSFastLowerBound2 lowerBound() {
                            return new JSFastLowerBound2(problem);
                        }

                        @Override
                        public DominanceChecker<JSState> dominance() {
                            return new AstarDominanceChecker<>(new JSDominance2(problem), problem.nbVars());
                        }
                    };
                    int finalTimeLimit = timeLimit;
                    Solution bestSolution = Solvers.minimizeAcs(model, s -> s.runTimeMs() > finalTimeLimit, (sol, s) -> {
                    });
                    System.out.println(fileName + ";" + "ACS;" + instance.getnJobs() + ";" + instance.getnMachines() + ";" + finalTimeLimit + ";" + bestSolution.statistics());

                } else if (Objects.equals(searchName, "SOA")) {
                    final SOTA jsdpsoa = new SOTA(instance, timeLimit, Integer.MAX_VALUE);
                    System.out.println(fileName + ";" + searchName + ";"+ instance.getnJobs() + ";" + instance.getnMachines() + ";" + timeLimit + ";" +jsdpsoa.solve());
                }
            }

        } catch (Exception e) {
            System.err.println(e.getMessage());
        }

    }

    public static double main(String arg, String solver, boolean openshop) {
        JSInstance instance = new JSInstance(arg, openshop);
        JSProblem problem = new JSProblem(instance);
        if (!openshop) {
            for (int i = 0; i < instance.getnJobs(); i++) {
                for (int j = 0; j < instance.getnMachines() - 1; j++) {
                    problem.addPrecedence(i * instance.getnMachines() + j, i * instance.getnMachines() + j + 1);
                }
            }
        }

        if (solver.equals("Astar")) {
            final Model<JSState> model = new AcsModel<>() {

                @Override
                public Problem<JSState> problem() {
                    return problem;
                }
                @Override
                public  JSFastLowerBound lowerBound() {
                    return  new JSFastLowerBound(problem);
                }
                @Override
                public DominanceChecker<JSState> dominance(){
                    return new AstarDominanceChecker<>(new JSDominance(problem), problem.nbVars() );
                }
            };
            Solution bestSolution = Solvers.minimizeAstar(model, (sol, s) -> {

            });
            return bestSolution.value();


        } else if (solver.equals("ACS")) {
            final AcsModel<JSState> model = new AcsModel<>() {

                @Override
                public Problem<JSState> problem() {
                    return problem;
                }
                @Override
                public  JSFastLowerBound lowerBound() {
                    return  new JSFastLowerBound(problem);
                }
                @Override
                public DominanceChecker<JSState> dominance(){
                    return new AstarDominanceChecker<>(new JSDominance(problem), problem.nbVars() );
                }
            };
            Solution bestSolution = Solvers.minimizeAcs(model, (sol, s) -> {
            });
            return bestSolution.value();

        }else if (solver.equals( "SOA")) {
            final SOTA jsdpsoa = new SOTA(instance, 300, Integer.MAX_VALUE);
            return jsdpsoa.solve().incumbent();
        }
        return -1.0;
    }
}
