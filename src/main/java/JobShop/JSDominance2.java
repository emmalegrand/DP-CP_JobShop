package JobShop;

import org.ddolib.modeling.Dominance;

import java.util.HashSet;

public class JSDominance2 implements Dominance<JSState> {

    private final JSProblem2 problem;
    public JSDominance2(JSProblem2 problem) {
        this.problem = problem;
    }

    @Override
    public Object getKey(JSState state) {
        return state.done;
    }

    @Override
    public boolean isDominatedOrEqual(JSState a, JSState b) {
        if (a.done.cardinality()==problem.data.getnMachines()*problem.data.getnJobs()) {
            if (problem.getMakespan(b)<problem.getMakespan(a)) {
                return true;
            }
            return false;
        }
        int makespanA = problem.getMakespan(a);
        int makespanB = problem.getMakespan(b);
        boolean oneStrictInequality = false;
        for (int i = 0; i < problem.data.getnJobs(); i++) {
            for (int j = 0; j < problem.data.getnMachines(); j++) {
                if (!a.done.get(i*problem.data.getnMachines()+j)){
                    boolean available = true;
                    for (int pred: problem.precedences.getOrDefault(i*problem.data.getnMachines()+j, new HashSet<>())){
                        if (!a.done.get(pred)){
                            available = false;
                            break;
                        }
                    }
                    if (available) {
                        int aptA = a.est[i][j] + problem.data.getDuration()[i][j];
                        int aptB = b.est[i][j] + problem.data.getDuration()[i][j];
                        if (!a.eta.get(i * problem.data.getnMachines() + j)) {
                            aptA = makespanA + problem.data.getDuration()[i][j];
                        }
                        if (!b.eta.get(i * problem.data.getnMachines() + j)) {
                            aptB = makespanB + problem.data.getDuration()[i][j];
                        }
                        if (aptB <= aptA) {
                            oneStrictInequality = true;
                        } else {
                            return false;
                        }
                    }
                }
            }
        }
        return oneStrictInequality;
    }
}
