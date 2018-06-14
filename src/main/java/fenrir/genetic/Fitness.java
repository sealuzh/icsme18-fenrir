package fenrir.genetic;

import fenrir.Constants;

import java.text.DecimalFormat;

public class Fitness {
    private float durationScore;

    private float userGroupScore;

    private float startScore;

    private static final DecimalFormat decimalFormat = new DecimalFormat("###.###");

    public Fitness(float durationScore, float userGroupScore, float startScore) {
        this.durationScore = durationScore;
        this.userGroupScore = userGroupScore;
        this.startScore = startScore;
    }

    public float getValue() {
        return durationScore * Constants.FITNESS_DURATION_WEIGHT +
                userGroupScore * Constants.FITNESS_USERGROUP_WEIGHT +
                startScore * Constants.FITNESS_STARTSLOT_WEIGHT;
    }

    @Override
    public String toString() {
        return "Fitness{" +
                "durationScore=" + durationScore +
                ", userGroupScore=" + userGroupScore +
                ", startScore=" + startScore +
                ", total= " + getValue() +
                '}';
    }

    public String toCSV(int generation) {
        return String.format("%d,%s,%s,%s,%s", generation, decimalFormat.format(durationScore),
                decimalFormat.format(userGroupScore), decimalFormat.format(startScore),
                decimalFormat.format(getValue()));
    }

    public String getIndividualScoresCommaSeparated() {
        return durationScore + "," + userGroupScore + "," + startScore;
    }

}
