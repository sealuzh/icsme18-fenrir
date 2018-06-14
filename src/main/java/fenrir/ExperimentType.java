package fenrir;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public enum ExperimentType {
    REGRESSION, BUSINESS;

    private static final List<ExperimentType> VALUES = Collections.unmodifiableList(Arrays.asList(values()));
    private static final int SIZE = VALUES.size();
    private static final Random RANDOM = new Random();

    public static ExperimentType randomExperimentType()  {
        return VALUES.get(RANDOM.nextInt(SIZE));
    }
}
