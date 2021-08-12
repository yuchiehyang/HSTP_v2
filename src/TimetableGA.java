public class TimetableGA {
    // parameters for this project
    private static final int GRADE_CNT = 3;
    private static final int CLASS_CNT = 34;
    private static final int TEACHER_CNT = 143;
    private static final int SUBJECT_CNT = 29;
    private static final int DAY_CNT = 5;
    private static final int PERIOD_CNT = 7;
    // parameter for setting GA algorithm
    private static final int POPULATION_SIZE = 200;
    private static final double MUTATION_RATE = 0.5;
    private static final double CROSSOVER_RATE = 0.5;
    private static final int MAX_GENERATIONS = 3000;

    // IMPORTANT NOTE: 程式陣列長度皆為實際需要長度再加 1
    public static void main(String[] args) {

        // 記錄程式開始執行的時間
        long startTime = System.nanoTime();

        // 記錄班級屬於哪一個年級
        int grade1_classBound = 11;
        int grade2_classBound = 23;
        int[] gradeOfClass = new int[CLASS_CNT + 1];
        for (int i = 0; i < gradeOfClass.length; i++) {
            if (i <= grade1_classBound) {
                gradeOfClass[i] = 7;
            }
            else if (i <= grade2_classBound) {
                gradeOfClass[i] = 8;
            }
            else {
                gradeOfClass[i] = 9;
            }
        }
        // 記錄每個年級各個科目每周需要上的節數
        // index 0 不使用，以 -1 表示
        int[] grade1_subjectFixedCnt = {-1,5,3,4,0,3,0,1,0,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,1,1,0,0,1,0};
        int[] grade2_subjectFixedCnt = {-1,5,3,4,0,0,3,1,0,1,1,1,1,2,1,1,1,1,1,1,1,1,1,1,1,0,1,0,1,0};
        int[] grade3_subjectFixedCnt = {-1,5,4,4,3,0,0,0,1,1,2,1,1,2,1,1,1,1,1,1,1,1,1,0,0,0,0,1,0,1};
        int[][] subjectFixedCnt = new int[GRADE_CNT + 1][SUBJECT_CNT + 1];
        subjectFixedCnt[1] = grade1_subjectFixedCnt;
        subjectFixedCnt[2] = grade2_subjectFixedCnt;
        subjectFixedCnt[3] = grade3_subjectFixedCnt;
        // 記錄每節課各科目教室最多能容納幾個班級上課
        // subjectRoomLimit[SUBJECT_CNT + 1];
        // index 0 不使用，以 -1 表示
        int[] subjectRoomLimit = {-1,34,34,34,34,34,34,2,34,34,34,34,34,3,34,2,34,34,34,34,34,34,34,34,2,34,34,34,34,34};
        // 記錄老師偏好的授課的時間
        int[][][][] teacherPreferredTimetable = new int[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        // 記錄老師該節次是否有空堂可授課
        int[][][][] teacherAvailableTimetable = new int[POPULATION_SIZE + 1][TEACHER_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];

        // 記錄每個班級各個科目一周上了幾次
        int[][][] classSubjectCnt = new int[POPULATION_SIZE + 1][CLASS_CNT + 1][SUBJECT_CNT + 1];
        // 記錄每節課各科目教室有幾個班級在使用
        int[][][][] subjectRoomCnt = new int[POPULATION_SIZE + 1][SUBJECT_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        // 記錄每個班級各節次上了什麼科目
        int[][][][] classSubjectTable = new int[POPULATION_SIZE + 1][SUBJECT_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        // 記錄每個班級各節次的上課老師
        int[][][][] classTeacherTable = new int[POPULATION_SIZE + 1][SUBJECT_CNT + 1][DAY_CNT + 1][PERIOD_CNT + 1];
        // 記錄每個班級是否已經有指定的科目老師
        // 有則為老師 id，無則為 -1
        int[][] classAssignedTeacher = new int[CLASS_CNT + 1][SUBJECT_CNT + 1];

        // TODO: 記錄每個科目的可授課老師




        // 記錄程式執行完畢的時間
        long endTime = System.nanoTime();
        // 計算執行程式的 elapsed time (in nanoseconds)
        long timeElapsed = endTime - startTime;
        System.out.println("Execution time: " + timeElapsed / 1000000000 / 60 + " minutes " + timeElapsed / 1000000000 % 60 + " seconds");

    }
}
