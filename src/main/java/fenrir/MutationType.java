package fenrir;

import java.security.SecureRandom;
import java.util.*;

public enum MutationType {
    MoveSchedule,
    ShortenSchedule,
    ExtendSchedule,
    FlipUserGroup,
    FlipUserGroupRange,
    AddUserGroup,
    AddUserGroupRange,
    RemoveUserGroup,
    RemoveUserGroupRange,
    AdjustTraffic,
    AdjustTrafficRange;

    private static final List<MutationType> VALUES = Collections.unmodifiableList(Arrays.asList(values()));
    private static final int SIZE = VALUES.size();
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final List<Integer> WEIGHTS = Arrays.asList(40, 10, 5, 10, 5, 10, 5, 10, 5, 0, 0);

    public static MutationType randomMutationType()  {
        int weight_sum = WEIGHTS.stream().mapToInt(Integer::intValue).sum();

        double value = RANDOM.nextDouble() * weight_sum;

        for(int i=0; i < SIZE; i++) {
            value -= WEIGHTS.get(i);
            if(value <= 0)
                return VALUES.get(i);
        }
        return VALUES.get(SIZE - 1);
    }
}
